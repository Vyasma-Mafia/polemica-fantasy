import type { TournamentKind, TournamentStatus } from './types'

export interface StreamLinkRequest {
  label?: string | null
  url: string
}

export interface CreateTournamentRequest {
  name: string
  description?: string | null
  status: TournamentStatus
  kind?: TournamentKind | null
  polemicaCompetitionId?: number | null
  defaultExpectedGameCount?: number | null
  streamLinks?: StreamLinkRequest[]
}

export interface UpdateTournamentRequest {
  name?: string | null
  description?: string | null
  status?: TournamentStatus | null
  kind?: TournamentKind | null
  polemicaCompetitionId?: number | null
  defaultExpectedGameCount?: number | null
  streamLinks?: StreamLinkRequest[] | null
}

export interface AddTournamentPlayerRequest {
  fantasyPlayerId?: number | null
  polemicaUserId?: number | null
  nickname?: string | null
}

export interface PatchTournamentPlayerRequest {
  excludedFromPackPool: boolean
}
