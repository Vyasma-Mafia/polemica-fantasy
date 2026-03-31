import type { AdminUserListItemDto } from './types'
import { apiJson } from './client'

export function listAdminUsers(params?: {
  tournamentId: number
  seriesId: number
}) {
  if (params) {
    const search = new URLSearchParams({
      tournamentId: String(params.tournamentId),
      seriesId: String(params.seriesId),
    })
    return apiJson<AdminUserListItemDto[]>(`/v1/admin/users?${search.toString()}`)
  }
  return apiJson<AdminUserListItemDto[]>('/v1/admin/users')
}
