import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { UserTournament } from '../api/types'
import { useInitData } from '../context/InitDataContext'

export function HomePage() {
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['tournaments', initData],
    queryFn: () => apiGet<UserTournament[]>('/api/v1/tournaments', initData),
    enabled: !!initData,
  })

  if (!initData) {
    return (
      <div className="card">
        <p>Нет Telegram initData. Откройте приложение в Telegram или задайте переменную окружения VITE_DEV_INIT_DATA для локальной разработки.</p>
      </div>
    )
  }

  if (q.isLoading) return <p>Загрузка…</p>
  if (q.isError) return <p className="err">{(q.error as Error).message}</p>

  const list = q.data ?? []
  if (list.length === 0) return <p>Нет активных турниров.</p>

  return (
    <div>
      <h1>Турниры</h1>
      <ul className="list">
        {list.map((t) => (
          <li key={t.id}>
            <Link to={`/tournaments/${t.id}`}>{t.name}</Link>
            <span className="muted"> — {t.status}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
