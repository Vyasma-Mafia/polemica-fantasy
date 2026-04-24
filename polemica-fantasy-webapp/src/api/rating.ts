import { apiGet } from './client'
import type { GlobalRating } from './types'

export async function fetchGlobalRating(initData: string): Promise<GlobalRating> {
  return apiGet<GlobalRating>('/api/v1/rating', initData)
}
