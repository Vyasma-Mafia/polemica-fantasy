import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import { fetchLeagueLeaderboard, fetchSeriesLeagues } from '../api/leagues'
import type { SeriesLeagueBrief, UserProfile, UserSeriesDetail } from '../api/types'
import { LeagueTabs } from '../components/LeagueTabs'
import { LeaderboardPinnedBlock } from '../components/LeaderboardPinnedBlock'
import { PageHeader } from '../components/PageHeader'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { useInitData } from '../context/useInitData'
import { defaultLeagueCode, leagueShortName, resolveActiveLeagueCode } from '../lib/leagues'
import { splitLeaderboardByTelegramId } from '../lib/leaderboardSelf'
import { formatUserDisplayName } from '../lib/userDisplayName'

export function LeaderboardPage() {
  const { seriesId } = useParams<{ seriesId: string }>()
  const id = Number(seriesId)
  const initData = useInitData()
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedLeagueCode = defaultLeagueCode(searchParams.get('league'))

  const seriesMeta = useQuery({
    queryKey: ['series', id, initData],
    queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${id}`, initData),
    enabled: !!initData && Number.isFinite(id),
  })
  const leaguesQ = useQuery({
    queryKey: ['series', id, 'leagues', initData],
    queryFn: () => fetchSeriesLeagues(id, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  const fallbackLeagues: SeriesLeagueBrief[] = seriesMeta.data?.leagues ?? []
  const leagues: SeriesLeagueBrief[] =
    leaguesQ.data?.map((league) => ({
      code: league.code,
      name: league.name,
      hasTeam: league.hasTeam,
      valueCap: league.valueCap,
    })) ?? fallbackLeagues
  const activeLeagueCode = resolveActiveLeagueCode(leagues, requestedLeagueCode)
  const activeLeague = leagues.find((league) => league.code.toUpperCase() === activeLeagueCode.toUpperCase())
  const leagueName = activeLeague ? leagueShortName(activeLeague.code, activeLeague.name) : activeLeagueCode

  const q = useQuery({
    queryKey: ['leaderboard', id, activeLeagueCode, initData],
    queryFn: () => fetchLeagueLeaderboard(id, activeLeagueCode, initData),
    enabled: !!initData && Number.isFinite(id) && leagues.length > 0,
  })

  const meQ = useQuery({
    queryKey: ['me', initData],
    queryFn: () => apiGet<UserProfile>('/api/v1/me', initData),
    enabled: !!initData,
  })

  if (!initData) return <MissingInitDataNotice />
  if (seriesMeta.isLoading || leaguesQ.isLoading || q.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>
  if (leaguesQ.isError) return <p className="pf-err">{(leaguesQ.error as Error).message}</p>

  const rows = q.data ?? []
  const myTg = meQ.data?.telegramId
  const { pinned, rest } = splitLeaderboardByTelegramId(rows, myTg)
  const s = seriesMeta.data
  const back = s ? `/tournaments/${s.tournamentId}` : '/'
  const setLeague = (code: string) => {
    const next = new URLSearchParams(searchParams)
    next.set('league', code.toUpperCase())
    setSearchParams(next, { replace: true })
  }

  return (
    <div className="pf-page">
      <PageHeader title="Лидерборд" subtitle={s ? `${s.name} · ${leagueName}` : leagueName} backTo={back} />
      {leagues.length > 0 && <LeagueTabs leagues={leagues} activeCode={activeLeagueCode} onChange={setLeague} />}

      {pinned && (
        <LeaderboardPinnedBlock>
          <Link
            to={`/series/${id}/leaderboard/player/${pinned.user.telegramId}?league=${encodeURIComponent(activeLeagueCode)}`}
            className="pf-lb-row pf-lb-row--link"
          >
            <span className="pf-lb-rank">#{pinned.rank}</span>
            <span className="pf-lb-name">{formatUserDisplayName(pinned.user)}</span>
            <span className="pf-lb-score">
              {pinned.totalScore != null ? pinned.totalScore.toFixed(2) : '—'}
              <span className="pf-lb-score-label">очков</span>
            </span>
          </Link>
        </LeaderboardPinnedBlock>
      )}

      <ul className="pf-lb-list">
        {rest.map((r) => (
          <li key={r.rank + '-' + r.user.telegramId}>
            <Link
              to={`/series/${id}/leaderboard/player/${r.user.telegramId}?league=${encodeURIComponent(activeLeagueCode)}`}
              className="pf-lb-row pf-lb-row--link"
            >
              <span className="pf-lb-rank">#{r.rank}</span>
              <span className="pf-lb-name">{formatUserDisplayName(r.user)}</span>
              <span className="pf-lb-score">
                {r.totalScore != null ? r.totalScore.toFixed(2) : '—'}
                <span className="pf-lb-score-label">очков</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
      {rows.length === 0 && <p className="pf-muted">Пока нет команд.</p>}

      <p className="pf-footer-link">
        <Link to={`/series/${id}?league=${encodeURIComponent(activeLeagueCode)}`}>← К серии</Link>
      </p>
    </div>
  )
}
