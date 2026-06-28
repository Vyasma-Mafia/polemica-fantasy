import type { PagedFantikiAdjustmentsDto, UserProfileDto } from './types'
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
  return apiJson<PagedFantikiAdjustmentsDto>(
    `/v1/admin/users/${telegramUserId}/fantiki-adjustments?${q.toString()}`,
  )
}
