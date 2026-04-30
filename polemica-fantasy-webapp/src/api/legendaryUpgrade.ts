import { apiGet, apiSend } from './client'
import type { LegendaryUpgradeInfo, LegendaryUpgradeResponse } from './types'

export function fetchLegendaryUpgradeInfo(initData: string) {
  return apiGet<LegendaryUpgradeInfo>('/api/v1/legendary-upgrade/info', initData)
}

export function postLegendaryUpgrade(
  initData: string,
  body: { userCardId: number; achievementId: string },
) {
  return apiSend<LegendaryUpgradeResponse>('POST', '/api/v1/legendary-upgrade', initData, body)
}
