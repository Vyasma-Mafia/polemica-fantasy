import type { CreateCardPackRequest, UpdateCardPackRequest } from './packRequests'
import type { CardPackDto } from './types'
import { apiJson } from './client'

export type { CreateCardPackRequest, UpdateCardPackRequest } from './packRequests'

export function listCardPacks(tournamentId?: number) {
  const q =
    tournamentId != null ? `?tournamentId=${tournamentId}` : ''
  return apiJson<CardPackDto[]>(`/v1/admin/card-packs${q}`)
}

export function getCardPack(id: number) {
  return apiJson<CardPackDto>(`/v1/admin/card-packs/${id}`)
}

export function createCardPack(body: CreateCardPackRequest) {
  return apiJson<CardPackDto>('/v1/admin/card-packs', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateCardPack(id: number, body: UpdateCardPackRequest) {
  return apiJson<CardPackDto>(`/v1/admin/card-packs/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function updateCardPackPlayers(id: number, playerIds: number[]) {
  return apiJson<CardPackDto>(`/v1/admin/card-packs/${id}/players`, {
    method: 'PUT',
    body: JSON.stringify({ playerIds }),
  })
}
