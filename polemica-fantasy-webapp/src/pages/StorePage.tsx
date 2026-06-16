import type { QueryClient } from '@tanstack/react-query'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMarkOnboardingStep } from '../api/antiChurn'
import { ApiError, apiGet, apiSend } from '../api/client'
import type { BuyPackResponse, PackChoice, PackOpeningCard, StorePackItem, UserCardItem, UserProfile } from '../api/types'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PackChoiceOverlay } from '../components/PackChoiceOverlay'
import { PackOpening } from '../components/PackOpening'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { rarityClass } from '../lib/rarity'
import { formatUserDisplayName } from '../lib/userDisplayName'

export function StorePage() {
  const initData = useInitData()
  useMarkOnboardingStep('OPEN_STORE')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [confirmPackId, setConfirmPackId] = useState<number | null>(null)
  const [lastOpening, setLastOpening] = useState<{
    response: BuyPackResponse
    packName: string
    packId: number
  } | null>(null)
  const [pendingChoice, setPendingChoice] = useState<PackChoice | null>(null)
  const [buyError, setBuyError] = useState<string | null>(null)
  const [choiceError, setChoiceError] = useState<string | null>(null)
  const meQ = useQuery({
    queryKey: ['me', initData],
    queryFn: () => apiGet<UserProfile>('/api/v1/me', initData),
    enabled: !!initData,
  })

  const packsQ = useQuery({
    queryKey: ['store-packs', initData],
    queryFn: () => apiGet<StorePackItem[]>('/api/v1/store/packs', initData),
    enabled: !!initData,
  })

  const buyM = useMutation({
    mutationFn: (packId: number) =>
      apiSend<BuyPackResponse>('POST', `/api/v1/store/packs/${packId}/buy`, initData),
    onSuccess: (data, packId) => {
      const pack = queryClient.getQueryData<StorePackItem[]>(['store-packs', initData])?.find((p) => p.id === packId)
      if (data.kind === 'PENDING_CHOICE' && data.choice) {
        setPendingChoice(data.choice)
      } else if (data.cards.length > 0) {
        setLastOpening({ response: data, packName: pack?.name ?? 'Пак', packId })
      }
      setConfirmPackId(null)
      setBuyError(null)
      invalidateStoreQueries(queryClient)
    },
    onError: (e: Error) => {
      setBuyError(e instanceof ApiError ? e.message : String(e))
    },
  })

  const selectChoiceM = useMutation({
    mutationFn: ({ choiceId, optionId }: { choiceId: number; optionId: string }) =>
      apiSend<BuyPackResponse>('POST', `/api/v1/store/pack-choices/${choiceId}/select`, initData, { optionId }),
    onSuccess: (data) => {
      const choice = pendingChoice
      if (data.kind === 'OPENED' && data.cards.length > 0) {
        setLastOpening({ response: data, packName: choice?.packName ?? 'Пак', packId: choice?.packId ?? 0 })
      }
      setPendingChoice(null)
      setChoiceError(null)
      invalidateStoreQueries(queryClient)
    },
    onError: (e: Error) => {
      setChoiceError(e instanceof ApiError ? e.message : String(e))
    },
  })

  const balance = meQ.data?.fantiki ?? 0
  const confirmingPack = packsQ.data?.find((p) => p.id === confirmPackId)
  const confirmMax = confirmingPack?.maxOpensPerUser ?? 0
  const confirmUsed = confirmingPack?.packOpensUsed ?? 0
  const confirmAtLimit = confirmMax > 0 && confirmUsed >= confirmMax
  const openingCards = lastOpening ? resolvePackOpeningCards(lastOpening.response) : []

  if (!initData) return <MissingInitDataNotice />

  return (
    <div className="pf-page">
      <PageHeader title="Магазин" backTo="/" backLabel="Турниры" />

      {meQ.data && initData && (
        <StoreProfileNameBlock
          key={`${meQ.data.id}-${meQ.data.displayName ?? ''}`}
          me={meQ.data}
          initData={initData}
          queryClient={queryClient}
        />
      )}

      <section className="pf-store-legendary" aria-label="Легендарный апгрейд">
        <p className="pf-muted pf-store-legendary__text">
          Эпическую карту с двумя перками можно превратить в легендарную за фантики (тот же экземпляр карты, +1
          использование).
        </p>
        <button type="button" className="pf-btn pf-store-legendary__btn" onClick={() => navigate('/cards?legendaryUpgrade=1')}>
          Легендарный апгрейд
        </button>
      </section>

      {packsQ.isLoading && <p className="pf-muted">Загрузка…</p>}
      {packsQ.isError && <p className="pf-err">{(packsQ.error as Error).message}</p>}

      <ul className="pf-store-list">
        {(packsQ.data ?? []).map((pack) => {
          const freeLeft = pack.freeOpensRemaining ?? 0
          const nextIsFree = pack.priceFantiki > 0 && freeLeft > 0
          const maxOpens = pack.maxOpensPerUser ?? 0
          const packUsed = pack.packOpensUsed ?? 0
          const hasPendingChoice = pack.pendingChoice != null
          const atPackLimit = maxOpens > 0 && packUsed >= maxOpens && !hasPendingChoice
          const affordable =
            hasPendingChoice || (!atPackLimit && (pack.priceFantiki === 0 || nextIsFree || balance >= pack.priceFantiki))
          return (
            <li key={pack.id} className="pf-store-card">
              <div className="pf-store-card__head">
                <h2 className="pf-store-card__title">{pack.name}</h2>
                <span className="pf-store-card__price">
                  {pack.priceFantiki === 0
                    ? 'Бесплатно'
                    : nextIsFree
                      ? `0 🪙 (ещё ${freeLeft} беспл.)`
                      : `${pack.priceFantiki.toLocaleString('ru-RU')} 🪙`}
                </span>
              </div>
              <div className="pf-store-card__layout" aria-label="Состав пака">
                {pack.rarityLayout.map((slot) => (
                  <div key={slot.rarity} className={`pf-store-rarity-slot pf-store-rarity-slot--${rarityClass(slot.rarity)}`}>
                    <span className="pf-store-rarity-slot__label">{slot.rarity}</span>
                    <span className="pf-store-rarity-slot__count">×{slot.cardsCount}</span>
                  </div>
                ))}
              </div>
              {pack.openingMode === 'CHOOSE' && (
                <p className="pf-store-hint pf-store-hint--choice">Выбор 1 из 3 наборов</p>
              )}
              {hasPendingChoice && (
                <p className="pf-store-hint pf-muted">Покупка уже оплачена. Осталось выбрать набор.</p>
              )}
              <button
                type="button"
                className="pf-btn pf-store-buy"
                disabled={!affordable || buyM.isPending}
                onClick={() => {
                  setBuyError(null)
                  setChoiceError(null)
                  if (pack.pendingChoice) {
                    setPendingChoice(pack.pendingChoice)
                  } else {
                    setConfirmPackId(pack.id)
                  }
                }}
              >
                {pack.pendingChoice ? 'Продолжить выбор' : 'Купить'}
              </button>
              {atPackLimit && (
                <p className="pf-store-hint pf-muted">Лимит открытий исчерпан</p>
              )}
              {!atPackLimit && !affordable && pack.priceFantiki > 0 && (
                <p className="pf-store-hint pf-muted">Недостаточно фантиков</p>
              )}
              {maxOpens > 0 && !atPackLimit && (
                <p className="pf-store-hint pf-muted">
                  Осталось открытий: {Math.max(0, maxOpens - packUsed)} / {maxOpens}
                </p>
              )}
              {pack.priceFantiki > 0 && freeLeft > 0 && !atPackLimit && (
                <p className="pf-store-hint pf-muted">Бесплатных открытий: {freeLeft}</p>
              )}
            </li>
          )
        })}
      </ul>

      {(packsQ.data?.length ?? 0) === 0 && !packsQ.isLoading && (
        <p className="pf-muted">Пока нет паков в магазине.</p>
      )}

      {buyError && <p className="pf-err">{buyError}</p>}

      {lastOpening && openingCards.length > 0 && (
        <PackOpening
          key={openingCards.map((c, idx) => openingCardKey(c, idx)).join('-')}
          cards={openingCards}
          packName={lastOpening.packName}
          onBuyMore={() => {
            const id = lastOpening.packId
            setLastOpening(null)
            setBuyError(null)
            setConfirmPackId(id)
          }}
          onDismiss={() => {
            setLastOpening(null)
            navigate('/cards')
          }}
        />
      )}

      {pendingChoice && (
        <PackChoiceOverlay
          choice={pendingChoice}
          isSubmitting={selectChoiceM.isPending}
          error={choiceError}
          onSelect={(optionId) => selectChoiceM.mutate({ choiceId: pendingChoice.id, optionId })}
          onDismiss={() => {
            setPendingChoice(null)
            setChoiceError(null)
          }}
        />
      )}

      {confirmingPack && (
        <div className="pf-modal-backdrop" role="dialog" aria-modal aria-label="Подтверждение покупки" onClick={() => setConfirmPackId(null)}>
          <div className="pf-modal pf-modal--narrow" onClick={(e) => e.stopPropagation()}>
            <h3 className="pf-modal__title">Купить пак?</h3>
            <p className="pf-muted">
              {confirmingPack.name} —{' '}
              {confirmAtLimit
                ? 'лимит открытий'
                : confirmingPack.priceFantiki === 0
                  ? 'бесплатно'
                  : (confirmingPack.freeOpensRemaining ?? 0) > 0
                    ? 'бесплатно (квота)'
                    : `${confirmingPack.priceFantiki.toLocaleString('ru-RU')} фантиков`}
            </p>
            {confirmingPack.openingMode === 'CHOOSE' && (
              <p className="pf-muted">
                После покупки появятся 3 набора. В коллекцию попадёт только выбранный.
              </p>
            )}
            {confirmAtLimit && (
              <p className="pf-err" style={{ marginTop: 8 }}>
                Лимит открытий исчерпан
              </p>
            )}
            <div className="pf-modal__actions">
              <button type="button" className="pf-btn pf-btn--ghost" onClick={() => setConfirmPackId(null)}>
                Отмена
              </button>
              <button
                type="button"
                className="pf-btn"
                disabled={buyM.isPending || confirmAtLimit}
                onClick={() => buyM.mutate(confirmingPack.id)}
              >
                {buyM.isPending ? 'Покупка…' : 'Купить'}
              </button>
            </div>
          </div>
        </div>
      )}

      <p className="pf-footer-link">
        <Link to="/">← К турнирам</Link>
      </p>
    </div>
  )
}

