import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { UserTournamentDetail } from '../api/types'
import { useInitData } from '../context/InitDataContext'

export function TournamentPage() {
  const { tournamentId } = useParams<{ tournamentId: string }>()
  const id = Number(tournamentId)
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['tournament', id, initData],
    queryFn: () => apiGet<UserTournamentDetail>(`/api/v1/tournaments/${id}`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  if (!initData) return <p className="muted">Нужен initData (Telegram или VITE_DEV_INIT_DATA).</p>
  if (q.isLoading) return <p>Загрузка…</p>
  if (q.isError) return <p className="err">{(q.error as Error).message}</p>
  const t = q.data!
  return (
    <div>
      <h1>{t.name}</h1>
      {t.description && <p className="desc">{t.description}</p>}
      <h2>Серии</h2>
      <ul className="list">
        {t.series.map((s) => (
          <li key={s.id}>
            <Link to={`/series/${s.id}`}>{s.name}</Link>
            <span className="muted"> — {s.status}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
