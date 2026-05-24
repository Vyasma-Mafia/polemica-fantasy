import { useQuery } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router-dom'
import { fetchPlayerProfile } from '../api/playerProfile'
import type { PlayerMarketplaceTrade, PlayerProfile, PlayerSeriesResult, Rarity } from '../api/types'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { SeriesStatusBadge } from '../components/StatusBadge'
import { useInitData } from '../context/useInitData'
import { leagueShortName } from '../lib/leagues'
import { rarityClass } from '../lib/rarity'
import { shareToTelegram } from '../lib/shareLinks'
import { formatDateShort } from '../lib/tournamentDates'
import { formatUserDisplayName } from '../lib/userDisplayName'

const RARITY_ORDER: Rarity[] = ['LEGENDARY', 'EPIC', 'RARE', 'COMMON']

const RARITY_LABEL: Record<Rarity, string> = {
  COMMON: 'Обычные',
  RARE: 'Редкие',
  EPIC: 'Эпик',
  LEGENDARY: 'Легенды',
}

function formatValue(n: number): string {
  return n.toLocaleString('ru-RU')
}

function RatingSection({ profile }: { profile: PlayerProfile }) {
  const r = profile.rating
  if (!r) return null
  return (
    <section className="pf-section">
      <h2 className="pf-section-title">Рейтинг</h2>
      <div className="pf-profile-rating">
        <div className="pf-profile-rating__rank">#{r.rank}</div>
        <div className="pf-profile-rating__item">
          <span className="pf-profile-rating__label">Баланс ₣</span>
          <span className="pf-profile-rating__value">{formatValue(r.fantikiBalance)}</span>
        </div>
        <div className="pf-profile-rating__item">
          <span className="pf-profile-rating__label">Карты ₱</span>
          <span className="pf-profile-rating__value">{formatValue(r.cardsValue)}</span>
        </div>
        <div className="pf-profile-rating__item">
          <span className="pf-profile-rating__label">Призовые ₣</span>
          <span className="pf-profile-rating__value">{formatValue(r.prizeWinnings)}</span>
        </div>
        <div className="pf-profile-rating__item">
          <span className="pf-profile-rating__label">Всего</span>
          <span className="pf-profile-rating__value">{formatValue(r.totalValue)}</span>
        </div>
      </div>
    </section>
  )
}

