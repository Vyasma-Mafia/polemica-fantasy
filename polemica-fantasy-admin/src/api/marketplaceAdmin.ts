import type {
  BanPairResultDto,
  PairAnalysisDto,
  PairTradesResultDto,
} from './types'
import { apiJson, apiVoid } from './client'

export function getPairAnalysis() {
  return apiJson<PairAnalysisDto[]>('/v1/admin/marketplace/pair-analysis')
}

export function getPairTrades(userA: number, userB: number) {
  const q = new URLSearchParams({ userA: String(userA), userB: String(userB) })
  return apiJson<PairTradesResultDto>(`/v1/admin/marketplace/pair-trades?${q.toString()}`)
}

export function banPair(body: {
  telegramIdA: number
  telegramIdB: number
  reason: string
}) {
  return apiJson<BanPairResultDto>('/v1/admin/marketplace/ban-pair', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function unbanMarketplace(telegramId: number) {
  return apiVoid(`/v1/admin/marketplace/unban/${encodeURIComponent(telegramId)}`, {
    method: 'POST',
  })
}
