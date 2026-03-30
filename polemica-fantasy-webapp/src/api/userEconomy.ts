import { apiGet, apiSend } from './client'
import type { EconomyInfo, RecycleResult, RenewResult } from './types'

export function fetchEconomyInfo(initData: string) {
  return apiGet<EconomyInfo>('/api/v1/me/economy-info', initData)
}

export function recycleUserCard(initData: string, cardId: number) {
  return apiSend<RecycleResult>('POST', `/api/v1/me/cards/${cardId}/recycle`, initData)
}

export function renewUserCard(initData: string, cardId: number) {
  return apiSend<RenewResult>('POST', `/api/v1/me/cards/${cardId}/renew`, initData)
}
