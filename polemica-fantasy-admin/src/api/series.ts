import type {
  AssignSeriesPlayersRequest,
  CreateSeriesRequest,
  UpdateSeriesRequest,
} from './seriesRequests'
import type { SeriesDto } from './types'
import { apiJson, apiVoid } from './client'

export type {
  CreateSeriesRequest,
  UpdateSeriesRequest,
  AssignSeriesPlayersRequest,
} from './seriesRequests'

export function listSeriesByTournament(tournamentId: number) {
  return apiJson<SeriesDto[]>(
    `/v1/admin/tournaments/${tournamentId}/series`,
  )
}

export function getSeries(id: number) {
  return apiJson<SeriesDto>(`/v1/admin/series/${id}`)
}

export function createSeries(tournamentId: number, body: CreateSeriesRequest) {
  return apiJson<SeriesDto>(`/v1/admin/tournaments/${tournamentId}/series`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateSeries(id: number, body: UpdateSeriesRequest) {
  return apiJson<SeriesDto>(`/v1/admin/series/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function assignSeriesPlayers(id: number, body: AssignSeriesPlayersRequest) {
  return apiJson<SeriesDto>(`/v1/admin/series/${id}/players`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function syncGames(id: number) {
  return apiVoid(`/v1/admin/series/${id}/sync-games`, { method: 'POST' })
}

export function calculateScores(id: number) {
  return apiVoid(`/v1/admin/series/${id}/calculate-scores`, { method: 'POST' })
}
