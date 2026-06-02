import type { Rarity } from '../api/types'

export type NotificationCategory =
  | 'ADMIN_BROADCAST'
  | 'SERIES_START'
  | 'TEAM_DEADLINE_REMINDER'
  | 'SERIES_FINALIZED'
  | 'SERIES_ROSTER_CHANGE'
  | 'MARKETPLACE_SALE'
  | 'MARKETPLACE_WATCH'
  | 'ONBOARDING_TIPS'
  | 'MARKETPLACE_COMPLAINT_RESOLVED'
  | 'MARKETPLACE_SANCTION_APPLIED'
  | 'PAIR_BAN'

export interface NotificationCategoryDto {
  category: NotificationCategory | string
  enabled: boolean
  toggleable: boolean
  description: string
}

export interface NotificationSettingsResponse {
  categories: NotificationCategoryDto[]
}

export interface UpdateNotificationSettingsRequest {
  categories: Record<string, boolean>
}

export interface TournamentSubscriptionEntry {
  tournamentId: number
  tournamentName: string
  subscribed: boolean
}

export interface TournamentSubscriptionsResponse {
  subscriptions: TournamentSubscriptionEntry[]
  availableTournaments: TournamentSubscriptionEntry[]
}

export interface UpdateTournamentSubscriptionsRequest {
  tournamentIds: number[]
}

export interface TournamentBriefDto {
  id: number
  name: string
}

export interface MarketplaceWatchDto {
  id: number
  fantasyPlayer: { id: number; nickname: string; photoUrl: string | null } | null
  tournament: TournamentBriefDto | null
  rarity: Rarity | null
  maxPrice: number | null
  minTimesRenewed: number | null
  maxTimesRenewed: number | null
  perks: { id: string; name: string }[]
  createdAt: string
}

export interface MarketplaceWatchesResponse {
  watches: MarketplaceWatchDto[]
  maxWatches: number
}

export interface CreateMarketplaceWatchRequest {
  fantasyPlayerId: number | null
  tournamentId: number | null
  rarity: Rarity | null
  maxPrice: number | null
  minTimesRenewed?: number | null
  maxTimesRenewed?: number | null
  perkIds: string[]
}
