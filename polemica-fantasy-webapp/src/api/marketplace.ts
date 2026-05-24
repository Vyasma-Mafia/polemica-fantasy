import { apiGet, apiSend } from './client'
import type {
  BuyCardResult,
  CardOwnershipHistoryEntry,
  ComplainResult,
  MarketplaceAnalyticsDetail,
  MarketplaceAnalyticsSummary,
  MarketplaceFeed,
  MarketplaceListingsPage,
  MarketplaceListingEntry,
  MarketplaceTransactionDetail,
  MarketplaceSortBy,
  Rarity,
} from './types'

export interface ListingsQueryParams {
  fantasyPlayerId?: number
  tournamentId?: number
  seriesId?: number
  rarity?: Rarity
  minPrice?: number
  maxPrice?: number
  achievementIds?: string[]
  sortBy?: MarketplaceSortBy
  page?: number
  size?: number
}

function listingsSearchParams(p: ListingsQueryParams): string {
  const sp = new URLSearchParams()
  if (p.fantasyPlayerId != null) sp.set('fantasyPlayerId', String(p.fantasyPlayerId))
  if (p.tournamentId != null) sp.set('tournamentId', String(p.tournamentId))
  if (p.seriesId != null) sp.set('seriesId', String(p.seriesId))
  if (p.rarity) sp.set('rarity', p.rarity)
  if (p.minPrice != null) sp.set('minPrice', String(p.minPrice))
  if (p.maxPrice != null) sp.set('maxPrice', String(p.maxPrice))
  for (const achievementId of p.achievementIds ?? []) {
    sp.append('achievementIds', achievementId)
  }
  if (p.sortBy) sp.set('sortBy', p.sortBy)
  sp.set('page', String(p.page ?? 0))
  sp.set('size', String(p.size ?? 20))
  const q = sp.toString()
  return q ? `?${q}` : ''
}

export function fetchMarketplaceListings(initData: string | undefined, params: ListingsQueryParams) {
  return apiGet<MarketplaceListingsPage>(
    `/api/v1/marketplace/listings${listingsSearchParams(params)}`,
    initData,
  )
}

export function fetchMarketplaceFeed(initData: string | undefined, limit = 20) {
  const sp = new URLSearchParams()
  sp.set('limit', String(limit))
  return apiGet<MarketplaceFeed>(`/api/v1/marketplace/feed?${sp.toString()}`, initData)
}

export function fetchMyMarketplaceListings(initData: string | undefined) {
  return apiGet<MarketplaceListingEntry[]>('/api/v1/marketplace/my-listings', initData)
}

export function fetchMarketplaceTransactionDetail(initData: string | undefined, listingId: number) {
  return apiGet<MarketplaceTransactionDetail>(`/api/v1/marketplace/transactions/${listingId}`, initData)
}

export function complainMarketplaceTransaction(initData: string | undefined, listingId: number) {
  return apiSend<ComplainResult>('POST', `/api/v1/marketplace/transactions/${listingId}/complain`, initData)
}

export function createMarketplaceListing(
  initData: string | undefined,
  body: { userCardId: number; price: number },
) {
  return apiSend<MarketplaceListingEntry>('POST', '/api/v1/marketplace/listings', initData, body)
}

export function updateMarketplaceListingPrice(
  initData: string | undefined,
  listingId: number,
  body: { price: number },
) {
  return apiSend<MarketplaceListingEntry>(
    'PATCH',
    `/api/v1/marketplace/listings/${listingId}`,
    initData,
    body,
  )
}

export function cancelMarketplaceListing(initData: string | undefined, listingId: number) {
  return apiSend<void>('DELETE', `/api/v1/marketplace/listings/${listingId}`, initData)
}

export function buyMarketplaceListing(initData: string | undefined, listingId: number) {
  return apiSend<BuyCardResult>('POST', `/api/v1/marketplace/listings/${listingId}/buy`, initData)
}

export function fetchCardOwnershipHistory(initData: string | undefined, userCardId: number) {
  return apiGet<CardOwnershipHistoryEntry[]>(
    `/api/v1/user-cards/${userCardId}/ownership-history`,
    initData,
  )
}

export function fetchMarketplaceAnalyticsSummary(
  initData: string | undefined,
  fantasyPlayerIds: number[],
) {
  const sp = new URLSearchParams()
  sp.set('fantasyPlayerIds', fantasyPlayerIds.join(','))
  return apiGet<MarketplaceAnalyticsSummary>(
    `/api/v1/marketplace/analytics/summary?${sp.toString()}`,
    initData,
  )
}

export function fetchMarketplaceAnalyticsDetail(
  initData: string | undefined,
  fantasyPlayerId: number,
  rarity: Rarity,
) {
  const sp = new URLSearchParams()
  sp.set('fantasyPlayerId', String(fantasyPlayerId))
  sp.set('rarity', rarity)
  return apiGet<MarketplaceAnalyticsDetail>(
    `/api/v1/marketplace/analytics/detail?${sp.toString()}`,
    initData,
  )
}
