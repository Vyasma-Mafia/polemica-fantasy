import { apiGet, apiSend } from './client'

export type PeriodicRatingStatus = 'OPEN' | 'SETTLING' | 'FINALIZED'

export interface PeriodicRatingPeriod {
  id: number
  code: string
  title: string
  startsAt: string
  endsAt: string
  timezone: 'Europe/Moscow'
  league: 'MAIN'
  status: PeriodicRatingStatus
  provisional: boolean
  seriesCount: number
  participantCount: number
  finalizedAt: string | null
}

export interface PeriodicRatingUser {
  telegramId: number
  username: string | null
  firstName: string | null
  displayName: string | null
  profileFrameCode: string | null
}

export interface PeriodicRatingEntry {
  user: PeriodicRatingUser
  rank: number
  totalScore: number
  seriesCount: number
  averageScore: number
  bestSeriesScore: number
}

export interface PeriodicRatingContribution {
  seriesId: number
  seriesName: string
  tournamentId: number
  tournamentName: string
  score: number
  seriesRank: number
  participantsCount: number
}

export interface PeriodicRatingLeaderboard {
  period: PeriodicRatingPeriod
  provisional: boolean
  entries: PeriodicRatingEntry[]
  currentUser: PeriodicRatingEntry | null
  blockersCount: number
}

export interface PeriodicRatingMe {
  period: PeriodicRatingPeriod
  entry: PeriodicRatingEntry | null
  contributions: PeriodicRatingContribution[]
}

export const fetchCurrentPeriodicRating = (initData: string) =>
  apiGet<PeriodicRatingPeriod>('/api/v1/periodic-ratings/current', initData)

export const fetchPeriodicRatingPeriods = (initData: string) =>
  apiGet<PeriodicRatingPeriod[]>('/api/v1/periodic-ratings/periods', initData)

export const fetchPeriodicRatingLeaderboard = (periodId: number, initData: string) =>
  apiGet<PeriodicRatingLeaderboard>(`/api/v1/periodic-ratings/periods/${periodId}/leaderboard`, initData)

export const fetchMyPeriodicRating = (periodId: number, initData: string) =>
  apiGet<PeriodicRatingMe>(`/api/v1/periodic-ratings/periods/${periodId}/me`, initData)

export type PeriodicRatingRewardStatus =
  | 'AVAILABLE'
  | 'DRAFT'
  | 'CHANGES_REQUESTED'
  | 'REVIEW_REQUIRED'
  | 'FULFILLED'
  | 'OVERDUE'
  | 'CANCELLED'

export type PeriodicRatingRewardPolicy = {
  policyVersion?: number
  rarity: 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY'
  editionTier: string
  skinCodes: string[]
  playerSelectionMode: 'FREE_CHOICE' | 'BUNDLED_OPTIONS'
  perkSelectionMode: 'FREE_CHOICE' | 'FIXED_PERK_OPTIONS' | 'BUNDLED_OPTIONS' | 'NONE'
  perkSelectionCount: number
  perkPool?: string[]
  bundles?: { playerId: number; perkIds: string[] }[]
  fulfillmentMode: string
}

export type PeriodicRatingRewardSelection = {
  playerId?: number
  perkIds?: string[]
  skinCode?: string
}

export interface PeriodicRatingReward {
  id: number
  periodId: number
  periodCode: string
  periodTitle: string
  user: PeriodicRatingUser
  rank: number
  status: PeriodicRatingRewardStatus
  serial: string
  policy: PeriodicRatingRewardPolicy
  selection: PeriodicRatingRewardSelection
  selectedPlayer: PeriodicRatingRewardPlayer | null
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

export interface PeriodicRatingRewardPlayer {
  id: number
  polemicaUserId: number
  nickname: string
  photoUrl: string | null
}

export interface PeriodicRatingRewardPlayerPage {
  content: PeriodicRatingRewardPlayer[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const fetchPeriodicRatingRewards = (initData: string) =>
  apiGet<PeriodicRatingReward[]>('/api/v1/periodic-ratings/rewards', initData)

export const fetchPeriodicRatingReward = (rewardId: number, initData: string) =>
  apiGet<PeriodicRatingReward>(`/api/v1/periodic-ratings/rewards/${rewardId}`, initData)

export const fetchPeriodicRatingRewardPlayers = (
  rewardId: number,
  initData: string,
  query: string,
  page: number,
  size = 20,
) => {
  const params = new URLSearchParams({ q: query, page: String(page), size: String(size) })
  return apiGet<PeriodicRatingRewardPlayerPage>(
    `/api/v1/periodic-ratings/rewards/${rewardId}/players?${params}`,
    initData,
  )
}

export const savePeriodicRatingRewardDraft = (
  rewardId: number,
  initData: string,
  draft: { playerId: number; perkIds: string[]; skinCode: string; version: number },
) => apiSend<PeriodicRatingReward>('PUT', `/api/v1/periodic-ratings/rewards/${rewardId}/draft`, initData, draft)

export const submitPeriodicRatingReward = (rewardId: number, initData: string, version: number) =>
  apiSend<PeriodicRatingReward>('POST', `/api/v1/periodic-ratings/rewards/${rewardId}/submit`, initData, { version })
