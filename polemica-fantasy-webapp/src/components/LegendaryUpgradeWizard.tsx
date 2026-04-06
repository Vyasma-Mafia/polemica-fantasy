import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { ApiError, apiGet } from '../api/client'
import { fetchAchievementCatalog } from '../api/achievementsCatalog'
import { fetchLegendaryUpgradeInfo, postLegendaryUpgrade } from '../api/legendaryUpgrade'
import type { AchievementCatalogItem, UserCardItem } from '../api/types'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { rarityScoreModifierLabel } from '../lib/rarity'
import { CardAchievementChips } from './CardAchievementChips'

type Step = 'card' | 'achievement' | 'confirm'

export function isEligibleEpicForLegendary(c: UserCardItem): boolean {
  return (
    c.rarity === 'EPIC' &&
    c.usesRemaining > 0 &&
    c.achievements.length === 2
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
  const [achievementId, setAchievementId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

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

  const achQ = useQuery({
    queryKey: ['achievements-catalog', initData],
    queryFn: () => fetchAchievementCatalog(initData),
    enabled: isOpen && !!initData,
  })

  const eligible = useMemo(
    () => (cardsQ.data ?? []).filter(isEligibleEpicForLegendary),
    [cardsQ.data],
  )

  const selectedCard = userCardId != null ? cardsQ.data?.find((c) => c.id === userCardId) : undefined

  const pickableAchievements = useMemo(() => {
    if (!selectedCard || !achQ.data) return []
    const existing = new Set(selectedCard.achievements.map((a) => a.achievementId))
    return achQ.data.filter((a) => !existing.has(a.id))
  }, [selectedCard, achQ.data])

  useEffect(() => {
    if (isOpen) return
    setError(null)
    setStep('card')
    setUserCardId(null)
    setAchievementId(null)
  }, [isOpen])

  useEffect(() => {
    if (!isOpen || initialUserCardId == null || !cardsQ.data) return
    const c = cardsQ.data.find((x) => x.id === initialUserCardId)
    if (c && isEligibleEpicForLegendary(c)) {
      setUserCardId(c.id)
      setStep('achievement')
    }
  }, [isOpen, initialUserCardId, cardsQ.data])

  const upgradeM = useMutation({
    mutationFn: () => {
      if (userCardId == null || achievementId == null) throw new Error('Не выбраны данные')
      return postLegendaryUpgrade(initData, { userCardId, achievementId })
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['cards'] })
      void qc.invalidateQueries({ queryKey: ['me'] })
      void qc.invalidateQueries({ queryKey: ['fantasy-teams'] })
      void qc.invalidateQueries({ queryKey: ['legendary-upgrade-info'] })
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
    if (step === 'confirm') {
      setStep('achievement')
      return
    }
    if (step === 'achievement') {
      if (initialUserCardId != null && userCardId === initialUserCardId) {
        onClose()
      } else {
        setStep('card')
        setAchievementId(null)
      }
      return
    }
    onClose()
  }

  const pickedAch = achievementId ? achQ.data?.find((a) => a.id === achievementId) : undefined

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
          Эпическая карта с двумя достижениями получает третье и становится легендарной. Стоимость:{' '}
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
                Нет подходящих карт: нужна EPIC с двумя достижениями и оставшимися использованиями.
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
                        setStep('achievement')
                        setAchievementId(null)
                        setError(null)
                      }}
                    >
                      <div className="pf-legendary-wizard__pick-frame">
                        {src ? (
                          <img src={src} alt="" className="pf-legendary-wizard__pick-img" />
                        ) : (
                          <div className="pf-legendary-wizard__pick-ph">{c.rarity}</div>
                        )}
                        <div className="pf-legendary-wizard__pick-cap">
                          <span className="pf-legendary-wizard__pick-name">{c.playerNickname}</span>
                          <CardAchievementChips achievements={c.achievements} max={4} />
                        </div>
                      </div>
                    </button>
                  </li>
                )
              })}
            </ul>
          </div>
        )}

        {step === 'achievement' && selectedCard && (
          <div className="pf-legendary-wizard__step">
            <p className="pf-field__label">Третье достижение</p>
            {achQ.isLoading && <p className="pf-muted">Каталог…</p>}
            {pickableAchievements.length === 0 && !achQ.isLoading && (
              <p className="pf-muted">Нет доступных достижений (все уже на карте).</p>
            )}
            <ul className="pf-legendary-wizard__ach-list">
              {pickableAchievements.map((a: AchievementCatalogItem) => (
                <li key={a.id}>
                  <button
                    type="button"
                    className={`pf-legendary-wizard__ach-item ${achievementId === a.id ? 'pf-legendary-wizard__ach-item--selected' : ''}`}
                    onClick={() => {
                      setAchievementId(a.id)
                      setError(null)
                    }}
                  >
                    <span className="pf-legendary-wizard__ach-name">{a.name}</span>
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
                disabled={!achievementId || !canAfford}
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

        {step === 'confirm' && selectedCard && pickedAch && (
          <div className="pf-legendary-wizard__step">
            <p className="pf-field__label">Подтверждение</p>
            <div className="pf-legendary-wizard__compare">
              <div>
                <p className="pf-muted pf-legendary-wizard__compare-label">Было</p>
                <div className="pf-legendary-wizard__mini pf-collection-card--epic">
                  <div className="pf-legendary-wizard__mini-frame">
                    {cardDisplayImageUrl(selectedCard) ? (
                      <img src={cardDisplayImageUrl(selectedCard)!} alt="" />
                    ) : (
                      <div className="pf-legendary-wizard__pick-ph">EPIC</div>
                    )}
                  </div>
                  <p className="pf-legendary-wizard__mini-cap">{selectedCard.playerNickname}</p>
                  <p className="pf-muted">EPIC {rarityScoreModifierLabel('EPIC')}</p>
                  <ul className="pf-modal__ach" style={{ marginTop: 8 }}>
                    {selectedCard.achievements.map((x) => (
                      <li key={x.achievementId}>
                        {x.achievementName}: +{x.bonusPoints}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
              <div>
                <p className="pf-muted pf-legendary-wizard__compare-label">Станет</p>
                <div className="pf-legendary-wizard__mini pf-collection-card--legendary">
                  <div className="pf-legendary-wizard__mini-frame">
                    {cardDisplayImageUrl(selectedCard) ? (
                      <img src={cardDisplayImageUrl(selectedCard)!} alt="" />
                    ) : (
                      <div className="pf-legendary-wizard__pick-ph">LEGENDARY</div>
                    )}
                  </div>
                  <p className="pf-legendary-wizard__mini-cap">{selectedCard.playerNickname}</p>
                  <p className="pf-muted">LEGENDARY {rarityScoreModifierLabel('LEGENDARY')}</p>
                  <ul className="pf-modal__ach" style={{ marginTop: 8 }}>
                    {[...selectedCard.achievements, { achievementId: pickedAch.id, achievementName: pickedAch.name, bonusPoints: pickedAch.bonusPoints }].map((x) => (
                      <li key={x.achievementId}>
                        {x.achievementName}: +{x.bonusPoints}
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
