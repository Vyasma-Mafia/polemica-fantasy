import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { SeriesStatus, UserTournamentDetail } from '../api/types'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { TournamentStatusBadge } from '../components/StatusBadge'
import { useInitData } from '../context/useInitData'
import { leagueShortName } from '../lib/leagues'
import { formatDateShort, tournamentSeriesDateRange } from '../lib/tournamentDates'

export function TournamentPage() {
  const { tournamentId } = useParams<{ tournamentId: string }>()
  const id = Number(tournamentId)
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['tournament', id, initData],
    queryFn: () => apiGet<UserTournamentDetail>(`/api/v1/tournaments/${id}`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  if (!initData) return <MissingInitDataNotice />
  if (q.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>

  const t = q.data!
  const range = tournamentSeriesDateRange(t.series)
  const tid = String(t.id)

  return (
    <div className="pf-page">
      <PageHeader title={t.name} subtitle={t.description ?? undefined} backTo="/" backLabel="Турниры" />
      <div className="pf-hero-card">
        <div className="pf-hero-card__top">
          <span className="pf-hero-card__label">{t.name}</span>
          <TournamentStatusBadge status={t.status} />
        </div>
        {range && (
          <p className="pf-hero-card__meta">
            <span className="pf-hero-card__icon" aria-hidden>
              📅
            </span>
            {formatDateShort(range.from)} — {formatDateShort(range.to)}
          </p>
        )}
        {t.series.length > 0 && (
          <p className="pf-hero-card__meta">
            <span className="pf-hero-card__icon" aria-hidden>
              🎴
            </span>
            {t.series.length} {t.series.length === 1 ? 'серия' : 'серий'}
          </p>
        )}
      </div>

      <section className="pf-actions">
        <h2 className="pf-section-title">Действия</h2>
        <div className="pf-action-grid">
          <Link className="pf-btn pf-btn--primary pf-action-tile" to={`/tournaments/${tid}/series`}>
            <span className="pf-action-tile__icon" aria-hidden>
              📋
            </span>
            Собрать фэнтези-команду
          </Link>
          <Link className="pf-btn pf-btn--primary pf-action-tile" to={`/tournaments/${tid}/leaderboard`}>
            <span className="pf-action-tile__icon" aria-hidden>
              🏆
            </span>
            Лидерборд
          </Link>
        </div>
        <div className="pf-action-list">
          <Link className="pf-btn pf-btn--outline pf-action-row" to={`/cards?tournamentId=${tid}`}>
            <span aria-hidden>🃏</span> Моя коллекция
          </Link>
          <Link className="pf-btn pf-btn--outline pf-action-row" to={`/tournaments/${tid}/participants`}>
            <span aria-hidden>👥</span> Участники
          </Link>
          <Link className="pf-btn pf-btn--outline pf-action-row" to={`/tournaments/${tid}/rules`}>
            <span aria-hidden>📖</span> Правила фэнтези
          </Link>
          <Link className="pf-btn pf-btn--outline pf-action-row" to={`/tournaments/${tid}/history`}>
            <span aria-hidden>📜</span> История фэнтези
          </Link>
        </div>
      </section>

      <section className="pf-section">
        <h2 className="pf-section-title">Серии</h2>
        <ul className="pf-link-list">
          {t.series.map((s) => (
            <li key={s.id}>
              <Link to={`/series/${s.id}`} className="pf-link-row">
                <span className="pf-link-row__main">
                  <span>{s.name}</span>
                  {s.leagues.length > 0 && (
                    <span className="pf-link-row__sub">
                      {s.leagues
                        .map((league) => `${leagueShortName(league.code, league.name)} ${league.hasTeam ? '✓' : '✗'}`)
                        .join(' / ')}
                    </span>
                  )}
                </span>
                <SeriesStatusInline status={s.status} />
              </Link>
            </li>
          ))}
        </ul>
        {t.series.length === 0 && <p className="pf-muted">Серий пока нет.</p>}
      </section>
    </div>
  )
}

function SeriesStatusInline({ status }: { status: SeriesStatus }) {
  const labels: Record<string, string> = {
    UPCOMING: 'Скоро',
    ACTIVE: 'Идёт',
    SCORING: 'Подсчёт',
    FINISHED: 'Готово',
  }
  return <span className="pf-muted pf-link-row__badge">{labels[status] ?? status}</span>
}