function SeriesHistorySection({ history, telegramId }: { history: PlayerSeriesResult[]; telegramId: string }) {
  if (history.length === 0) return null
  return (
    <section className="pf-section">
      <h2 className="pf-section-title">Серии</h2>
      <ul className="pf-profile-series-list">
        {history.map((s) => (
          <li key={`${s.seriesId}-${s.leagueCode}`}>
            <Link
              to={`/series/${s.seriesId}/leaderboard/player/${telegramId}?league=${encodeURIComponent(s.leagueCode)}`}
              className="pf-profile-series-item"
            >
              <div className="pf-profile-series-item__info">
                <span className="pf-profile-series-item__name">{s.seriesName}</span>
                <span className="pf-profile-series-item__meta">
                  {s.tournamentName} · {leagueShortName(s.leagueCode, s.leagueName)}
                </span>
              </div>
              <div className="pf-profile-series-item__stats">
                {s.rank != null ? (
                  <span className="pf-profile-series-item__rank">
                    #{s.rank}
                    <span className="pf-profile-series-item__of"> из {s.participantsCount}</span>
                  </span>
                ) : (
                  <SeriesStatusBadge status={s.status} />
                )}
                {s.totalScore != null && (
                  <span className="pf-profile-series-item__score">{s.totalScore.toFixed(2)} очк.</span>
                )}
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

function CollectionSection({ profile }: { profile: PlayerProfile }) {
  const c = profile.collectionSummary
  return (
    <section className="pf-section">
      <h2 className="pf-section-title">Коллекция: {c.totalCards} карт</h2>
      <div className="pf-profile-collection">
        {RARITY_ORDER.map((r) => {
          const count = c.byRarity[r] ?? 0
          if (count === 0) return null
          return (
            <span key={r} className={`pf-profile-collection__item pf-profile-collection__item--${rarityClass(r)}`}>
              {RARITY_LABEL[r]}: {count}
            </span>
          )
        })}
      </div>
    </section>
  )
}

function MarketplaceSection({ profile }: { profile: PlayerProfile }) {
  const m = profile.marketplaceStats
  return (
    <section className="pf-section">
      <h2 className="pf-section-title">Маркетплейс</h2>
      <div className="pf-profile-market-stats">
        <div className="pf-profile-market-stats__item">
          <span className="pf-profile-market-stats__value">{m.activeSalesCount}</span>
          <span className="pf-profile-market-stats__label">Активных</span>
        </div>
        <div className="pf-profile-market-stats__item">
          <span className="pf-profile-market-stats__value">{m.totalSoldCount}</span>
          <span className="pf-profile-market-stats__label">Продано</span>
        </div>
        <div className="pf-profile-market-stats__item">
          <span className="pf-profile-market-stats__value">{m.totalPurchasedCount}</span>
          <span className="pf-profile-market-stats__label">Куплено</span>
        </div>
      </div>
    </section>
  )
}

function TradesSection({
  trades,
  backPath,
}: {
  trades: PlayerMarketplaceTrade[]
  backPath: string
}) {
  if (trades.length === 0) return null
  return (
    <section className="pf-section">
      <h2 className="pf-section-title">Последние сделки</h2>
      <ul className="pf-profile-trades-list">
        {trades.map((t, i) => (
          <li key={`${t.listingId}-${i}`}>
            <Link
              to={`/marketplace/transactions/${t.listingId}`}
              state={{ backTo: backPath, backLabel: 'Профиль' }}
              className={`pf-profile-trade-item pf-profile-trade-item--link${t.sanctioned ? ' pf-profile-trade-item--sanctioned' : ''}`}
            >
              <div className="pf-profile-trade-item__main">
                <span className={`pf-profile-trade-item__type pf-profile-trade-item__type--${t.type.toLowerCase()}`}>
                  {t.type === 'SALE' ? 'Продажа' : 'Покупка'}
                </span>
                <span className="pf-profile-trade-item__player">{t.playerName}</span>
                <span className={`pf-rarity-dot pf-rarity-dot--${rarityClass(t.rarity)}`} />
              </div>
              <div className="pf-profile-trade-item__details">
                <span className="pf-profile-trade-item__price">
                  <span className="pf-profile-trade-item__price-value">{formatValue(t.price)} ₣</span>
                  {t.sanctioned && <span className="pf-sanctioned-badge">Нерыночная</span>}
                </span>
                <span className="pf-profile-trade-item__party">{t.counterpartyDisplayName}</span>
                <span className="pf-profile-trade-item__date">{formatDateShort(new Date(t.date))}</span>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

export function PlayerProfilePage() {
  const { telegramId } = useParams<{ telegramId: string }>()
  const location = useLocation()
  const tgId = Number(telegramId)
  const initData = useInitData()

  const q = useQuery({
    queryKey: ['player-profile', tgId, initData],
    queryFn: () => fetchPlayerProfile(tgId, initData!),
    enabled: !!initData && Number.isFinite(tgId),
  })

  if (!initData) return <MissingInitDataNotice />
  if (q.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>

  const profile = q.data!
  const name = formatUserDisplayName(profile.user)
  const username = profile.user.username ? `@${profile.user.username}` : null

  return (
    <div className="pf-page">
      <PageHeader title={name} backTo="/rating" />

      <div className="pf-profile-header">
        {username && <p className="pf-profile-header__username">{username}</p>}
        <p className="pf-profile-header__since">
          Участник с {formatDateShort(new Date(profile.memberSince))}
        </p>
        <div className="pf-share-row">
          <button
            type="button"
            className="pf-btn pf-btn--small pf-btn--outline"
            onClick={() =>
              shareToTelegram(
                { kind: 'profile', telegramId: profile.user.telegramId },
                `Профиль ${name} в Polemica Fantasy`,
              )
            }
          >
            Поделиться профилем
          </button>
        </div>
      </div>

      <RatingSection profile={profile} />
      <SeriesHistorySection history={profile.seriesHistory} telegramId={telegramId!} />
      <CollectionSection profile={profile} />
      <MarketplaceSection profile={profile} />
      <TradesSection
        trades={profile.recentTrades}
        backPath={location.pathname + location.search}
      />
    </div>
  )
}
