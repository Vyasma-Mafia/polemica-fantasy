import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import {
  cancelMarketplaceListing,
  fetchMyMarketplaceListings,
  updateMarketplaceListingPrice,
} from '../api/marketplace'
import { fetchEconomyInfo } from '../api/userEconomy'
import { CardPerkChips } from '../components/CardPerkChips'
import { ContractReissueBadge } from '../components/ContractReissueBadge'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { skinClass } from '../lib/cardFrameClasses'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { rarityClass } from '../lib/rarity'
import { rarityScoreModifierLabel } from '../lib/rarity'

export function MyListingsPage() {
  const initData = useInitData()
  const qc = useQueryClient()
  const [editListingId, setEditListingId] = useState<number | null>(null)
  const [editPriceDraft, setEditPriceDraft] = useState('')

  const q = useQuery({
    queryKey: ['my-marketplace-listings', initData],
    queryFn: () => fetchMyMarketplaceListings(initData),
    enabled: !!initData,
  })

  const economyQ = useQuery({
    queryKey: ['economy-info', initData],
    queryFn: () => fetchEconomyInfo(initData!),
    enabled: !!initData,
  })

  const updatePriceM = useMutation({
    mutationFn: ({ listingId, price }: { listingId: number; price: number }) =>
      updateMarketplaceListingPrice(initData, listingId, { price }),
    onSuccess: () => {
      setEditListingId(null)
      void qc.invalidateQueries({ queryKey: ['my-marketplace-listings'] })
      void qc.invalidateQueries({ queryKey: ['marketplace-listings'] })
    },
    onError: (e: Error) => window.alert(e instanceof ApiError ? e.message : String(e)),
  })

  const cancelM = useMutation({
    mutationFn: (listingId: number) => cancelMarketplaceListing(initData, listingId),
    onSuccess: (_, listingId) => {
      setEditListingId((id) => (id === listingId ? null : id))
      void qc.invalidateQueries({ queryKey: ['my-marketplace-listings'] })
      void qc.invalidateQueries({ queryKey: ['marketplace-listings'] })
      void qc.invalidateQueries({ queryKey: ['cards'] })
    },
    onError: (e: Error) => window.alert(e instanceof ApiError ? e.message : String(e)),
  })

  if (!initData) return <MissingInitDataNotice />

  return (
    <div className="pf-page">
      <PageHeader title="Мои листинги" backTo="/marketplace" backLabel="Маркетплейс" />

      <p className="pf-footer-link" style={{ marginBottom: 12 }}>
        <Link to="/marketplace">← К маркетплейсу</Link>
      </p>

      {q.isLoading && <p className="pf-muted">Загрузка…</p>}
      {q.isError && <p className="pf-err">{(q.error as Error).message}</p>}

      {q.data && q.data.length === 0 && <p className="pf-muted">Нет активных объявлений.</p>}

      <ul className="pf-collection-grid">
        {(q.data ?? []).map((row) => {
          const c = row.card
          const skinMod = skinClass(c.skinCode)
          const img = cardDisplayImageUrl({ playerPhotoUrl: c.playerPhotoUrl, imageUrl: null })
          const perkForChips = c.perks.map((a) => ({
            perkId: a.perkId,
            perkName: a.name,
            bonusPoints: a.bonusPoints,
          }))
          return (
            <li
              key={row.listingId}
              className={`pf-collection-card pf-collection-card--${rarityClass(c.rarity)}${skinMod ? ` pf-collection-card${skinMod}` : ''}`}
            >
              <div className="pf-collection-card__frame">
                <ContractReissueBadge timesRenewed={c.timesRenewed} maxRenewals={c.maxRenewals} />
                <div className="pf-collection-card__open pf-marketplace-card__open">
                  {img ? (
                    <img src={img} alt="" className="pf-collection-card__img" />
                  ) : (
                    <div className="pf-collection-card__ph">{c.rarity}</div>
                  )}
                  <div className="pf-collection-card__cap">
                    <span className="pf-collection-card__name">{c.playerName}</span>
                    <span className="pf-collection-card__rarity">
                      {c.rarity}{' '}
                      <span className="pf-rarity-mod" title="Множитель очков в фэнтези">
                        {rarityScoreModifierLabel(c.rarity)}
                      </span>
                    </span>
                    <CardPerkChips perks={perkForChips} max={4} />
                  </div>
                </div>
                <div className="pf-marketplace-card__meta">
                  {editListingId === row.listingId && !economyQ.data && (
                    <>
                      <p className="pf-muted" style={{ fontSize: '0.75rem' }}>
                        {economyQ.isError
                          ? 'Не удалось загрузить лимиты цен. Попробуйте позже.'
                          : 'Загрузка…'}
                      </p>
                      <button
                        type="button"
                        className="pf-btn pf-btn--small"
                        onClick={() => {
                          setEditListingId(null)
                          setEditPriceDraft('')
                        }}
                      >
                        Отмена
                      </button>
                    </>
                  )}
                  {editListingId === row.listingId && economyQ.data ? (
                    <>
                      <label className="pf-field" style={{ marginBottom: 6 }}>
                        <span className="pf-field__label" style={{ fontSize: '0.75rem' }}>
                          Цена (мин. {c.minListingPrice}₣, макс.{' '}
                          {economyQ.data.marketplaceMaxPrices[c.rarity]}₣)
                        </span>
                        <input
                          className="pf-input"
                          inputMode="numeric"
                          value={editPriceDraft}
                          onChange={(e) => setEditPriceDraft(e.target.value)}
                          disabled={updatePriceM.isPending}
                        />
                      </label>
                      {(() => {
                        const price = Number(editPriceDraft)
                        const pct = economyQ.data.marketplaceCommissionPercent ?? 10
                        const commission = Number.isFinite(price) ? Math.floor((price * pct) / 100) : 0
                        const youGet = Number.isFinite(price) ? price - commission : 0
                        return (
                          <p className="pf-muted" style={{ fontSize: '0.7rem', margin: '0 0 6px' }}>
                            Комиссия {pct}%: {Number.isFinite(price) ? `${commission}₣` : '—'}. Вы получите:{' '}
                            <strong>
                              {Number.isFinite(price) && price > 0 ? `${youGet}₣` : '—'}
                            </strong>
                          </p>
                        )
                      })()}
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                        <button
                          type="button"
                          className="pf-btn pf-btn--small pf-btn--primary"
                          disabled={updatePriceM.isPending || !economyQ.data}
                          onClick={() => {
                            if (!economyQ.data) return
                            const min = c.minListingPrice
                            const max = economyQ.data.marketplaceMaxPrices[c.rarity]
                            const price = Number(editPriceDraft)
                            if (!Number.isFinite(price) || price < min) {
                              window.alert(`Минимальная цена для этой редкости: ${min}₣`)
                              return
                            }
                            if (!Number.isFinite(price) || price > max) {
                              window.alert(`Максимальная цена для этой редкости: ${max}₣`)
                              return
                            }
                            if (price === row.price) {
                              setEditListingId(null)
                              return
                            }
                            updatePriceM.mutate({ listingId: row.listingId, price })
                          }}
                        >
                          {updatePriceM.isPending ? 'Сохранение…' : 'Сохранить'}
                        </button>
                        <button
                          type="button"
                          className="pf-btn pf-btn--small"
                          disabled={updatePriceM.isPending}
                          onClick={() => {
                            setEditListingId(null)
                            setEditPriceDraft('')
                          }}
                        >
                          Отмена
                        </button>
                      </div>
                    </>
                  ) : editListingId !== row.listingId ? (
                    <div className="pf-marketplace-card__price">{row.price}₣</div>
                  ) : null}
                  <p className="pf-muted" style={{ fontSize: '0.75rem', margin: '4px 0' }}>
                    Выставлено: {new Date(row.createdAt).toLocaleString()}
                  </p>
                  {editListingId !== row.listingId && (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 6 }}>
                      <button
                        type="button"
                        className="pf-btn pf-btn--small"
                        disabled={cancelM.isPending || updatePriceM.isPending || !economyQ.data}
                        onClick={() => {
                          setEditListingId(row.listingId)
                          setEditPriceDraft(String(row.price))
                        }}
                      >
                        Изменить цену
                      </button>
                    </div>
                  )}
                  <button
                    type="button"
                    className="pf-btn pf-btn--small"
                    disabled={cancelM.isPending}
                    onClick={() => {
                      if (!window.confirm('Снять карту с продажи?')) return
                      cancelM.mutate(row.listingId)
                    }}
                  >
                    Снять с продажи
                  </button>
                </div>
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
