import { apiJson } from './client'

export type PeriodicRatingStatus = 'DRAFT' | 'OPEN' | 'SETTLING' | 'FINALIZED' | 'CANCELLED'

export interface PeriodicRatingPeriod {
  id: number; code: string; title: string; startsAt: string; endsAt: string
  timezone: 'Europe/Moscow'; league: 'MAIN'; status: PeriodicRatingStatus
  provisional: boolean; seriesCount: number; participantCount: number; finalizedAt: string | null
}
export interface PeriodicRatingUser { telegramId: number; username: string | null; firstName: string | null; displayName: string | null; profileFrameCode: string | null }
export interface PeriodicRatingEntry { user: PeriodicRatingUser; rank: number; totalScore: number; seriesCount: number; averageScore: number; bestSeriesScore: number }
export interface PeriodicRatingSeriesPreview { seriesId: number; seriesName: string; tournamentId: number; tournamentName: string; effectiveAt: string | null; included: boolean; reason: string | null; finalized: boolean; blocker: boolean }
export interface PeriodicRatingPreview { period: PeriodicRatingPeriod; sourceChecksum: string; series: PeriodicRatingSeriesPreview[]; blockers: PeriodicRatingSeriesPreview[]; leaderboard: PeriodicRatingEntry[]; rewardLiability: Record<string, number> }

export type PeriodicRatingRewardStatus =
  | 'AVAILABLE'
  | 'DRAFT'
  | 'REVIEW_REQUIRED'
  | 'CHANGES_REQUESTED'
  | 'FULFILLED'
  | 'OVERDUE'
  | 'CANCELLED'

export interface PeriodicRatingReward {
  id: number
  periodId: number
  periodCode: string
  periodTitle: string
  user: PeriodicRatingUser
  rank: number
  status: PeriodicRatingRewardStatus
  serial: string
  policy: Record<string, unknown>
  selection: Record<string, unknown>
  selectedPlayer: { id: number; polemicaUserId: number; nickname: string; photoUrl: string | null } | null
  version: number
  claimDeadline: string | null
  changesRequestedReason: string | null
  fantikiAmount: number
  fantikiGrantedAt: string | null
  issuedCardTemplateId: number | null
  issuedUserCardId: number | null
  issuedAt: string | null
  createdAt: string
  updatedAt: string
}

export const listPeriodicRatingPeriods = () => apiJson<PeriodicRatingPeriod[]>('/v1/admin/periodic-ratings/periods')
export const createPeriodicRatingPeriod = (body: { code: string; title: string; startsAt: string; endsAt: string }) => apiJson<PeriodicRatingPeriod>('/v1/admin/periodic-ratings/periods', { method: 'POST', body: JSON.stringify(body) })
export const openPeriodicRatingPeriod = (id: number) => apiJson<PeriodicRatingPeriod>(`/v1/admin/periodic-ratings/periods/${id}/open`, { method: 'POST', body: '{}' })
export const previewPeriodicRatingPeriod = (id: number) => apiJson<PeriodicRatingPreview>(`/v1/admin/periodic-ratings/periods/${id}/preview`, { method: 'POST', body: '{}' })
export const updatePeriodicRatingSeries = (periodId: number, seriesId: number, body: { included: boolean; reason?: string | null }) => apiJson<PeriodicRatingPreview>(`/v1/admin/periodic-ratings/periods/${periodId}/series/${seriesId}`, { method: 'PUT', body: JSON.stringify(body) })
export const finalizePeriodicRatingPeriod = (id: number, body: { sourceChecksum: string; reason?: string }) => apiJson<PeriodicRatingPreview>(`/v1/admin/periodic-ratings/periods/${id}/finalize`, { method: 'POST', body: JSON.stringify(body) })
export const listPeriodicRatingRewards = (filters: { periodId?: number; status?: string }) => {
  const params = new URLSearchParams()
  if (filters.periodId != null) params.set('periodId', String(filters.periodId))
  if (filters.status) params.set('status', filters.status)
  const query = params.size ? `?${params.toString()}` : ''
  return apiJson<PeriodicRatingReward[]>(`/v1/admin/periodic-ratings/rewards${query}`)
}
export const requestPeriodicRatingRewardChanges = (id: number, body: { reason: string; version: number }) => apiJson<PeriodicRatingReward>(`/v1/admin/periodic-ratings/rewards/${id}/request-changes`, { method: 'POST', body: JSON.stringify(body) })
export const approveAndIssuePeriodicRatingReward = (id: number, body: { version: number }) => apiJson<PeriodicRatingReward>(`/v1/admin/periodic-ratings/rewards/${id}/approve-and-issue`, { method: 'POST', body: JSON.stringify(body) })
