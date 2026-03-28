import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { UserSeriesDetail } from '../api/types'
import { useInitData } from '../context/InitDataContext'

export function SeriesPage() {
  const { seriesId } = useParams<{ seriesId: string }>()
  const id = Number(seriesId)
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['series', id, initData],
    queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${id}`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  if (!initData) return <p className="muted">Нужен initData.</p>
  if (q.isLoading) return <p>Загрузка…</p>
  if (q.isError) return <p className="err">{(q.error as Error).message}</p>
  const s = q.data!
  return (
    <div>
      <h1>{s.name}</h1>
      <p className="muted">
        Статус: {s.status} · Дедлайн команды: {new Date(s.teamDeadline).toLocaleString()}
      </p>
      <p>
        <Link to={`/series/${s.id}/team`}>Собрать команду</Link>
        {' · '}
        <Link to={`/series/${s.id}/leaderboard`}>Лидерборд</Link>
      </p>
      <h2>Игроки серии</h2>
      <ul className="list">
        {s.players.map((p) => (
          <li key={p.tournamentPlayerId}>
            {p.nickname}
            {p.photoUrl && (
              <img src={p.photoUrl} alt="" width={32} height={32} style={{ verticalAlign: 'middle', marginLeft: 8 }} />
            )}
          </li>
        ))}
      </ul>
      <h2>Игры</h2>
      <ul className="list">
        {s.games.map((g) => (
          <li key={g.polemicaGameId}>
            {g.gameName} {g.scored ? '✓' : '…'}
          </li>
        ))}
      </ul>
    </div>
  )
}
