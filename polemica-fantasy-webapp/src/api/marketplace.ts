import { apiGet, apiSend } from './client'
import type {
  BuyCardResult,
  CardOwnershipHistoryEntry,
  MarketplaceFeed,
  MarketplaceListingsPage,
  MarketplaceListingEntry,
  MarketplaceSortBy,
  Rarity,
} from './types'

export interface ListingsQueryParams {
  fantasyPlayerId?: number
  rarity?: Rarity
  minPrice?: number
  maxPrice?: number
  sortBy?: MarketplaceSortBy
  page?: number
  size?: number
}

function listingsSearchParams(p: ListingsQueryParams): string {
  const sp = new URLSearchParams()
  if (p.fantasyPlayerId != null) sp.set('fantasyPlayerId', String(p.fantasyPlayerId))
  if (p.rarity) sp.set('rarity', p.rarity)
  if (p.minPrice != null) sp.set('minPrice', String(p.minPrice))
  if (p.maxPrice != null) sp.set('maxPrice', String(p.maxPrice))
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

export function createMarketplaceListing(
  initData: string | undefined,
  body: { userCardId: number; price: number },
) {
  return apiSend<MarketplaceListingEntry>('POST', '/api/v1/marketplace/listings', initData, body)
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