function resolvePackOpeningCards(response: BuyPackResponse): PackOpeningCard[] {
  if (response.openingCards && response.openingCards.length > 0) {
    return response.openingCards
  }
  return buildLegacyOpeningCards(response.cards)
}

function invalidateStoreQueries(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: ['me'] })
  void queryClient.invalidateQueries({ queryKey: ['cards'] })
  void queryClient.invalidateQueries({ queryKey: ['achievements'] })
  void queryClient.invalidateQueries({ queryKey: ['store-packs'] })
  void queryClient.invalidateQueries({ queryKey: ['marketplace-listings'] })
  void queryClient.invalidateQueries({ queryKey: ['marketplace-feed'] })
}

function buildLegacyOpeningCards(cards: UserCardItem[]): PackOpeningCard[] {
  return cards.map((card) => ({
    kind: 'USER_CARD',
    card,
    rarity: card.rarity,
    value: card.value,
    relatedUserCardId: null,
    companionCardName: null,
    companionCardImageUrl: null,
  }))
}

function openingCardKey(card: PackOpeningCard, index: number): string {
  if (card.kind === 'USER_CARD' && card.card) return `user-${card.card.id}`
  if (card.relatedUserCardId != null) return `companion-${card.relatedUserCardId}`
  return `companion-${index}`
}

