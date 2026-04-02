import { useQuery } from '@tanstack/react-query'
import { apiGet } from '../api/client'
import type { UserProfile } from '../api/types'
import { useInitData } from '../context/useInitData'
import { formatUserDisplayName } from '../lib/userDisplayName'

export function TopBarDisplayName() {
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['me', initData],
    queryFn: () => apiGet<UserProfile>('/api/v1/me', initData),
    enabled: !!initData,
  })

  if (!initData) return null

  let label: string
  if (q.isLoading) {
    label = '…'
  } else if (q.isError || !q.data) {
    label = '—'
  } else {
    label = formatUserDisplayName(q.data)
  }

  return (
    <span className="top__user" title={label === '…' || label === '—' ? undefined : label}>
      {label}
    </span>
  )
}
