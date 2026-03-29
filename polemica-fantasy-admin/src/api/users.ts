import type { UserProfileDto } from './types'
import { apiJson } from './client'

export function giveFantiki(telegramUserId: number, amount: number) {
  return apiJson<UserProfileDto>(
    `/v1/admin/users/${telegramUserId}/give-fantiki`,
    {
      method: 'POST',
      body: JSON.stringify({ amount }),
    },
  )
}
