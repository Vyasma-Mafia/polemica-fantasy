import type { PagedFantikiTransactionsDto, UserProfileDto } from './types'
import { apiJson } from './client'

export function giveFantiki(
  telegramUserId: number,
  body: { amount: number; adminReason: string },
) {
  return apiJson<UserProfileDto>(
    `/v1/admin/users/${telegramUserId}/give-fantiki`,
    {
      method: 'POST',
      body: JSON.stringify(body),
    },
  )
}

export function takeFantiki(
  telegramUserId: number,
  body: { amount: number; adminReason: string },
) {
  return apiJson<UserProfileDto>(
    `/v1/admin/users/${telegramUserId}/take-fantiki`,
    {
      method: 'POST',
      body: JSON.stringify(body),
    },
  )
}

export function getFantikiAdjustments(
  telegramUserId: number,
  options?: { page?: number; size?: number },
) {
  const page = options?.page ?? 0
  const size = options?.size ?? 20
  const q = new URLSearchParams({ page: String(page), size: String(size) })
  return apiJson<PagedFantikiTransactionsDto>(
    `/v1/admin/users/${telegramUserId}/fantiki-adjustments?${q.toString()}`,
  )
}

export function getFantikiTransactions(options?: {
  telegramUserId?: number
  page?: number
  size?: number
}) {
  const page = options?.page ?? 0
  const size = options?.size ?? 20
  const q = new URLSearchParams({ page: String(page), size: String(size) })
  if (options?.telegramUserId != null) {
    q.set('telegramUserId', String(options.telegramUserId))
  }
  return apiJson<PagedFantikiTransactionsDto>(
    `/v1/admin/users/fantiki-transactions?${q.toString()}`,
  )
}
