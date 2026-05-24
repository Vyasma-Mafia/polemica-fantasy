import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, apiGet } from '../api/client'
import { fetchLeagueLeaderboard } from '../api/leagues'
import type {
  FantasyTeamDto,
  FantasyTeamSeriesDetails,
  LeaderboardEntry,
  PublicFantasyTeam,
  UserCardItem,
  UserProfile,
  UserSeriesDetail,
} from '../api/types'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { defaultLeagueCode, leagueShortName } from '../lib/leagues'
import { shareToTelegram } from '../lib/shareLinks'
import { formatUserDisplayName } from '../lib/userDisplayName'

async function getOrNull<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null
    throw error
  }
}

function rankFor(rows: LeaderboardEntry[] | undefined, telegramId: number | undefined): number | null {
  if (telegramId == null) return null
  return rows?.find((row) => row.user.telegramId === telegramId)?.rank ?? null
}

function totalByGame(details: FantasyTeamSeriesDetails | null | undefined): Map<number, number | null> {
  const m = new Map<number, number | null>()
  for (let gi = 0; gi < (details?.games.length ?? 0); gi += 1) {
    const game = details!.games[gi]!
    let total = 0
    let hasAny = false
    for (const col of details!.columns) {
      const value = col.cells[gi]?.totalScore
      if (value != null && Number.isFinite(value)) {
        total += value
        hasAny = true
      }
    }
    m.set(game.seriesGameId, hasAny ? Math.round(total * 100) / 100 : null)
  }
  return m
}

function scoreText(score: number | null | undefined): string {
  return score != null ? score.toFixed(2) : '—'
}

function TeamSummary({
  title,
  name,
  rank,
  score,
  cards,
  emptyAction,
}: {
  title: string
  name: string
  rank: number | null
  score: number | null | undefined
  cards: PublicFantasyTeam['slots'] | null
  emptyAction?: string
}) {
  return (
    <section className="pf-compare-card">
      <p className="pf-compare-card__eyebrow">{title}</p>
      <h2 className="pf-compare-card__name">{name}</h2>
      <div className="pf-compare-stats">
        <span>Место: {rank != null ? `#${rank}` : '—'}</span>
        <strong>{scoreText(score)} очков</strong>
      </div>
      {cards && cards.length > 0 ? (
        <ul className="pf-compare-cards">
          {cards
            .slice()
            .sort((a, b) => a.slot - b.slot)
            .map((slot) => {
              const img = cardDisplayImageUrl(slot.card)
              return (
                <li key={slot.slot} className="pf-compare-card-row">
                  {img ? <img src={img} alt="" /> : <span className="pf-compare-card-row__ph" />}
                  <span>{slot.card.playerNickname}</span>
                  <strong>{scoreText(slot.score)}</strong>
                </li>
              )
            })}
        </ul>
      ) : (
        <p className="pf-muted">{emptyAction ?? 'Команды нет.'}</p>
      )}
    </section>
  )
}

