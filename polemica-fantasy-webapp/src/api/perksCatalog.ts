import { apiGet } from './client'
import type { PerkCatalogItem } from './types'

export function fetchPerkCatalog(initData: string) {
  return apiGet<PerkCatalogItem[]>('/api/v1/perks', initData)
}
