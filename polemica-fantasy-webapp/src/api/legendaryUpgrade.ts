import { apiGet, apiSend } from './client'
import type { LegendaryUpgradeInfo, UserCardItem } from './types'

export function fetchLegendaryUpgradeInfo(initData: string) {
  return apiGet<LegendaryUpgradeInfo>('/api/v1/legendary-upgrade/info', initData)
}

export function postLegendaryUpgrade(
  initData: string,
  body: { userCardId: number; achievementId: string },
) {
  return apiSend<UserCardItem>('POST', '/api/v1/legendary-upgrade', initData, body)
}
