import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import { fetchLeagueLeaderboard, fetchSeriesLeagues } from '../api/leagues'
import type { SeriesLeagueBrief, UserSeriesDetail } from '../api/types'
import { LeagueTabs } from '../components/LeagueTabs'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { SeriesStatusBadge } from '../components/StatusBadge'
import { useInitData } from '../context/useInitData'
import { defaultLeagueCode, leagueShortName, resolveActiveLeagueCode } from '../lib/leagues'
import { formatDateShortWithTime } from '../lib/tournamentDates'
import { formatUserDisplayName } from '../lib/userDisplayName'

export function SeriesPage() {
  const { seriesId } = useParams<{ seriesId: string }>()
  const id = Number(seriesId)
  const initData = useInitData()
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedLeagueCode = defaultLeagueCode(searchParams.get('league'))

  const q = useQuery({
    queryKey: ['series', id, initData],
    queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${id}`, initData),
    enabled: !!initData && Number.isFinite(id),
  })
  const leaguesQ = useQuery({
    queryKey: ['series', id, 'leagues', initData],
    queryFn: () => fetchSeriesLeagues(id, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  const fallbackLeagues: SeriesLeagueBrief[] = q.data?.leagues ?? []
  const leagues: SeriesLeagueBrief[] =
    leaguesQ.data?.map((league) => ({
      code: league.code,
      name: league.name,
      hasTeam: league.hasTeam,
      valueCap: league.valueCap,
    })) ?? fallbackLeagues
  const activeLeagueCode = resolveActiveLeagueCode(leagues, requestedLeagueCode)

  const leaderboardQ = useQuery({
    queryKey: ['series', id, 'leaderboard', activeLeagueCode, initData],
    queryFn: () => fetchLeagueLeaderboard(id, activeLeagueCode, initData),
    enabled: !!initData && Number.isFinite(id) && leagues.length > 0,
  })

  const playerFilter = searchParams.get('player') ?? ''
  const filteredLeaderboard = useMemo(() => {
    const rows = leaderboardQ.data ?? []
    if (!playerFilter) return rows
    const pid = Number(playerFilter)
    if (!Number.isFinite(pid)) return rows
    return rows.filter((r) => r.fantasyPlayerIds.includes(pid))
  }, [leaderboardQ.data, playerFilter])

  if (!initData) return <MissingInitDataNotice />
  if (q.isLoading || leaguesQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>
  if (leaguesQ.isError) return <p className="pf-err">{(leaguesQ.error as Error).message}</p>
  const s = q.data!
  const back = `/tournaments/${s.tournamentId}`
  const activeLeague = leagues.find((league) => league.code.toUpperCase() === activeLeagueCode.toUpperCase())
  const leagueName = activeLeague ? leagueShortName(activeLeague.code, activeLeague.name) : activeLeagueCode

  const setLeague = (code: string) => {
    const next = new URLSearchParams(searchParams)
    next.set('league', code.toUpperCase())
    setSearchParams(next, { replace: true })
  }
  const setPlayerFilter = (fpId: string) => {
    const next = new URLSearchParams(searchParams)
    if (fpId) {
      next.set('player', fpId)
    } else {
      next.delete('player')
    }
    setSearchParams(next, { replace: true })
  }
  const teamPath = `/series/${s.id}/team?league=${encodeURIComponent(activeLeagueCode)}`
  const leaderboardPath = `/series/${s.id}/leaderboard?league=${encodeURIComponent(activeLeagueCode)}`

  return (
    <div className="pf-page">
      <PageHeader title={s.name} backTo={back} />
      <div className="pf-hero-card pf-hero-card--compact">
        <div className="pf-hero-card__top">
          <SeriesStatusBadge status={s.status} />
        </div>
        <p className="pf-hero-card__meta">
          Дедлайн команды: {formatDateShortWithTime(new Date(s.teamDeadline))}
        </p>
      </div>
      {leagues.length > 0 && <LeagueTabs leagues={leagues} activeCode={activeLeagueCode} onChange={setLeague} />}
      <div className="pf-action-grid pf-action-grid--single">
        <Link className="pf-btn pf-btn--primary pf-action-tile" to={teamPath}>
          Собрать команду
        </Link>
        <Link className="pf-btn pf-btn--outline pf-action-tile" to={leaderboardPath}>
          Лидерборд лиги
        </Link>
      </div>
      {activeLeague?.valueCap != null && (
        <p className="pf-muted pf-league-caption">
          {leagueName}: макс. ценность команды {activeLeague.valueCap}₱
        </p>
      )}

      <section className="pf-section">
        <h2 className="pf-section-title">Лидерборд: {leagueName}</h2>
        <label className="pf-field">
          <span className="pf-field__label">Фильтр по игроку</span>
          <select
            className="pf-input"
            value={playerFilter}
            onChange={(e) => setPlayerFilter(e.target.value)}
          >
            <option value="">Все команды</option>
            {s.players.map((p) => (
              <option key={p.fantasyPlayerId} value={String(p.fantasyPlayerId)}>
                {p.nickname}
              </option>
            ))}
          </select>
        </label>
        {leaderboardQ.isLoading && <p className="pf-muted">Загрузка таблицы…</p>}
        {leaderboardQ.isError && <p className="pf-err">{(leaderboardQ.error as Error).message}</p>}
        {!leaderboardQ.isLoading && !leaderboardQ.isError && (
          <ul className="pf-lb-list">
            {filteredLeaderboard.map((row) => (
              <li key={row.rank + '-' + row.user.telegramId}>
                <Link to={`/players/${row.user.telegramId}`} className="pf-lb-row pf-lb-row--link">
                  <span className="pf-lb-rank">#{row.rank}</span>
                  <span className="pf-lb-name">{formatUserDisplayName(row.user)}</span>
                  <span className="pf-lb-score">
                    {row.totalScore != null ? row.totalScore.toFixed(2) : '—'}
                    <span className="pf-lb-score-label">очков</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
        {!leaderboardQ.isLoading && !leaderboardQ.isError && filteredLeaderboard.length === 0 && (
          <p className="pf-muted">
            {playerFilter ? 'Нет команд с этим игроком в составе.' : 'Пока нет команд в этой лиге.'}
          </p>
        )}
      </section>

      <section className="pf-section">
        <h2 className="pf-section-title">Игроки серии</h2>
        <ul className="pf-participants">
          {s.players.map((p) => (
            <li key={p.tournamentPlayerId} className="pf-participants__row">
              {p.photoUrl ? (
                <img src={p.photoUrl} alt="" className="pf-participants__avatar" />
              ) : (
                <div className="pf-participants__avatar pf-participants__avatar--ph" aria-hidden>
                  ?
                </div>
              )}
              <span className="pf-participants__name">{p.nickname}</span>
            </li>
          ))}
        </ul>
      </section>

      <section className="pf-section">
        <h2 className="pf-section-title">Игры</h2>
        <ul className="pf-link-list pf-link-list--plain">
          {s.games.map((g) => (
            <li key={g.polemicaGameId} className="pf-game-row">
              <span>{g.gameName}</span>
              <span className="pf-muted">{g.scored ? '✓ учтено' : '…'}</span>
            </li>
          ))}
        </ul>
        {s.games.length === 0 && <p className="pf-muted">Игры ещё не синхронизированы.</p>}
      </section>

      <p className="pf-footer-link">
        <Link to={back}>← К турниру</Link>
      </p>
    </div>
  )
}
