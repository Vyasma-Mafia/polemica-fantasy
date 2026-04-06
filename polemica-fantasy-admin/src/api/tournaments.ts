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
import { apiJson, apiVoid } from './client'

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
