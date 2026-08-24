import type {
  AddSeriesGameRequest,
  AssignSeriesPlayersRequest,
  BatchStartSeriesRequest,
  CreateSeriesRequest,
  CreateResultMafiaOverrideRequest,
  UpdateSeriesRequest,
} from './seriesRequests'
import type {
  AdminSeriesGameDto,
  BatchStartSeriesResponseDto,
  SeriesDto,
  SeriesCompletionPreviewDto,
  SeriesFinalizationResultDto,
  ResultMafiaOverrideDto,
  SeriesPlayerMarketplaceUnlistResultDto,
  SeriesResultsDto,
} from './types'
import { apiJson, apiVoid } from './client'

export type {
  AddSeriesGameRequest,
  CreateSeriesRequest,
  UpdateSeriesRequest,
  AssignSeriesPlayersRequest,
  BatchStartSeriesRequest,
  CreateResultMafiaOverrideRequest,
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

export function unlistSeriesPlayerFromMarketplace(id: number, tournamentPlayerId: number) {
  return apiJson<SeriesPlayerMarketplaceUnlistResultDto>(
    `/v1/admin/series/${id}/players/${tournamentPlayerId}/unlist-marketplace`,
    { method: 'POST' },
  )
}

export function batchStartSeries(body: BatchStartSeriesRequest) {
  return apiJson<BatchStartSeriesResponseDto>('/v1/admin/series/batch-start', {
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

export function listSeriesGames(id: number) {
  return apiJson<AdminSeriesGameDto[]>(`/v1/admin/series/${id}/games`)
}

export function getSeriesResults(id: number) {
  return apiJson<SeriesResultsDto>(`/v1/admin/series/${id}/results`)
}

export function addSeriesGame(id: number, body: AddSeriesGameRequest) {
  return apiJson<AdminSeriesGameDto>(`/v1/admin/series/${id}/games`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function deleteSeriesGame(id: number, gameId: number) {
  return apiVoid(`/v1/admin/series/${id}/games/${gameId}`, { method: 'DELETE' })
}

export function getSeriesCompletionPreview(id: number) {
  return apiJson<SeriesCompletionPreviewDto>(`/v1/admin/series/${id}/completion-preview`)
}

export function finalizeSeries(id: number) {
  return apiJson<SeriesFinalizationResultDto>(`/v1/admin/series/${id}/finalize`, {
    method: 'POST',
  })
}

export function createResultMafiaOverride(id: number, body: CreateResultMafiaOverrideRequest) {
  return apiJson<ResultMafiaOverrideDto>(
    `/v1/admin/series/${id}/telegram-result/mafia-override`,
    { method: 'POST', body: JSON.stringify(body) },
  )
}
