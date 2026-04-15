import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { UserTournamentDetail } from '../api/types'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { formatDateShortWithTime } from '../lib/tournamentDates'
import { useNow } from '../lib/useNow'

export function SeriesPickerPage() {
  const { tournamentId } = useParams<{ tournamentId: string }>()
  const id = Number(tournamentId)
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['tournament', id, initData],
    queryFn: () => apiGet<UserTournamentDetail>(`/api/v1/tournaments/${id}`, initData),
    enabled: !!initData && Number.isFinite(id),
  })
  const now = useNow()

  const seriesChronologicalIndex = useMemo(() => {
    const series = q.data?.series
    if (!series) return new Map<number, number>()
    const sorted = [...series].sort((a, b) => a.id - b.id)
    return new Map(sorted.map((s, i) => [s.id, i + 1]))
  }, [q.data?.series])

  if (!initData) return <MissingInitDataNotice />
  if (!Number.isFinite(id)) {
    return <p className="pf-err">Некорректная ссылка на турнир.</p>
  }
  if (q.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>
  if (q.data == null) return <p className="pf-loading">Загрузка…</p>

  const t = q.data
  const back = `/tournaments/${t.id}`

  return (
    <div className="pf-page">
      <PageHeader title="Фэнтези-команда" subtitle={t.name} backTo={back} />
      <p className="pf-instruction">Выберите серию</p>
      <ul className="pf-day-list">
        {t.series.map((s, idx) => {
          const deadline = new Date(s.teamDeadline)
          const expired = now > deadline.getTime()
          const seriesNum = s.gameNumFrom ?? seriesChronologicalIndex.get(s.id) ?? idx + 1
          return (
            <li key={s.id}>
              <div className={`pf-day-card ${expired ? 'pf-day-card--expired' : ''}`}>
                <div className="pf-day-card__badge">
                  <span className="pf-day-card__badge-label">Серия</span>
                  <span className="pf-day-card__badge-num">{seriesNum}</span>
                </div>
                <div className="pf-day-card__body">
                  <p className="pf-day-card__deadline">Доступно до: {formatDateShortWithTime(deadline)}</p>
                  <p className="pf-day-card__name">{s.name}</p>
                </div>
                <div className="pf-day-card__action">
                  {expired ? (
                    <span className="pf-day-card__status pf-day-card__status--muted">Время вышло</span>
                  ) : (
                    <Link className="pf-btn pf-btn--small pf-btn--ghost" to={`/series/${s.id}/team`}>
                      Далее
                    </Link>
                  )}
                </div>
              </div>
            </li>
          )
        })}
      </ul>
      {t.series.length === 0 && <p className="pf-muted">Нет серий в этом турнире.</p>}
    </div>
  )
}
