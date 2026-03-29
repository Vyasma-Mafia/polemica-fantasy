import { useQuery } from '@tanstack/react-query'
import { apiGet } from '../api/client'
import type { UserProfile } from '../api/types'
import { useInitData } from '../context/InitDataContext'

function formatFantiki(n: number): string {
  return n.toLocaleString('ru-RU')
}

export function FantikiBalance() {
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['me', initData],
    queryFn: () => apiGet<UserProfile>('/api/v1/me', initData),
    enabled: !!initData,
  })

  if (!initData) return null

  if (q.isLoading) {
    return (
      <span className="pf-fantiki pf-fantiki--muted" aria-label="Баланс">
        …
      </span>
    )
  }

  if (q.isError || !q.data) {
    return (
      <span className="pf-fantiki pf-fantiki--muted" title="Не удалось загрузить баланс">
        —
      </span>
    )
  }

  return (
    <span className="pf-fantiki" title="Фантики">
      <span className="pf-fantiki__icon" aria-hidden>
        🪙
      </span>
      <span className="pf-fantiki__value">{formatFantiki(q.data.fantiki)}</span>
    </span>
  )
}
