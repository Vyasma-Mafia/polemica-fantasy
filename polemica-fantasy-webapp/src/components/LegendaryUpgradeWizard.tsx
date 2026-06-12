import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { ApiError, apiGet } from '../api/client'
import { fetchPerkCatalog } from '../api/perksCatalog'
import { fetchLegendaryUpgradeInfo, postLegendaryUpgrade } from '../api/legendaryUpgrade'
import type { PerkCatalogItem, LegendaryUpgradeResponse, UserCardItem } from '../api/types'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { rarityScoreModifierLabel } from '../lib/rarity'
import { CardPerkChips } from './CardPerkChips'
import { PlayerImage } from './PlayerImage'

type Step = 'card' | 'perk' | 'confirm' | 'result'

export function isEligibleEpicForLegendary(c: UserCardItem): boolean {
  return (
    c.rarity === 'EPIC' &&
    c.usesRemaining > 0 &&
    c.perks.length === 2
  )
}

export function LegendaryUpgradeWizard({
  isOpen,
  onClose,
  initData,
  initialUserCardId,
}: {
  isOpen: boolean
  onClose: () => void
  initData: string
  initialUserCardId?: number | null
}) {
  const qc = useQueryClient()
  const [step, setStep] = useState<Step>('card')
  const [userCardId, setUserCardId] = useState<number | null>(null)
  const [perkId, setPerkId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [resultCard, setResultCard] = useState<UserCardItem | null>(null)
  const [easterEggResult, setEasterEggResult] = useState<LegendaryUpgradeResponse['easterEgg']>(null)

  const cardsQ = useQuery({
    queryKey: ['cards', 'legendary-wizard', initData],
    queryFn: () => apiGet<UserCardItem[]>('/api/v1/me/cards', initData),
    enabled: isOpen && !!initData,
  })

  const infoQ = useQuery({
    queryKey: ['legendary-upgrade-info', initData],
    queryFn: () => fetchLegendaryUpgradeInfo(initData),
    enabled: isOpen && !!initData,
  })

  const perkQ = useQuery({
    queryKey: ['perks-catalog', initData],
    queryFn: () => fetchPerkCatalog(initData),
    enabled: isOpen && !!initData,
  })

  const eligible = useMemo(
    () => (cardsQ.data ?? []).filter(isEligibleEpicForLegendary),
    [cardsQ.data],
  )

  const selectedCard = userCardId != null ? cardsQ.data?.find((c) => c.id === userCardId) : undefined

  const pickablePerks = useMemo(() => {
    if (!selectedCard || !perkQ.data) return []
    const existing = new Set(selectedCard.perks.map((a) => a.perkId))
    return perkQ.data.filter((a) => !existing.has(a.id))
  }, [selectedCard, perkQ.data])

  useEffect(() => {
    if (isOpen) return
    setError(null)
    setStep('card')
    setUserCardId(null)
    setPerkId(null)
    setResultCard(null)
    setEasterEggResult(null)
  }, [isOpen])

  useEffect(() => {
    if (!isOpen || initialUserCardId == null || !cardsQ.data) return
    const c = cardsQ.data.find((x) => x.id === initialUserCardId)
    if (c && isEligibleEpicForLegendary(c)) {
      setUserCardId(c.id)
      setStep('perk')
    }
  }, [isOpen, initialUserCardId, cardsQ.data])

  const upgradeM = useMutation({
    mutationFn: () => {
      if (userCardId == null || perkId == null) throw new Error('Не выбраны данные')
      return postLegendaryUpgrade(initData, { userCardId, perkId })
    },
    onSuccess: (result) => {
      void qc.invalidateQueries({ queryKey: ['cards'] })
      void qc.invalidateQueries({ queryKey: ['me'] })
      void qc.invalidateQueries({ queryKey: ['fantasy-teams'] })
      void qc.invalidateQueries({ queryKey: ['legendary-upgrade-info'] })
      if (result.easterEgg) {
        setResultCard(result.card)
        setEasterEggResult(result.easterEgg)
        setStep('result')
        return
      }
      onClose()
    },
    onError: (e: Error) => {
      setError(e instanceof ApiError ? e.message : String(e))
    },
  })

  if (!isOpen) return null

  const cost = infoQ.data?.cost ?? null
  const canAfford = infoQ.data?.canAfford ?? false

  const goBack = () => {
    setError(null)
    if (step === 'result') {
      onClose()
      return
    }
    if (step === 'confirm') {
      setStep('perk')
      return
    }
    if (step === 'perk') {
      if (initialUserCardId != null && userCardId === initialUserCardId) {
        onClose()
      } else {
        setStep('card')
        setPerkId(null)
      }
      return
    }
    onClose()
  }

  const pickedPerk = perkId ? perkQ.data?.find((a) => a.id === perkId) : undefined

  return (
    <div
      className="pf-modal-backdrop"
      role="dialog"
      aria-modal
      aria-label="Легендарный апгрейд"
      onClick={() => !upgradeM.isPending && onClose()}
    >
      <div className="pf-modal pf-modal--legendary-wizard" onClick={(e) => e.stopPropagation()}>
        <button
          type="button"
          className="pf-modal__close"
          disabled={upgradeM.isPending}
          onClick={() => !upgradeM.isPending && onClose()}
        >
          ×
        </button>

        <h3 className="pf-modal__title">Легендарный апгрейд</h3>
        <p className="pf-muted pf-legendary-wizard__lead">
          Эпическая карта с двумя перками получает третье и становится легендарной. Стоимость:{' '}
          {cost != null ? (
            <strong>{cost.toLocaleString('ru-RU')}₣</strong>
          ) : (
            <span>…</span>
          )}
          . После апгрейда остаётся тот же экземпляр карты (+1 использование).
        </p>

        {infoQ.isLoading && <p className="pf-muted">Загрузка…</p>}
        {infoQ.data && !infoQ.data.canAfford && step !== 'card' && (
          <p className="pf-err">Недостаточно фантиков для апгрейда.</p>
        )}

        {step === 'card' && (
          <div className="pf-legendary-wizard__step">
            <p className="pf-field__label">Выберите эпическую карту</p>
            {cardsQ.isLoading && <p className="pf-muted">Загрузка карт…</p>}
            {cardsQ.isError && <p className="pf-err">{(cardsQ.error as Error).message}</p>}
            {eligible.length === 0 && !cardsQ.isLoading && (
              <p className="pf-muted">
                Нет подходящих карт: нужна EPIC с двумя перками и оставшимися использованиями.
              </p>
            )}
            <ul className="pf-legendary-wizard__card-pick">
              {eligible.map((c) => {
                const src = cardDisplayImageUrl(c)
                return (
                  <li key={c.id}>
                    <button
                      type="button"
                      className={`pf-legendary-wizard__pick-card pf-collection-card--epic`}
                      onClick={() => {
                        setUserCardId(c.id)
                        setStep('perk')
                        setPerkId(null)
                        setError(null)
                      }}
                    >
                      <div className="pf-legendary-wizard__pick-frame">
                        <PlayerImage
                          src={src}
                          seedId={c.fantasyPlayerId}
                          variant="card"
                          className="pf-legendary-wizard__pick-img"
                        />
                        <div className="pf-legendary-wizard__pick-cap">
                          <span className="pf-legendary-wizard__pick-name">{c.playerNickname}</span>
                          <CardPerkChips perks={c.perks} max={4} />
                        </div>
                      </div>
                    </button>
                  </li>
                )
              })}
            </ul>
          </div>
        )}

        {step === 'perk' && selectedCard && (
          <div className="pf-legendary-wizard__step">
            <p className="pf-field__label">Третье перк</p>
            {perkQ.isLoading && <p className="pf-muted">Каталог…</p>}
            {pickablePerks.length === 0 && !perkQ.isLoading && (
              <p className="pf-muted">Нет доступных перков (все уже на карте).</p>
            )}
            <ul className="pf-legendary-wizard__perk-list">
              {pickablePerks.map((a: PerkCatalogItem) => (
                <li key={a.id}>
                  <button
                    type="button"
                    className={`pf-legendary-wizard__perk-item ${perkId === a.id ? 'pf-legendary-wizard__perk-item--selected' : ''}`}
                    onClick={() => {
                      setPerkId(a.id)
                      setError(null)
                    }}
                  >
                    <span className="pf-legendary-wizard__perk-name">{a.name}</span>
                    <span className="pf-muted">+{a.bonusPoints}</span>
                  </button>
                </li>
              ))}
            </ul>
            <div className="pf-modal__actions">
              <button type="button" className="pf-btn pf-btn--ghost" onClick={goBack}>
                Назад
              </button>
              <button
                type="button"
                className="pf-btn"
                disabled={!perkId || !canAfford}
                onClick={() => {
                  setStep('confirm')
                  setError(null)
                }}
              >
                Далее
              </button>
            </div>
          </div>
        )}

        {step === 'confirm' && selectedCard && pickedPerk && (
          <div className="pf-legendary-wizard__step">
            <p className="pf-field__label">Подтверждение</p>
            <div className="pf-legendary-wizard__compare">
              <div>
                <p className="pf-muted pf-legendary-wizard__compare-label">Было</p>
                <div className="pf-legendary-wizard__mini pf-collection-card--epic">
                  <div className="pf-legendary-wizard__mini-frame">
                    <PlayerImage
                      src={cardDisplayImageUrl(selectedCard)}
                      seedId={selectedCard.fantasyPlayerId}
                      variant="card"
                    />
                  </div>
                  <p className="pf-legendary-wizard__mini-cap">{selectedCard.playerNickname}</p>
                  <p className="pf-muted">EPIC {rarityScoreModifierLabel('EPIC')}</p>
                  <ul className="pf-modal__ach" style={{ marginTop: 8 }}>
                    {selectedCard.perks.map((x) => (
                      <li key={x.perkId}>
                        {x.perkName}: +{x.bonusPoints}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
              <div>
                <p className="pf-muted pf-legendary-wizard__compare-label">Станет</p>
                <div className="pf-legendary-wizard__mini pf-collection-card--legendary">
                  <div className="pf-legendary-wizard__mini-frame">
                    <PlayerImage
                      src={cardDisplayImageUrl(selectedCard)}
                      seedId={selectedCard.fantasyPlayerId}
                      variant="card"
                    />
                  </div>
                  <p className="pf-legendary-wizard__mini-cap">{selectedCard.playerNickname}</p>
                  <p className="pf-muted">LEGENDARY {rarityScoreModifierLabel('LEGENDARY')}</p>
                  <ul className="pf-modal__ach" style={{ marginTop: 8 }}>
                    {[...selectedCard.perks, { perkId: pickedPerk.id, perkName: pickedPerk.name, bonusPoints: pickedPerk.bonusPoints }].map((x) => (
                      <li key={x.perkId}>
                        {x.perkName}: +{x.bonusPoints}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            </div>
            <p className="pf-muted" style={{ marginTop: 12 }}>
              Списание: <strong>{cost?.toLocaleString('ru-RU') ?? '—'}₣</strong>
            </p>
            {error && <p className="pf-err">{error}</p>}
            <div className="pf-modal__actions">
              <button type="button" className="pf-btn pf-btn--ghost" disabled={upgradeM.isPending} onClick={goBack}>
                Назад
              </button>
              <button
                type="button"
                className="pf-btn pf-btn--primary"
                disabled={upgradeM.isPending || !canAfford}
                onClick={() => upgradeM.mutate()}
              >
                {upgradeM.isPending ? 'Апгрейд…' : 'Подтвердить'}
              </button>
            </div>
          </div>
        )}

        {step === 'result' && resultCard && easterEggResult && (
          <div className="pf-legendary-wizard__step pf-legendary-wizard__easter-egg">
            <p className="pf-legendary-wizard__easter-egg-message">{easterEggResult.message}</p>
            <div className="pf-legendary-wizard__easter-egg-cards">
              <article className="pf-legendary-wizard__easter-egg-card pf-collection-card pf-collection-card--legendary pf-collection-card--legendary-crafted">
                <p className="pf-muted pf-legendary-wizard__easter-egg-label">Ваша легендарная карта</p>
                <div className="pf-legendary-wizard__easter-egg-frame">
                  <PlayerImage
                    src={cardDisplayImageUrl(resultCard)}
                    seedId={resultCard.fantasyPlayerId}
                    variant="card"
                  />
                </div>
                <p className="pf-legendary-wizard__easter-egg-name">{resultCard.playerNickname}</p>
              </article>
              <article className="pf-legendary-wizard__easter-egg-card pf-collection-card pf-collection-card--legendary">
                <p className="pf-muted pf-legendary-wizard__easter-egg-label">Найденная карта</p>
                <div className="pf-legendary-wizard__easter-egg-frame">
                  {easterEggResult.companionCardImageUrl ? (
                    <img src={easterEggResult.companionCardImageUrl} alt="" />
                  ) : (
                    <div className="pf-legendary-wizard__pick-ph">LEGENDARY</div>
                  )}
                </div>
                <p className="pf-legendary-wizard__easter-egg-name">{easterEggResult.companionCardName}</p>
              </article>
            </div>
            <p className="pf-legendary-wizard__easter-egg-bonus">
              +{easterEggResult.bonusFantiki.toLocaleString('ru-RU')} фантиков
            </p>
            <div className="pf-modal__actions">
              <button type="button" className="pf-btn pf-btn--primary" onClick={onClose}>
                OK
              </button>
            </div>
          </div>
        )}

        {step === 'card' && (
          <div className="pf-modal__actions" style={{ marginTop: 12 }}>
            <button type="button" className="pf-btn pf-btn--ghost" onClick={onClose}>
              Закрыть
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
