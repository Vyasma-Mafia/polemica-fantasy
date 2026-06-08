import type {
  AddTournamentPlayerRequest,
  CreateTournamentRequest,
  PatchTournamentPlayerRequest,
  UpdateTournamentRequest,
} from './tournamentRequests'
import type {
  TournamentDetailDto,
  TournamentDto,
  TournamentPlayerDto,
} from './types'
import { apiFetch, apiJson, apiVoid } from './client'

export type {
  AddTournamentPlayerRequest,
  CreateTournamentRequest,
  PatchTournamentPlayerRequest,
  UpdateTournamentRequest,
} from './tournamentRequests'

export function listTournaments() {
  return apiJson<TournamentDto[]>('/v1/admin/tournaments')
}

export function getTournament(id: number) {
  return apiJson<TournamentDetailDto>(`/v1/admin/tournaments/${id}`)
}

export async function openTournamentReport(
  tournamentId: number,
  seriesIds: number[],
) {
  const reportWindow = window.open('', '_blank')
  if (!reportWindow) {
    throw new Error('Browser blocked report popup')
  }
  reportWindow.opener = null

  const params = new URLSearchParams()
  seriesIds.forEach((seriesId) => params.append('seriesIds', String(seriesId)))
  try {
    const res = await apiFetch(
      `/v1/admin/tournaments/${tournamentId}/report.html?${params.toString()}`,
    )
    if (!res.ok) {
      const text = await res.text()
      throw new Error(text || `HTTP ${res.status}`)
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    reportWindow.location.href = url
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (error) {
    reportWindow.close()
    throw error
  }
}

export function createTournament(body: CreateTournamentRequest) {
  return apiJson<TournamentDto>('/v1/admin/tournaments', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateTournament(id: number, body: UpdateTournamentRequest) {
  return apiJson<TournamentDto>(`/v1/admin/tournaments/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function addTournamentPlayer(
  tournamentId: number,
  body: AddTournamentPlayerRequest,
) {
  return apiJson<TournamentPlayerDto>(
    `/v1/admin/tournaments/${tournamentId}/players`,
    { method: 'POST', body: JSON.stringify(body) },
  )
}

export function removeTournamentPlayer(tournamentId: number, playerId: number) {
  return apiVoid(`/v1/admin/tournaments/${tournamentId}/players/${playerId}`, {
    method: 'DELETE',
  })
}

export function patchTournamentPlayer(
  tournamentId: number,
  playerId: number,
  body: PatchTournamentPlayerRequest,
) {
  return apiJson<TournamentPlayerDto>(
    `/v1/admin/tournaments/${tournamentId}/players/${playerId}`,
    { method: 'PATCH', body: JSON.stringify(body) },
  )
}

export function uploadPlayerPhoto(
  tournamentId: number,
  playerId: number,
  file: File,
) {
  const fd = new FormData()
  fd.append('file', file)
  return apiJson<TournamentPlayerDto>(
    `/v1/admin/tournaments/${tournamentId}/players/${playerId}/photo`,
    { method: 'POST', body: fd },
  )
}
