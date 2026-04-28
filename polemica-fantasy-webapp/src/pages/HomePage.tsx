import { useQueries, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiGet } from '../api/client'
import { fetchSeriesLeagues } from '../api/leagues'
import type { SeriesLeagueInfo, SeriesOpenForTeam, UserTournament } from '../api/types'
import { TournamentStatusBadge } from '../components/StatusBadge'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { useInitData } from '../context/useInitData'
import { leagueShortName } from '../lib/leagues'
import { formatDateShortWithTime } from '../lib/tournamentDates'

export function HomePage() {
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['tournaments', initData],
    queryFn: () => apiGet<UserTournament[]>('/api/v1/tournaments', initData),
    enabled: !!initData,
  })
  const openSeriesQ = useQuery({
    queryKey: ['tournaments', 'series-open-for-team', initData],
    queryFn: () => apiGet<SeriesOpenForTeam[]>('/api/v1/tournaments/series-open-for-team', initData),
    enabled: !!initData,
  })
  const openSeriesIds = openSeriesQ.data?.map((series) => series.seriesId) ?? []
  const openSeriesLeaguesQ = useQueries({
    queries: openSeriesIds.map((seriesId) => ({
      queryKey: ['series', seriesId, 'leagues', initData],
      queryFn: () => fetchSeriesLeagues(seriesId, initData),
      enabled: !!initData && openSeriesIds.length > 0,
    })),
  })

  if (!initData) {
    return <MissingInitDataNotice />
  }

  /** v5: `isLoading` is only pending+fetching; avoid rendering with no data during the brief pending+idle gap. */
  const tournamentsBooting = q.isPending && q.data === undefined && !q.isError
  const openBooting = openSeriesQ.isPending && openSeriesQ.data === undefined && !openSeriesQ.isError
  const openSeriesLeaguesBooting =
    openSeriesIds.length > 0 &&
    openSeriesLeaguesQ.some((item) => (item.isPending || item.isLoading) && item.data === undefined)
  if (tournamentsBooting || openBooting || openSeriesLeaguesBooting) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>

  const list = q.data ?? []
  const openSeries = openSeriesQ.data ?? []
  const leaguesBySeriesId = new Map<number, SeriesLeagueInfo[]>()
  for (let i = 0; i < openSeries.length; i++) {
    leaguesBySeriesId.set(openSeries[i].seriesId, openSeriesLeaguesQ[i]?.data ?? [])
  }
  const openSeriesError = openSeriesLeaguesQ.find((item) => item.isError)?.error as Error | undefined

  const openSeriesStatus = (seriesId: number): string => {
    const leagues = leaguesBySeriesId.get(seriesId) ?? []
    if (leagues.length === 0) return 'Лиги недоступны'
    return leagues
      .map((league) => `${leagueShortName(league.code, league.name)} ${league.hasTeam ? '✓' : '✗'}`)
      .join(' / ')
  }

  const openSeriesCta = (seriesId: number) => {
    const leagues = leaguesBySeriesId.get(seriesId) ?? []
    const missing = leagues.filter((league) => !league.hasTeam)
    if (missing.length === 0) {
      return { to: `/series/${seriesId}`, label: 'Изменить' }
    }
    if (missing.length === 1) {
      return {
        to: `/series/${seriesId}/team?league=${encodeURIComponent(missing[0].code)}`,
        label: 'Собрать',
      }
    }
    return { to: `/series/${seriesId}`, label: 'Открыть' }
  }

  return (
    <div className="pf-page pf-page--home">
      <h1 className="pf-home-title">Турниры</h1>
      <p className="pf-home-sub">Выберите событие, чтобы собрать команду и следить за очками.</p>

      {openSeriesQ.isError ? (
        <p className="pf-err pf-home-open-series-err">{(openSeriesQ.error as Error).message}</p>
      ) : openSeriesError ? (
        <p className="pf-err pf-home-open-series-err">{openSeriesError.message}</p>
      ) : openSeries.length > 0 ? (
        <section className="pf-home-open-series" aria-labelledby="home-open-series-heading">
          <h2 id="home-open-series-heading" className="pf-section-title">
            Состав на серию
          </h2>
          <p className="pf-instruction pf-home-open-series-hint">
            Активные серии с открытым дедлайном: можно подать новый состав или изменить текущий
          </p>
          <ul className="pf-day-list">
            {openSeries.map((s, idx) => {
              const deadline = new Date(s.teamDeadline)
              const seriesNum = s.gameNumFrom ?? idx + 1
              const cta = openSeriesCta(s.seriesId)
              return (
                <li key={s.seriesId}>
                  <div className="pf-day-card">
                    <div className="pf-day-card__badge">
                      <span className="pf-day-card__badge-label">Серия</span>
                      <span className="pf-day-card__badge-num">{seriesNum}</span>
                    </div>
                    <div className="pf-day-card__body">
                      <p className="pf-home-open-series-tournament">{s.tournamentName}</p>
                      <p className="pf-day-card__deadline">Доступно до: {formatDateShortWithTime(deadline)}</p>
                      <p className="pf-day-card__name">{s.seriesName}</p>
                      <p className="pf-home-leagues-status">{openSeriesStatus(s.seriesId)}</p>
                    </div>
                    <div className="pf-day-card__action">
                      <Link className="pf-btn pf-btn--small pf-btn--ghost" to={cta.to} state={{ fromHome: true }}>
                        {cta.label}
                      </Link>
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        </section>
      ) : null}

      {list.length === 0 ? (
        <p className="pf-muted">Нет активных турниров.</p>
      ) : (
        <ul className="pf-tournament-grid">
          {list.map((t) => (
            <li key={t.id}>
              <Link to={`/tournaments/${t.id}`} className="pf-tournament-card">
                <div className="pf-tournament-card__head">
                  <span className="pf-tournament-card__title">{t.name}</span>
                  <TournamentStatusBadge status={t.status} />
                </div>
                {t.description && <p className="pf-tournament-card__desc">{t.description}</p>}
                <span className="pf-tournament-card__cta">Открыть →</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
