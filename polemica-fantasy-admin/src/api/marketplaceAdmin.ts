import type {
  BanUserRequest,
  BanPairPreviewDto,
  BanPairResultDto,
  PagedComplainedTransactionsDto,
  PagedPairSanctionHistoryDto,
  PagedUsersByComplaintsDto,
  PairAnalysisDto,
  PairTradesResultDto,
  SanctionTransactionRequest,
  SanctionTransactionResultDto,
  TransactionComplaintsListDto,
} from './types'
import { apiJson, apiVoid } from './client'

export function getPairAnalysis() {
  return apiJson<PairAnalysisDto[]>('/v1/admin/marketplace/pair-analysis')
}

export function getPairTrades(userA: number, userB: number) {
  const q = new URLSearchParams({ userA: String(userA), userB: String(userB) })
  return apiJson<PairTradesResultDto>(`/v1/admin/marketplace/pair-trades?${q.toString()}`)
}

export function getBanPairPreview(userA: number, userB: number) {
  const q = new URLSearchParams({ userA: String(userA), userB: String(userB) })
  return apiJson<BanPairPreviewDto>(`/v1/admin/marketplace/ban-pair/preview?${q.toString()}`)
}

export function getBanPairHistory(options?: { page?: number; size?: number }) {
  const page = options?.page ?? 0
  const size = options?.size ?? 20
  const q = new URLSearchParams({ page: String(page), size: String(size) })
  return apiJson<PagedPairSanctionHistoryDto>(`/v1/admin/marketplace/ban-pair/history?${q.toString()}`)
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

export function getComplainedTransactions(options?: {
  page?: number
  size?: number
  minComplaints?: number
  sortBy?: 'complaints_desc' | 'sold_at_desc'
}) {
  const page = options?.page ?? 0
  const size = options?.size ?? 20
  const minComplaints = options?.minComplaints ?? 1
  const sortBy = options?.sortBy ?? 'complaints_desc'
  const q = new URLSearchParams({
    page: String(page),
    size: String(size),
    minComplaints: String(minComplaints),
    sortBy,
  })
  return apiJson<PagedComplainedTransactionsDto>(`/v1/admin/marketplace/complained-transactions?${q.toString()}`)
}

export function getTransactionComplaints(listingId: number) {
  return apiJson<TransactionComplaintsListDto>(
    `/v1/admin/marketplace/transactions/${encodeURIComponent(listingId)}/complaints`,
  )
}

export function sanctionTransaction(listingId: number, body: SanctionTransactionRequest) {
  return apiJson<SanctionTransactionResultDto>(
    `/v1/admin/marketplace/transactions/${encodeURIComponent(listingId)}/sanction`,
    {
      method: 'POST',
      body: JSON.stringify(body),
    },
  )
}

export function getUsersByComplaints(options?: {
  page?: number
  size?: number
  sortBy?: 'total_complaints_desc' | 'avg_complaints_desc'
}) {
  const page = options?.page ?? 0
  const size = options?.size ?? 20
  const sortBy = options?.sortBy ?? 'total_complaints_desc'
  const q = new URLSearchParams({
    page: String(page),
    size: String(size),
    sortBy,
  })
  return apiJson<PagedUsersByComplaintsDto>(`/v1/admin/marketplace/users-by-complaints?${q.toString()}`)
}

export function banMarketplaceUser(telegramId: number, body: BanUserRequest) {
  return apiVoid(`/v1/admin/marketplace/users/${encodeURIComponent(telegramId)}/ban`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function markPairCleared(body: {
  telegramIdA: number
  telegramIdB: number
  note?: string
}) {
  return apiVoid('/v1/admin/marketplace/pair-analysis/clear', {
    method: 'POST',
    body: JSON.stringify({
      telegramIdA: body.telegramIdA,
      telegramIdB: body.telegramIdB,
      note: body.note,
    }),
  })
}

export function unmarkPairCleared(userA: number, userB: number) {
  const q = new URLSearchParams({ userA: String(userA), userB: String(userB) })
  return apiVoid(`/v1/admin/marketplace/pair-analysis/clear?${q.toString()}`, {
    method: 'DELETE',
  })
}
