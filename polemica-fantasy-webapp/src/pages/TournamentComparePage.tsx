import { useQueries, useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useTrackProductEvent } from '../api/antiChurn'
import { ApiError, apiGet } from '../api/client'
import { fetchLeagueLeaderboard } from '../api/leagues'
import type { LeaderboardEntry, SeriesLeagueBrief, UserProfile, UserTournamentDetail } from '../api/types'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { aggregateTournamentLeaderboards } from '../lib/aggregateLeaderboard'
import { defaultLeagueCode, leagueShortName, resolveActiveLeagueCode } from '../lib/leagues'
import { shareToTelegram } from '../lib/shareLinks'
import { formatUserDisplayName } from '../lib/userDisplayName'

function seriesScore(board: LeaderboardEntry[] | undefined, telegramId: number | undefined): number | null {
  if (telegramId == null) return null
  return board?.find((row) => row.user.telegramId === telegramId)?.totalScore ?? null
}

function scoreText(score: number | null | undefined): string {
  return score != null ? score.toFixed(2) : '—'
}

export function TournamentComparePage() {
  const { tournamentId, telegramId } = useParams<{ tournamentId: string; telegramId: string }>()
  const tid = Number(tournamentId)
  const targetTelegramId = Number(telegramId)
  const initData = useInitData()
  const track = useTrackProductEvent()
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedLeagueCode = defaultLeagueCode(searchParams.get('league'))

  const tournamentQ = useQuery({
    queryKey: ['tournament', tid, initData],
    queryFn: () => apiGet<UserTournamentDetail>(`/api/v1/tournaments/${tid}`, initData),
    enabled: !!initData && Number.isFinite(tid),
  })
  const meQ = useQuery({
    queryKey: ['me', initData],
    queryFn: () => apiGet<UserProfile>('/api/v1/me', initData),
    enabled: !!initData,
  })

  const series = tournamentQ.data?.series ?? []
  const availableLeagues: SeriesLeagueBrief[] = (() => {
    const byCode = new Map<string, SeriesLeagueBrief>()
    for (const s of series) {
      for (const league of s.leagues ?? []) {
        const key = league.code.toUpperCase()
        if (!byCode.has(key)) byCode.set(key, { ...league, valueCap: null })
      }
    }
    return [...byCode.values()]
  })()
  const activeLeagueCode = resolveActiveLeagueCode(availableLeagues, requestedLeagueCode)

  useEffect(() => {
    if (!Number.isFinite(tid) || !Number.isFinite(targetTelegramId)) return
    track({
      eventType: 'COMPARE_OPEN',
      subjectType: 'TOURNAMENT_COMPARE',
      subjectId: tid,
      metadata: { leagueCode: activeLeagueCode, telegramId: targetTelegramId },
    })
  }, [activeLeagueCode, targetTelegramId, tid, track])

  const leaderboardQueries = useQueries({
    queries: series.map((s) => ({
      queryKey: ['leaderboard', s.id, activeLeagueCode, initData],
      queryFn: async () => {
        try {
          return await fetchLeagueLeaderboard(s.id, activeLeagueCode, initData)
        } catch (error) {
          if (error instanceof ApiError && error.status === 404) return []
          throw error
        }
      },
      enabled: !!initData && series.length > 0 && availableLeagues.length > 0,
    })),
  })

  if (!initData) return <MissingInitDataNotice />
  if (tournamentQ.isLoading || meQ.isLoading || leaderboardQueries.some((q) => q.isLoading)) {
    return <p className="pf-loading">Загрузка…</p>
  }
  const firstError = [tournamentQ, meQ, ...leaderboardQueries].find((q) => q.isError)?.error
  if (firstError) return <p className="pf-err">{(firstError as Error).message}</p>

  const tournament = tournamentQ.data!
  const me = meQ.data!
  const boards = leaderboardQueries.map((q) => q.data ?? [])
  const aggregateRows = aggregateTournamentLeaderboards(boards)
  const myRow = aggregateRows.find((row) => row.telegramId === me.telegramId) ?? null
  const targetRow = aggregateRows.find((row) => row.telegramId === targetTelegramId) ?? null
  const targetUser = boards.flatMap((board) => board).find((row) => row.user.telegramId === targetTelegramId)?.user
  const targetName = targetRow?.displayName ?? (targetUser ? formatUserDisplayName(targetUser) : `Игрок ${targetTelegramId}`)
  const diff = myRow && targetRow ? myRow.totalScore - targetRow.totalScore : null

  const setLeague = (code: string) => {
    const next = new URLSearchParams(searchParams)
    next.set('league', code.toUpperCase())
    setSearchParams(next, { replace: true })
  }

  return (
    <div className="pf-page">
      <PageHeader
        title="Сравнение"
        subtitle={`${tournament.name} · ${leagueShortName(activeLeagueCode)}`}
        backTo={`/tournaments/${tid}/leaderboard?league=${encodeURIComponent(activeLeagueCode)}`}
      />

      {availableLeagues.length > 1 && (
        <div className="pf-rarity-tabs">
          {availableLeagues.map((league) => (
            <button
              key={league.code}
              type="button"
              className={`pf-rarity-tab ${league.code.toUpperCase() === activeLeagueCode ? 'pf-rarity-tab--active' : ''}`}
              onClick={() => setLeague(league.code)}
            >
              {leagueShortName(league.code, league.name)}
            </button>
          ))}
        </div>
      )}

      <div className="pf-share-row">
        <button
          type="button"
          className="pf-btn pf-btn--small pf-btn--outline"
          onClick={() =>
            shareToTelegram(
              { kind: 'compareT', tournamentId: tid, telegramId: targetTelegramId, leagueCode: activeLeagueCode },
              `Сравни нас в турнире ${tournament.name}, ${leagueShortName(activeLeagueCode)}`,
            )
          }
        >
          Поделиться сравнением
        </button>
      </div>

      <div className="pf-compare-grid">
        <section className="pf-compare-card">
          <p className="pf-compare-card__eyebrow">Ты</p>
          <h2 className="pf-compare-card__name">{formatUserDisplayName(me)}</h2>
          <div className="pf-compare-stats">
            <span>Место: {myRow ? `#${myRow.rank}` : '—'}</span>
            <strong>{scoreText(myRow?.totalScore)} очков</strong>
          </div>
        </section>
        <section className="pf-compare-card">
          <p className="pf-compare-card__eyebrow">Выбранный пользователь</p>
          <h2 className="pf-compare-card__name">{targetName}</h2>
          <div className="pf-compare-stats">
            <span>Место: {targetRow ? `#${targetRow.rank}` : '—'}</span>
            <strong>{scoreText(targetRow?.totalScore)} очков</strong>
          </div>
        </section>
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
        </div>
      </section>

      <section className="pf-section">
        <h2 className="pf-section-title">По сериям</h2>
        <div className="pf-rating-table-wrap">
          <table className="pf-rating-table pf-compare-table">
            <thead>
              <tr>
                <th>Серия</th>
                <th className="pf-rating__th--num">Ты</th>
                <th className="pf-rating__th--num">{targetName}</th>
              </tr>
            </thead>
            <tbody>
              {series.map((s, i) => (
                <tr key={s.id}>
                  <td>
                    <Link to={`/series/${s.id}/leaderboard?league=${encodeURIComponent(activeLeagueCode)}`}>
                      {s.name}
                    </Link>
                  </td>
                  <td className="pf-rating__num">{scoreText(seriesScore(boards[i], me.telegramId))}</td>
                  <td className="pf-rating__num">{scoreText(seriesScore(boards[i], targetTelegramId))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