function StoreProfileNameBlock({
  me,
  initData,
  queryClient,
}: {
  me: UserProfile
  initData: string
  queryClient: QueryClient
}) {
  const [displayDraft, setDisplayDraft] = useState(me.displayName ?? '')
  const [displayError, setDisplayError] = useState<string | null>(null)

  const patchDisplayM = useMutation({
    mutationFn: (displayName: string | null) =>
      apiSend<UserProfile>('PATCH', '/api/v1/me', initData, { displayName }),
    onSuccess: () => {
      setDisplayError(null)
      void queryClient.invalidateQueries({ queryKey: ['me'] })
    },
    onError: (e: Error) => {
      setDisplayError(e instanceof ApiError ? e.message : String(e))
    },
  })

  return (
    <section className="pf-store-profile" aria-label="Имя в игре">
      <p className="pf-muted" style={{ marginBottom: 6 }}>
        Сейчас в списках: <strong>{formatUserDisplayName(me)}</strong>
      </p>
      <div className="pf-store-profile__row">
        <input
          type="text"
          className="pf-input"
          placeholder="Ник в фэнтези (необязательно)"
          maxLength={255}
          value={displayDraft}
          onChange={(e) => setDisplayDraft(e.target.value)}
          aria-label="Ник в фэнтези"
        />
        <button
          type="button"
          className="pf-btn"
          disabled={patchDisplayM.isPending}
          onClick={() => patchDisplayM.mutate(displayDraft.trim() === '' ? null : displayDraft.trim())}
        >
          {patchDisplayM.isPending ? 'Сохранение…' : 'Сохранить'}
        </button>
      </div>
      {displayError && <p className="pf-err">{displayError}</p>}
    </section>
  )
}
