import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { LeaderboardEntry, UserSeriesDetail } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { useInitData } from '../context/useInitData'
import { formatUserDisplayName } from '../lib/userDisplayName'

export function LeaderboardPage() {
  const { seriesId } = useParams<{ seriesId: string }>()
  const id = Number(seriesId)
  const initData = useInitData()

  const seriesMeta = useQuery({
    queryKey: ['series', id, initData],
    queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${id}`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  const q = useQuery({
    queryKey: ['leaderboard', id, initData],
    queryFn: () => apiGet<LeaderboardEntry[]>(`/api/v1/series/${id}/leaderboard`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  if (!initData) return <MissingInitDataNotice />
  if (q.isLoading || seriesMeta.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>

  const rows = q.data ?? []
  const s = seriesMeta.data
  const back = s ? `/tournaments/${s.tournamentId}` : '/'

  return (
    <div className="pf-page">
      <PageHeader title="Лидерборд" subtitle={s?.name} backTo={back} />

      <ul className="pf-lb-list">
        {rows.map((r) => (
          <li key={r.rank + '-' + r.user.telegramId}>
            <Link
              to={`/series/${id}/leaderboard/player/${r.user.telegramId}`}
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
        <Link to={`/series/${id}`}>← К серии</Link>
      </p>
    </div>
  )
}
