import { useQuery } from '@tanstack/react-query'
import { fetchCardOwnershipHistory } from '../api/marketplace'
import { useInitData } from '../context/useInitData'

export function CardOwnershipHistoryBlock({ userCardId }: { userCardId: number }) {
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['ownership-history', userCardId, initData],
    queryFn: () => fetchCardOwnershipHistory(initData, userCardId),
    enabled: !!initData && userCardId > 0,
  })

  if (!initData) return null
  if (q.isLoading) return <p className="pf-muted">История карточки…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>
  const items = q.data ?? []
  if (items.length === 0) return null

  return (
    <div className="pf-card-ownership">
      <h4 className="pf-card-ownership__title">История карточки</h4>
      <ol className="pf-card-ownership__list">
        {items.map((row, i) => (
          <li key={`${row.acquiredAt}-${i}`}>
            <strong>{row.ownerDisplayName}</strong>
            <span className="pf-muted"> — {row.acquisitionLabel}</span>
            <span className="pf-muted"> ({new Date(row.acquiredAt).toLocaleDateString()})</span>
          </li>
        ))}
      </ol>
    </div>
  )
}
