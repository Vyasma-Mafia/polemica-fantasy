import type {
  FantasyPlayerAdminDto,
  FantasyPlayerMergePreviewDto,
  FantasyPlayerMergeResultDto,
} from './types'
import { apiJson } from './client'

export interface CreateFantasyPlayerRequest {
  polemicaUserId: number
  nickname: string
}

export interface UpdateFantasyPlayerRequest {
  nickname: string
}

export interface AddFantasyPlayerAliasRequest {
  polemicaUserId: number
  primary?: boolean
}

export interface MergeFantasyPlayersRequest {
  sourceFantasyPlayerId: number
  reason: string
}

export function listFantasyPlayers(params?: { query?: string }) {
  const q = new URLSearchParams()
  if (params?.query?.trim()) {
    q.set('query', params.query.trim())
  }
  const suffix = q.toString() ? `?${q.toString()}` : ''
  return apiJson<FantasyPlayerAdminDto[]>(`/v1/admin/fantasy-players${suffix}`)
}

export function createFantasyPlayer(body: CreateFantasyPlayerRequest) {
  return apiJson<FantasyPlayerAdminDto>('/v1/admin/fantasy-players', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateFantasyPlayer(
  id: number,
  body: UpdateFantasyPlayerRequest,
) {
  return apiJson<FantasyPlayerAdminDto>(`/v1/admin/fantasy-players/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function uploadFantasyPlayerPhoto(id: number, file: File) {
  const fd = new FormData()
  fd.append('file', file)
  return apiJson<FantasyPlayerAdminDto>(
    `/v1/admin/fantasy-players/${id}/photo`,
    { method: 'POST', body: fd },
  )
}

export function addFantasyPlayerAlias(
  id: number,
  body: AddFantasyPlayerAliasRequest,
) {
  return apiJson<FantasyPlayerAdminDto>(`/v1/admin/fantasy-players/${id}/aliases`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function getFantasyPlayerMergePreview(
  targetId: number,
  sourceFantasyPlayerId: number,
) {
  const q = new URLSearchParams()
  q.set('sourceFantasyPlayerId', String(sourceFantasyPlayerId))
  return apiJson<FantasyPlayerMergePreviewDto>(
    `/v1/admin/fantasy-players/${targetId}/merge-preview?${q.toString()}`,
  )
}

export function mergeFantasyPlayers(
  targetId: number,
  body: MergeFantasyPlayersRequest,
) {
  return apiJson<FantasyPlayerMergeResultDto>(
    `/v1/admin/fantasy-players/${targetId}/merge`,
    {
      method: 'POST',
      body: JSON.stringify(body),
    },
  )
}
