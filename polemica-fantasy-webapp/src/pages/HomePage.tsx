import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { UserTournament } from '../api/types'
import { TournamentStatusBadge } from '../components/StatusBadge'
import { useInitData } from '../context/useInitData'

export function HomePage() {
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['tournaments', initData],
    queryFn: () => apiGet<UserTournament[]>('/api/v1/tournaments', initData),
    enabled: !!initData,
  })

  if (!initData) {
    return (
      <div className="pf-card pf-card--notice">
        <p>Нет Telegram initData. Откройте приложение в Telegram или задайте переменную окружения VITE_DEV_INIT_DATA для локальной разработки.</p>
      </div>
    )
  }

  if (q.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>

  const list = q.data ?? []
  if (list.length === 0) return <p className="pf-muted">Нет активных турниров.</p>

  return (
    <div className="pf-page pf-page--home">
      <h1 className="pf-home-title">Турниры</h1>
      <p className="pf-home-sub">Выберите событие, чтобы собрать команду и следить за очками.</p>
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
    </div>
  )
}
