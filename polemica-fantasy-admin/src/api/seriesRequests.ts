import type { SeriesStatus } from './types'

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
