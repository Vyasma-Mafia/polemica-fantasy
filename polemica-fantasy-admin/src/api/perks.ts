import type { UpdatePerkRequest } from './perkRequests'
import type { PerkAdminDto } from './types'
import { apiJson } from './client'

export type { UpdatePerkRequest } from './perkRequests'

export function listPerks() {
  return apiJson<PerkAdminDto[]>('/v1/admin/perks')
}

export function updatePerk(id: string, body: UpdatePerkRequest) {
  return apiJson<PerkAdminDto>(`/v1/admin/perks/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}