export function SeriesComparePage() {
  const { seriesId, telegramId } = useParams<{ seriesId: string; telegramId: string }>()
  const sid = Number(seriesId)
  const targetTelegramId = Number(telegramId)
  const initData = useInitData()
  const [searchParams] = useSearchParams()
  const leagueCode = defaultLeagueCode(searchParams.get('league'))

  const seriesQ = useQuery({
    queryKey: ['series', sid, initData],
    queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${sid}`, initData),
    enabled: !!initData && Number.isFinite(sid),
  })
  const meQ = useQuery({
    queryKey: ['me', initData],
    queryFn: () => apiGet<UserProfile>('/api/v1/me', initData),
    enabled: !!initData,
  })
  const leaderboardQ = useQuery({
    queryKey: ['leaderboard', sid, leagueCode, initData],
    queryFn: () => fetchLeagueLeaderboard(sid, leagueCode, initData),
    enabled: !!initData && Number.isFinite(sid),
  })
  const myTeamQ = useQuery({
    queryKey: ['fantasy-team', sid, leagueCode, initData],
    queryFn: () =>
      getOrNull(
        apiGet<FantasyTeamDto>(
          `/api/v1/me/fantasy-teams/${sid}?leagueCode=${encodeURIComponent(leagueCode)}`,
          initData,
        ),
      ),
    enabled: !!initData && Number.isFinite(sid),
    retry: false,
  })
  const targetTeamQ = useQuery({
    queryKey: ['public-fantasy-team', sid, targetTelegramId, leagueCode, initData],
    queryFn: () =>
      getOrNull(
        apiGet<PublicFantasyTeam>(
          `/api/v1/series/${sid}/users/${targetTelegramId}/fantasy-team?leagueCode=${encodeURIComponent(leagueCode)}`,
          initData,
        ),
      ),
    enabled: !!initData && Number.isFinite(sid) && Number.isFinite(targetTelegramId),
    retry: false,
  })
  const myCardsQ = useQuery({
    queryKey: ['cards', 'compare', sid, initData],
    queryFn: () => apiGet<UserCardItem[]>(`/api/v1/me/cards?seriesId=${sid}`, initData),
    enabled: !!initData && Number.isFinite(sid) && myTeamQ.data != null,
  })
  const myDetailsQ = useQuery({
    queryKey: ['fantasy-team-details', sid, leagueCode, initData],
    queryFn: () =>
      getOrNull(
        apiGet<FantasyTeamSeriesDetails>(
          `/api/v1/me/fantasy-teams/${sid}/details?leagueCode=${encodeURIComponent(leagueCode)}`,
          initData,
        ),
      ),
    enabled: !!initData && myTeamQ.data != null,
  })
  const targetDetailsQ = useQuery({
    queryKey: ['public-fantasy-team-details', sid, targetTelegramId, leagueCode, initData],
    queryFn: () =>
      getOrNull(
        apiGet<FantasyTeamSeriesDetails>(
          `/api/v1/series/${sid}/users/${targetTelegramId}/fantasy-team/details?leagueCode=${encodeURIComponent(leagueCode)}`,
          initData,
        ),
      ),
    enabled: !!initData && targetTeamQ.data != null,
  })

  if (!initData) return <MissingInitDataNotice />
  if (seriesQ.isLoading || meQ.isLoading || leaderboardQ.isLoading || myTeamQ.isLoading || targetTeamQ.isLoading) {
    return <p className="pf-loading">Загрузка…</p>
  }
  const firstError = [seriesQ, meQ, leaderboardQ, myTeamQ, targetTeamQ, myDetailsQ, targetDetailsQ]
    .find((q) => q.isError)?.error
  if (firstError) return <p className="pf-err">{(firstError as Error).message}</p>

  const series = seriesQ.data!
  const myTelegramId = meQ.data?.telegramId
  const targetName =
    targetTeamQ.data?.owner != null
      ? formatUserDisplayName(targetTeamQ.data.owner)
      : `Игрок ${targetTelegramId}`
  const myRank = rankFor(leaderboardQ.data, myTelegramId)
  const targetRank = rankFor(leaderboardQ.data, targetTelegramId)
  const myScore = myTeamQ.data?.totalScore ?? null
  const targetScore = targetTeamQ.data?.totalScore ?? null
  const diff = myScore != null && targetScore != null ? myScore - targetScore : null
  const myCardById = new Map((myCardsQ.data ?? []).map((card) => [card.id, card]))
  const myTotals = totalByGame(myDetailsQ.data)
  const targetTotals = totalByGame(targetDetailsQ.data)
  const games = myDetailsQ.data?.games ?? targetDetailsQ.data?.games ?? []

  return (
    <div className="pf-page">
      <PageHeader
        title="Сравнение"
        subtitle={`${series.name} · ${leagueShortName(leagueCode)}`}
        backTo={`/series/${sid}/leaderboard/player/${targetTelegramId}?league=${encodeURIComponent(leagueCode)}`}
      />

      <div className="pf-share-row">
        <button
          type="button"
          className="pf-btn pf-btn--small pf-btn--outline"
          onClick={() =>
            shareToTelegram(
              { kind: 'compareS', seriesId: sid, telegramId: targetTelegramId, leagueCode },
              `Сравни нас в ${series.name}, ${leagueShortName(leagueCode)}`,
            )
          }
        >
          Поделиться сравнением
        </button>
      </div>

      <div className="pf-compare-grid">
        <TeamSummary
          title="Ты"
          name={formatUserDisplayName(meQ.data!)}
          rank={myRank}
          score={myScore}
          cards={
            myTeamQ.data
              ? myTeamQ.data.slots.map((slot) => ({
                  slot: slot.slot,
                  score: slot.score,
                  card: myCardById.get(slot.userCardId) ?? {
                    id: slot.userCardId,
                    acquiredAt: '',
                    cardTemplateId: 0,
                    fantasyPlayerId: 0,
                    rarity: 'COMMON',
                    imageUrl: null,
                    description: null,
                    playerNickname: `Карта #${slot.userCardId}`,
                    playerPhotoUrl: null,
                    achievements: [],
                    usesRemaining: 0,
                    timesRenewed: 0,
                    value: 0,
                  },
                }))
              : null
          }
          emptyAction={`Нет команды. Собери состав в ${leagueShortName(leagueCode)}.`}
        />
        <TeamSummary
          title="Выбранный пользователь"
          name={targetName}
          rank={targetRank}
          score={targetScore}
          cards={targetTeamQ.data?.slots ?? null}
        />
      </div>

      <section className="pf-section">
        <h2 className="pf-section-title">Разница</h2>
        <div className="pf-compare-delta">
          {diff != null ? (
            <strong className={diff >= 0 ? 'pf-compare-delta--positive' : 'pf-compare-delta--negative'}>
              {diff >= 0 ? '+' : ''}
              {diff.toFixed(2)}
            </strong>
          ) : (
            <span className="pf-muted">Недостаточно данных для сравнения.</span>
          )}
          {myTeamQ.data == null && (
            <Link className="pf-btn pf-btn--small pf-btn--primary" to={`/series/${sid}/team?league=${encodeURIComponent(leagueCode)}`}>
              Собрать команду
            </Link>
          )}
        </div>
      </section>

      {games.length > 0 && (
        <section className="pf-section">
          <h2 className="pf-section-title">По играм</h2>
          <div className="pf-rating-table-wrap">
            <table className="pf-rating-table pf-compare-table">
              <thead>
                <tr>
                  <th>Игра</th>
                  <th className="pf-rating__th--num">Ты</th>
                  <th className="pf-rating__th--num">{targetName}</th>
                </tr>
              </thead>
              <tbody>
                {games.map((game) => (
                  <tr key={game.seriesGameId}>
                    <td>{game.gameName}</td>
                    <td className="pf-rating__num">{scoreText(myTotals.get(game.seriesGameId))}</td>
                    <td className="pf-rating__num">{scoreText(targetTotals.get(game.seriesGameId))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  )
}
