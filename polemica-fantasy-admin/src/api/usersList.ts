import type { AdminUserListItemDto } from './types'
import { apiJson } from './client'

export type ListAdminUsersParams = {
  tournamentId?: number
  seriesId?: number
  /** Trims; omit or empty = no search filter. */
  q?: string
}

export function listAdminUsers(params?: ListAdminUsersParams) {
  const search = new URLSearchParams()
  if (params?.tournamentId != null) search.set('tournamentId', String(params.tournamentId))
  if (params?.seriesId != null) search.set('seriesId', String(params.seriesId))
  if (params?.q != null && params.q.trim() !== '') search.set('q', params.q.trim())
  const qs = search.toString()
  return apiJson<AdminUserListItemDto[]>(qs ? `/v1/admin/users?${qs}` : '/v1/admin/users')
}
