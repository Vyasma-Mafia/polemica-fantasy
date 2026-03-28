import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { LeaderboardEntry } from '../api/types'
import { useInitData } from '../context/InitDataContext'

export function LeaderboardPage() {
  const { seriesId } = useParams<{ seriesId: string }>()
  const id = Number(seriesId)
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['leaderboard', id, initData],
    queryFn: () => apiGet<LeaderboardEntry[]>(`/api/v1/series/${id}/leaderboard`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  if (!initData) return <p className="muted">Нужен initData.</p>
  if (q.isLoading) return <p>Загрузка…</p>
  if (q.isError) return <p className="err">{(q.error as Error).message}</p>

  const rows = q.data ?? []
  return (
    <div>
      <p>
        <Link to={`/series/${id}`}>← Серия</Link>
      </p>
      <h1>Лидерборд</h1>
      <table className="table">
        <thead>
          <tr>
            <th>#</th>
            <th>Игрок</th>
            <th>Очки</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.rank}>
              <td>{r.rank}</td>
              <td>{r.user.firstName ?? r.user.username ?? r.user.telegramId}</td>
              <td>{r.totalScore ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length === 0 && <p>Пока нет команд.</p>}
    </div>
  )
}
