import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiGet } from '../api/client'
import {
  buyMarketplaceListing,
  fetchMarketplaceFeed,
  fetchMarketplaceListings,
} from '../api/marketplace'
import type {
  FantasyPlayerBrief,
  MarketplaceListingEntry,
  MarketplaceSortBy,
  Rarity,
  UserTournamentDetail,
} from '../api/types'
import { CardAchievementChips } from '../components/CardAchievementChips'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { RARITY_UI, rarityScoreModifierLabel } from '../lib/rarity'
import { rarityClass } from '../lib/rarity'

const SORT_OPTIONS: { value: MarketplaceSortBy; label: string }[] = [
  { value: 'created_at_desc', label: 'Сначала новые' },
  { value: 'price_asc', label: 'Цена ↑' },
  { value: 'price_desc', label: 'Цена ↓' },
]

export function MarketplacePage() {
  const initData = useInitData()
  const qc = useQueryClient()

  const [rarity, setRarity] = useState<Rarity | ''>('')
  const [sortBy, setSortBy] = useState<MarketplaceSortBy>('created_at_desc')
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [page, setPage] = useState(0)
  const [tournamentId, setTournamentId] = useState('')
  const [seriesId, setSeriesId] = useState('')
  const [playerFilterId, setPlayerFilterId] = useState<number | ''>('')

  const [buyConfirm, setBuyConfirm] = useState<MarketplaceListingEntry | null>(null)
  const [buyError, setBuyError] = useState<string | null>(null)

  const tournamentsQ = useQuery({
    queryKey: ['tournaments', initData],
    queryFn: () => apiGet<{ id: number; name: string }[]>('/api/v1/tournaments', initData),
    enabled: !!initData,
  })

  const tournamentDetailQ = useQuery({
    queryKey: ['tournament-detail', tournamentId, initData],
    queryFn: () => apiGet<UserTournamentDetail>(`/api/v1/tournaments/${tournamentId}`, initData),
    enabled: !!initData && tournamentId !== '',
  })

  const fantasyPlayersQ = useQuery({
    queryKey: ['fantasy-players', initData],
    queryFn: () => apiGet<FantasyPlayerBrief[]>('/api/v1/fantasy-players', initData),
    enabled: !!initData,
  })

  const minP = minPrice.trim() === '' ? undefined : Number(minPrice)
  const maxP = maxPrice.trim() === '' ? undefined : Number(maxPrice)
  const minOk = minP === undefined || Number.isFinite(minP)
  const maxOk = maxP === undefined || Number.isFinite(maxP)

  const listingsParams = useMemo(() => {
    const scopeTournament =
      seriesId === '' && tournamentId !== '' ? Number(tournamentId) : undefined
    const scopeSeries = seriesId !== '' ? Number(seriesId) : undefined
    return {
      fantasyPlayerId: playerFilterId === '' ? undefined : playerFilterId,
      tournamentId: scopeTournament,
      seriesId: scopeSeries,
      rarity: rarity || undefined,
      minPrice: minOk ? minP : undefined,
      maxPrice: maxOk ? maxP : undefined,
      sortBy,
      page,
      size: 20,
    }
  }, [playerFilterId, tournamentId, seriesId, rarity, minOk, maxOk, minP, maxP, sortBy, page])

  const listingsQ = useQuery({
    queryKey: ['marketplace-listings', initData, listingsParams],
    queryFn: () => fetchMarketplaceListings(initData, listingsParams),
    enabled: !!initData && minOk && maxOk,
  })

  const feedQ = useQuery({
    queryKey: ['marketplace-feed', initData],
    queryFn: () => fetchMarketplaceFeed(initData, 20),
    enabled: !!initData,
  })

  const buyM = useMutation({
    mutationFn: (listingId: number) => buyMarketplaceListing(initData, listingId),
    onSuccess: (data) => {
      setBuyConfirm(null)
      setBuyError(null)
      void qc.invalidateQueries({ queryKey: ['me'] })
      void qc.invalidateQueries({ queryKey: ['cards'] })
      void qc.invalidateQueries({ queryKey: ['fantasy-teams'] })
      void qc.invalidateQueries({ queryKey: ['marketplace-listings'] })
      void qc.invalidateQueries({ queryKey: ['marketplace-feed'] })
      void qc.invalidateQueries({ queryKey: ['ownership-history', data.card.id] })
    },
    onError: (e: Error) => {
      setBuyError(e instanceof ApiError ? e.message : String(e))
    },
  })

  if (!initData) return <MissingInitDataNotice />

  const totalPages = listingsQ.data?.totalPages ?? 0

  return (
    <div className="pf-page">
      <PageHeader title="Маркетплейс" backTo="/" backLabel="Турниры" />

      <p className="pf-footer-link" style={{ marginBottom: 12 }}>
        <Link to="/marketplace/my">Мои листинги</Link>
        {' · '}
        <Link to="/cards">Коллекция</Link>
      </p>

      <section className="pf-marketplace-feed" aria-label="Последние сделки">
        <h2 className="pf-marketplace-feed__title">Последние покупки</h2>
        {feedQ.isLoading && <p className="pf-muted">Загрузка ленты…</p>}
        {feedQ.isError && <p className="pf-err">{(feedQ.error as Error).message}</p>}
        {feedQ.data && feedQ.data.items.length === 0 && (
          <p className="pf-muted">Пока нет сделок на маркетплейсе.</p>
        )}
        {feedQ.data && feedQ.data.items.length > 0 && (
          <div className="pf-marketplace-feed__strip">
            {feedQ.data.items.map((it, i) => {
              const c = it.card
              const img = cardDisplayImageUrl({
                playerPhotoUrl: c.playerPhotoUrl,
                imageUrl: null,
              })
              return (
                <div key={`${it.soldAt}-${i}`} className="pf-marketplace-feed__chip">
                  <div className="pf-marketplace-feed__chip-row">
                    <div className="pf-marketplace-feed__chip-copy">
                      <span className={`pf-rarity-tag pf-rarity-tag--${rarityClass(it.rarity)}`}>{it.rarity}</span>
                      <span className="pf-marketplace-feed__chip-name">{it.playerName}</span>
                      <span className="pf-marketplace-feed__chip-price">{it.price}₣</span>
                      <span className="pf-marketplace-feed__chip-buyer">
                        {it.sellerDisplayName} → {it.buyerDisplayName}
                      </span>
                    </div>
                    <div
                      className={`pf-marketplace-feed__thumb pf-mini-card pf-mini-card--${rarityClass(c.rarity)}`}
                      aria-hidden
                      title={c.playerName}
                    >
                      {img ? (
                        <img src={img} alt="" />
                      ) : (
                        <div className="pf-mini-card__ph" />
                      )}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </section>

      <div className="pf-filters pf-marketplace-filters">
        <label className="pf-field">
          <span className="pf-field__label">Игрок</span>
          <select
            className="pf-input"
            value={playerFilterId === '' ? '' : String(playerFilterId)}
            onChange={(e) => {
              const v = e.target.value
              setPlayerFilterId(v === '' ? '' : Number(v))
              setPage(0)
            }}
            disabled={fantasyPlayersQ.isLoading}
          >
            <option value="">Все игроки</option>
            {(fantasyPlayersQ.data ?? []).map((p) => (
              <option key={p.id} value={String(p.id)}>
                {p.nickname}
              </option>
            ))}
          </select>
        </label>
        <label className="pf-field">
          <span className="pf-field__label">Турнир</span>
          <select
            className="pf-input"
            value={tournamentId}
            onChange={(e) => {
              setTournamentId(e.target.value)
              setSeriesId('')
              setPage(0)
            }}
          >
            <option value="">—</option>
            {(tournamentsQ.data ?? []).map((t) => (
              <option key={t.id} value={String(t.id)}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
        {tournamentId && (
          <label className="pf-field">
            <span className="pf-field__label">Серия</span>
            <select
              className="pf-input"
              value={seriesId}
              onChange={(e) => {
                setSeriesId(e.target.value)
                setPage(0)
              }}
            >
              <option value="">Весь турнир</option>
              {(tournamentDetailQ.data?.series ?? []).map((s) => (
                <option key={s.id} value={String(s.id)}>
                  {s.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <label className="pf-field">
          <span className="pf-field__label">Сортировка</span>
          <select
            className="pf-input"
            value={sortBy}
            onChange={(e) => {
              setSortBy(e.target.value as MarketplaceSortBy)
              setPage(0)
            }}
          >
            {SORT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <label className="pf-field">
          <span className="pf-field__label">Цена от</span>
          <input
            className="pf-input"
            inputMode="numeric"
            value={minPrice}
            onChange={(e) => {
              setMinPrice(e.target.value)
              setPage(0)
            }}
            placeholder="—"
          />
        </label>
        <label className="pf-field">
          <span className="pf-field__label">Цена до</span>
          <input
            className="pf-input"
            inputMode="numeric"
            value={maxPrice}
            onChange={(e) => {
              setMaxPrice(e.target.value)
              setPage(0)
            }}
            placeholder="—"
          />
        </label>
      </div>

      <div className="pf-rarity-tabs">
        {RARITY_UI.map((tab) => (
          <button
            key={tab.label}
            type="button"
            className={`pf-rarity-tab ${rarity === tab.value ? 'pf-rarity-tab--active' : ''}`}
            onClick={() => {
              setRarity(tab.value)
              setPage(0)
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {listingsQ.isLoading && <p className="pf-muted">Загрузка…</p>}
      {listingsQ.isError && <p className="pf-err">{(listingsQ.error as Error).message}</p>}

      <ul className="pf-collection-grid">
        {(listingsQ.data?.content ?? []).map((row) => {
          const c = row.card
          const img = cardDisplayImageUrl({
            playerPhotoUrl: c.playerPhotoUrl,
            imageUrl: null,
          })
          const achForChips = c.achievements.map((a) => ({
            achievementId: a.achievementId,
            achievementName: a.name,
            bonusPoints: a.bonusPoints,
          }))
          return (
            <li key={row.listingId} className={`pf-collection-card pf-collection-card--${rarityClass(c.rarity)}`}>
              <div className="pf-collection-card__frame">
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
                    <CardAchievementChips achievements={achForChips} max={4} />
                  </div>
                </div>
                <div className="pf-marketplace-card__meta">
                  <div className="pf-marketplace-card__price">{row.price}₣</div>
                  <div className="pf-muted pf-marketplace-card__seller">Продавец: {row.seller.displayName}</div>
                  {row.canBuy ? (
                    <button
                      type="button"
                      className="pf-btn pf-btn--small pf-btn--primary"
                      disabled={buyM.isPending}
                      onClick={() => {
                        setBuyError(null)
                        setBuyConfirm(row)
                      }}
                    >
                      Купить за {row.price}₣
                    </button>
                  ) : (
                    <p className="pf-marketplace-card__reason" title={row.canBuyReason ?? ''}>
                      Недоступно{row.canBuyReason ? `: ${row.canBuyReason}` : ''}
                    </p>
                  )}
                </div>
              </div>
            </li>
          )
        })}
      </ul>

      {listingsQ.data && totalPages > 1 && (
        <div className="pf-marketplace-pagination">
          <button
            type="button"
            className="pf-btn pf-btn--small"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Назад
          </button>
          <span className="pf-muted">
            Стр. {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            className="pf-btn pf-btn--small"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Вперёд
          </button>
        </div>
      )}

      {buyConfirm && (
        <div
          className="pf-modal-backdrop"
          role="dialog"
          aria-modal
          aria-label="Подтверждение покупки"
          onClick={() => !buyM.isPending && setBuyConfirm(null)}
        >
          <div className="pf-modal" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              className="pf-modal__close"
              disabled={buyM.isPending}
              onClick={() => setBuyConfirm(null)}
            >
              ×
            </button>
            <h3 className="pf-modal__title">Купить карту?</h3>
            <p className="pf-muted">{buyConfirm.card.playerName}</p>
            <p>
              Цена: <strong>{buyConfirm.price}₣</strong>
            </p>
            {buyError && <p className="pf-err">{buyError}</p>}
            <div className="pf-modal__economy-actions">
              <button
                type="button"
                className="pf-btn pf-btn--small"
                disabled={buyM.isPending}
                onClick={() => setBuyConfirm(null)}
              >
                Отмена
              </button>
              <button
                type="button"
                className="pf-btn pf-btn--small pf-btn--primary"
                disabled={buyM.isPending}
                onClick={() => buyM.mutate(buyConfirm.listingId)}
              >
                {buyM.isPending ? 'Покупка…' : `Заплатить ${buyConfirm.price}₣`}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
