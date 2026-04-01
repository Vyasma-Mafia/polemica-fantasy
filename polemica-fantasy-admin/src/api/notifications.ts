import type { BroadcastAcceptedDto } from './types'
import { apiJson } from './client'

export function broadcastMessage(text: string) {
  return apiJson<BroadcastAcceptedDto>('/v1/admin/notifications/broadcast', {
    method: 'POST',
    body: JSON.stringify({ text }),
  })
}
