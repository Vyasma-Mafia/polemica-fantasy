import type { SeriesStatus } from './types'

export interface CreateSeriesRequest {
  name: string
  namePrefix?: string | null
  gameNumFrom?: number | null
  gameNumTo?: number | null
  status: SeriesStatus
  startsAt: string
  teamDeadline: string
}

export interface UpdateSeriesRequest {
  name?: string | null
  namePrefix?: string | null
  gameNumFrom?: number | null
  gameNumTo?: number | null
  status?: SeriesStatus | null
  startsAt?: string | null
  teamDeadline?: string | null
}

export interface AssignSeriesPlayersRequest {
  tournamentPlayerIds: number[]
}

export interface BatchStartSeriesRequest {
  seriesIds: number[]
}
