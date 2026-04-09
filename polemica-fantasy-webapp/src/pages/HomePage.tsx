import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { SeriesOpenForTeam, UserTournament } from '../api/types'
import { TournamentStatusBadge } from '../components/StatusBadge'
import { useInitData } from '../context/useInitData'
import { formatDateShort } from '../lib/tournamentDates'

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

  if (!initData) {
    return (
      <div className="pf-card pf-card--notice">
        <p>Нет Telegram initData. Откройте приложение в Telegram или задайте переменную окружения VITE_DEV_INIT_DATA для локальной разработки.</p>
      </div>
    )
  }

  if (q.isLoading || openSeriesQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>

  const list = q.data ?? []
  const openSeries = openSeriesQ.data ?? []

  return (
    <div className="pf-page pf-page--home">
      <h1 className="pf-home-title">Турниры</h1>
      <p className="pf-home-sub">Выберите событие, чтобы собрать команду и следить за очками.</p>

      {openSeriesQ.isError ? (
        <p className="pf-err pf-home-open-series-err">{(openSeriesQ.error as Error).message}</p>
      ) : openSeries.length > 0 ? (
        <section className="pf-home-open-series" aria-labelledby="home-open-series-heading">
          <h2 id="home-open-series-heading" className="pf-section-title">
            Состав на серию
          </h2>
          <p className="pf-instruction pf-home-open-series-hint">Серии, для которых ещё можно выставить команду</p>
          <ul className="pf-day-list">
            {openSeries.map((s, idx) => {
              const deadline = new Date(s.teamDeadline)
              const seriesNum = s.gameNumFrom ?? idx + 1
              return (
                <li key={s.seriesId}>
                  <div className="pf-day-card">
                    <div className="pf-day-card__badge">
                      <span className="pf-day-card__badge-label">Серия</span>
                      <span className="pf-day-card__badge-num">{seriesNum}</span>
                    </div>
                    <div className="pf-day-card__body">
                      <p className="pf-home-open-series-tournament">{s.tournamentName}</p>
                      <p className="pf-day-card__deadline">Доступно до: {formatDateShort(deadline)}</p>
                      <p className="pf-day-card__name">{s.seriesName}</p>
                    </div>
                    <div className="pf-day-card__action">
                      <Link className="pf-btn pf-btn--small pf-btn--ghost" to={`/series/${s.seriesId}/team`}>
                        Далее
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
