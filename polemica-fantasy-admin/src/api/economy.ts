import type { BulkUpdateEconomyConfigRequest, EconomyConfigItemDto, UpdateEconomyConfigRequest } from './types'
import { apiJson } from './client'

export function listEconomyConfig() {
  return apiJson<EconomyConfigItemDto[]>('/v1/admin/economy-config')
}

export function updateEconomyKey(key: string, body: UpdateEconomyConfigRequest) {
  return apiJson<EconomyConfigItemDto>(`/v1/admin/economy-config/${encodeURIComponent(key)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function bulkUpdateEconomy(body: BulkUpdateEconomyConfigRequest) {
  return apiJson<EconomyConfigItemDto[]>('/v1/admin/economy-config', {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}
