import type { SeriesStatus } from './types'

export const MAX_EXPECTED_GAME_COUNT = 1000

export interface StreamLinkRequest {
  label?: string | null
  url: string
}

export interface CreateSeriesRequest {
  name: string
  namePrefix?: string | null
  gameNumFrom?: number | null
  gameNumTo?: number | null
  gamePhase?: number | null
  gameStartedOn?: string | null
  status: SeriesStatus
  startsAt: string
  teamDeadline: string
  expectedGameCount?: number | null
  streamLinks?: StreamLinkRequest[]
}

export interface UpdateSeriesRequest {
  name?: string | null
  namePrefix?: string | null
  gameNumFrom?: number | null
  gameNumTo?: number | null
  gamePhase?: number | null
  gameStartedOn?: string | null
  status?: SeriesStatus | null
  startsAt?: string | null
  teamDeadline?: string | null
  expectedGameCount?: number | null
  streamLinks?: StreamLinkRequest[] | null
}

export interface AssignSeriesPlayersRequest {
  tournamentPlayerIds: number[]
  replacementPolemicaUserIds?: Record<number, number | null>
}

export interface BatchStartSeriesRequest {
  seriesIds: number[]
}

export interface AddSeriesGameRequest {
  polemicaGameId: number
}

export interface CreateResultMafiaOverrideRequest {
  gameNumber: number
  correctedMafiaLine: string
  reason: string
}
