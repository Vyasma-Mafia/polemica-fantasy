import type { TournamentKind, TournamentStatus } from './types'

export interface CreateTournamentRequest {
  name: string
  description?: string | null
  status: TournamentStatus
  kind?: TournamentKind | null
  polemicaCompetitionId?: number | null
}

export interface UpdateTournamentRequest {
  name?: string | null
  description?: string | null
  status?: TournamentStatus | null
  kind?: TournamentKind | null
  polemicaCompetitionId?: number | null
}

export interface AddTournamentPlayerRequest {
  fantasyPlayerId?: number | null
  polemicaUserId?: number | null
  nickname?: string | null
}

export interface PatchTournamentPlayerRequest {
  excludedFromPackPool: boolean
}
