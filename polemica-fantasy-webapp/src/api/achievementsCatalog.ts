import { apiGet } from './client'
import type { AchievementCatalogItem } from './types'

export function fetchAchievementCatalog(initData: string) {
  return apiGet<AchievementCatalogItem[]>('/api/v1/achievements', initData)
}
