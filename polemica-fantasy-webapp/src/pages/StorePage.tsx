import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, apiGet, apiSend } from '../api/client'
import type { BuyPackResponse, StorePackItem, UserProfile } from '../api/types'
import { PackOpening } from '../components/PackOpening'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/InitDataContext'
import { rarityClass } from '../lib/rarity'

export function StorePage() {
  const initData = useInitData()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [confirmPackId, setConfirmPackId] = useState<number | null>(null)
  const [lastOpening, setLastOpening] = useState<{ response: BuyPackResponse; packName: string } | null>(null)
  const [buyError, setBuyError] = useState<string | null>(null)

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
      if (data.cards.length > 0) {
        setLastOpening({ response: data, packName: pack?.name ?? 'Пак' })
      }
      setConfirmPackId(null)
      setBuyError(null)
      void queryClient.invalidateQueries({ queryKey: ['me'] })
      void queryClient.invalidateQueries({ queryKey: ['cards'] })
    },
    onError: (e: Error) => {
      setBuyError(e instanceof ApiError ? e.message : String(e))
    },
  })

  const balance = meQ.data?.fantiki ?? 0
  const confirmingPack = packsQ.data?.find((p) => p.id === confirmPackId)

  if (!initData) return <p className="pf-muted">Нужен initData.</p>

  return (
    <div className="pf-page">
      <PageHeader title="Магазин" backTo="/" backLabel="Турниры" />

      {packsQ.isLoading && <p className="pf-muted">Загрузка…</p>}
      {packsQ.isError && <p className="pf-err">{(packsQ.error as Error).message}</p>}

      <ul className="pf-store-list">
        {(packsQ.data ?? []).map((pack) => {
          const affordable = balance >= pack.priceFantiki
          return (
            <li key={pack.id} className="pf-store-card">
              <div className="pf-store-card__head">
                <h2 className="pf-store-card__title">{pack.name}</h2>
                <span className="pf-store-card__price">
                  {pack.priceFantiki === 0 ? 'Бесплатно' : `${pack.priceFantiki.toLocaleString('ru-RU')} 🪙`}
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
              <button
                type="button"
                className="pf-btn pf-store-buy"
                disabled={!affordable || buyM.isPending}
                onClick={() => {
                  setBuyError(null)
                  setConfirmPackId(pack.id)
                }}
              >
                Купить
              </button>
              {!affordable && pack.priceFantiki > 0 && (
                <p className="pf-store-hint pf-muted">Недостаточно фантиков</p>
              )}
            </li>
          )
        })}
      </ul>

      {(packsQ.data?.length ?? 0) === 0 && !packsQ.isLoading && (
        <p className="pf-muted">Пока нет паков в магазине.</p>
      )}

      {buyError && <p className="pf-err">{buyError}</p>}

      {lastOpening && lastOpening.response.cards.length > 0 && (
        <PackOpening
          key={lastOpening.response.cards.map((c) => c.id).join('-')}
          cards={lastOpening.response.cards}
          packName={lastOpening.packName}
          onDismiss={() => {
            setLastOpening(null)
            navigate('/cards')
          }}
        />
      )}

      {confirmingPack && (
        <div className="pf-modal-backdrop" role="dialog" aria-modal aria-label="Подтверждение покупки" onClick={() => setConfirmPackId(null)}>
          <div className="pf-modal pf-modal--narrow" onClick={(e) => e.stopPropagation()}>
            <h3 className="pf-modal__title">Купить пак?</h3>
            <p className="pf-muted">
              {confirmingPack.name} —{' '}
              {confirmingPack.priceFantiki === 0 ? 'бесплатно' : `${confirmingPack.priceFantiki.toLocaleString('ru-RU')} фантиков`}
            </p>
            <div className="pf-modal__actions">
              <button type="button" className="pf-btn pf-btn--ghost" onClick={() => setConfirmPackId(null)}>
                Отмена
              </button>
              <button
                type="button"
                className="pf-btn"
                disabled={buyM.isPending}
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
