import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchPerkCatalog } from '../api/perksCatalog'
import { ApiError, apiGet } from '../api/client'
import { useCreateMarketplaceWatch } from '../api/notifications'
import {
  buyMarketplaceListing,
  fetchMarketplaceFeed,
  fetchMarketplaceListings,
} from '../api/marketplace'
import { fetchEconomyInfo } from '../api/userEconomy'
import type {
  FantasyPlayerBrief,
  MarketplaceListingEntry,
  MarketplaceSortBy,
  Rarity,
  UserTournamentDetail,
} from '../api/types'
import { CardPerkChips } from '../components/CardPerkChips'
import { ContractReissueBadge } from '../components/ContractReissueBadge'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { skinClass } from '../lib/cardFrameClasses'
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
  const createWatchM = useCreateMarketplaceWatch()

  const [rarity, setRarity] = useState<Rarity | ''>('')
  const [sortBy, setSortBy] = useState<MarketplaceSortBy>('created_at_desc')
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [page, setPage] = useState(0)
  const [tournamentId, setTournamentId] = useState('')
  const [seriesId, setSeriesId] = useState('')
  const [playerFilterId, setPlayerFilterId] = useState<number | ''>('')
  const [selectedPerkIds, setSelectedPerkIds] = useState<string[]>([])
  const [contractFilter, setContractFilter] = useState<number | ''>('')

  const [buyConfirm, setBuyConfirm] = useState<MarketplaceListingEntry | null>(null)
  const [buyError, setBuyError] = useState<string | null>(null)
  const [watchState, setWatchState] = useState<'idle' | 'saving' | 'tracked' | 'duplicate' | 'limit' | 'error'>('idle')
  const [watchMessage, setWatchMessage] = useState<string | null>(null)

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

  const perksQ = useQuery({
    queryKey: ['perks-catalog', initData],
    queryFn: () => fetchPerkCatalog(initData!),
    enabled: !!initData,
  })

  const economyQ = useQuery({
    queryKey: ['economy-info', initData],
    queryFn: () => fetchEconomyInfo(initData!),
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
      perkIds: selectedPerkIds,
      minTimesRenewed: contractFilter === '' ? undefined : contractFilter,
      maxTimesRenewed: contractFilter === '' ? undefined : contractFilter,
      sortBy,
      page,
      size: 20,
    }
  }, [playerFilterId, tournamentId, seriesId, rarity, minOk, maxOk, minP, maxP, selectedPerkIds, contractFilter, sortBy, page])

  const watchPayload = useMemo(() => {
    const hasCriteria =
      playerFilterId !== '' ||
      tournamentId !== '' ||
      rarity !== '' ||
      selectedPerkIds.length > 0 ||
      contractFilter !== ''
    if (!hasCriteria) return null
    const parsedMaxPrice = maxPrice.trim() === '' ? null : Number(maxPrice)
    return {
      fantasyPlayerId: playerFilterId === '' ? null : playerFilterId,
      tournamentId: tournamentId === '' ? null : Number(tournamentId),
      rarity: rarity || null,
      maxPrice:
        parsedMaxPrice != null && Number.isFinite(parsedMaxPrice) && parsedMaxPrice > 0
          ? Math.floor(parsedMaxPrice)
          : null,
      minTimesRenewed: contractFilter === '' ? null : contractFilter,
      maxTimesRenewed: contractFilter === '' ? null : contractFilter,
      perkIds: selectedPerkIds,
    }
  }, [playerFilterId, tournamentId, rarity, maxPrice, selectedPerkIds, contractFilter])

  const watchPayloadKey = useMemo(() => JSON.stringify(watchPayload), [watchPayload])

  useEffect(() => {
    setWatchState('idle')
    setWatchMessage(null)
  }, [watchPayloadKey])

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

  const watchButtonLabel =
    watchState === 'saving'
      ? 'Сохраняем…'
      : watchState === 'tracked'
        ? '✓ Отслеживается'
        : watchState === 'duplicate'
          ? 'Уже отслеживается'
          : watchState === 'limit'
            ? 'Достигнут лимит фильтров'
            : watchState === 'error'
              ? 'Повторить отслеживание'
              : '🔔 Отслеживать этот фильтр'

  async function handleCreateWatch() {
    if (!watchPayload) return
    setWatchState('saving')
    setWatchMessage(null)
    try {
      await createWatchM.mutateAsync(watchPayload)
      setWatchState('tracked')
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setWatchState('duplicate')
        return
      }
      if (
        error instanceof ApiError &&
        error.status === 400 &&
        error.message.toLowerCase().includes('maximum')
      ) {
        setWatchState('limit')
        return
      }
      setWatchState('error')
      setWatchMessage(error instanceof ApiError ? error.message : String(error))
    }
  }

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
            {feedQ.data.items.map((it) => {
              const c = it.card
              const img = cardDisplayImageUrl({
                playerPhotoUrl: c.playerPhotoUrl,
                imageUrl: null,
              })
              const skinMod = skinClass(c.skinCode)
              return (
                <Link
                  key={it.listingId}
                  to={`/marketplace/transactions/${it.listingId}`}
                  state={{ backTo: '/marketplace', backLabel: 'Маркетплейс' }}
                  className={`pf-marketplace-feed__chip pf-marketplace-feed__chip-link${it.sanctioned ? ' pf-marketplace-feed__item--sanctioned' : ''}`}
                >
                  <div className="pf-marketplace-feed__chip-row">
                    <div className="pf-marketplace-feed__chip-copy">
                      <span className={`pf-rarity-tag pf-rarity-tag--${rarityClass(it.rarity)}`}>{it.rarity}</span>
                      <span className="pf-marketplace-feed__chip-name">{it.playerName}</span>
                      <span className="pf-marketplace-feed__chip-price">
                        <span className="pf-marketplace-feed__chip-price-value">{it.price}₣</span>
                        {it.sanctioned && <span className="pf-sanctioned-badge">Нерыночная</span>}
                      </span>
                      <span className="pf-marketplace-feed__chip-buyer">
                        {it.sellerDisplayName} → {it.buyerDisplayName}
                      </span>
                    </div>
                    <div
                      className={`pf-marketplace-feed__thumb pf-mini-card pf-mini-card--${rarityClass(c.rarity)}${skinMod ? ` pf-mini-card${skinMod}` : ''}`}
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
                </Link>
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
          <span className="pf-field__label">Перки</span>
          <select
            className="pf-input"
            multiple
            value={selectedPerkIds}
            onChange={(e) => {
              setSelectedPerkIds(
                Array.from(e.currentTarget.selectedOptions, (option) => option.value),
              )
              setPage(0)
            }}
            disabled={perksQ.isLoading}
          >
            {(perksQ.data ?? []).map((perk) => (
              <option key={perk.id} value={perk.id}>
                {perk.name}
              </option>
            ))}
          </select>
        </label>
        <label className="pf-field">
          <span className="pf-field__label">Контракт</span>
          <select
            className="pf-input"
            value={contractFilter === '' ? '' : String(contractFilter)}
            onChange={(e) => {
              const v = e.target.value
              setContractFilter(v === '' ? '' : Number(v))
              setPage(0)
            }}
            disabled={economyQ.isLoading}
          >
            <option value="">Любой</option>
            {Array.from({ length: Math.max(0, economyQ.data?.maxRenewals ?? 0) }, (_, i) => (
              <option key={i} value={String(i)}>
                ↻ {i}/{economyQ.data?.maxRenewals ?? 0}
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

      {watchPayload && (
        <div className="pf-marketplace-watch-cta">
          <button
            type="button"
            className="pf-btn pf-btn--small pf-btn--outline"
            onClick={handleCreateWatch}
            disabled={watchState === 'saving' || watchState === 'tracked' || watchState === 'duplicate' || watchState === 'limit'}
          >
            {watchButtonLabel}
          </button>
          {watchMessage && <p className="pf-err pf-err--inline">{watchMessage}</p>}
        </div>
      )}

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
          const skinMod = skinClass(c.skinCode)
          const img = cardDisplayImageUrl({
            playerPhotoUrl: c.playerPhotoUrl,
            imageUrl: null,
          })
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
                  <div className="pf-marketplace-card__price-row" aria-label="Цена в фантиках и ценность карты">
                    <span>
                      <span className="pf-muted">Цена: </span>
                      <span className="pf-marketplace-card__price-fantiki">{row.price}₣</span>
                    </span>
                    {c.value != null && (
                      <>
                        <span className="pf-muted" aria-hidden>
                          ·
                        </span>
                        <span>
                          <span className="pf-muted">Ценность: </span>
                          <span className="pf-marketplace-card__value-pill">{c.value}₱</span>
                        </span>
                      </>
                    )}
                  </div>
                  {row.seller && (
                    <div className="pf-muted pf-marketplace-card__seller">Продавец: {row.seller.displayName}</div>
                  )}
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
