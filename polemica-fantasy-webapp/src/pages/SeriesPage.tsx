import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { UserSeriesDetail } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { SeriesStatusBadge } from '../components/StatusBadge'
import { useInitData } from '../context/useInitData'
import { formatDateShort } from '../lib/tournamentDates'

export function SeriesPage() {
  const { seriesId } = useParams<{ seriesId: string }>()
  const id = Number(seriesId)
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['series', id, initData],
    queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${id}`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  if (!initData) return <p className="pf-muted">Нужен initData.</p>
  if (q.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>
  const s = q.data!
  const back = `/tournaments/${s.tournamentId}`

  return (
    <div className="pf-page">
      <PageHeader title={s.name} backTo={back} />
      <div className="pf-hero-card pf-hero-card--compact">
        <div className="pf-hero-card__top">
          <SeriesStatusBadge status={s.status} />
        </div>
        <p className="pf-hero-card__meta">
          Дедлайн команды: {formatDateShort(new Date(s.teamDeadline))}
        </p>
      </div>
      <div className="pf-action-grid pf-action-grid--single">
        <Link className="pf-btn pf-btn--primary pf-action-tile" to={`/series/${s.id}/team`}>
          Собрать команду
        </Link>
        <Link className="pf-btn pf-btn--outline pf-action-tile" to={`/series/${s.id}/leaderboard`}>
          Лидерборд серии
        </Link>
      </div>

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
