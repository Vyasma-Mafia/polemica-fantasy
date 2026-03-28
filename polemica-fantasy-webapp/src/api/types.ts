export type TournamentStatus = 'DRAFT' | 'ACTIVE' | 'FINISHED'
export type TournamentKind = 'STANDALONE' | 'POLEMICA_COMPETITION'
export type SeriesStatus = 'UPCOMING' | 'ACTIVE' | 'SCORING' | 'FINISHED'
export type Rarity = 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY'

export interface UserProfile {
  id: number
  telegramId: number
  username: string | null
  firstName: string | null
  createdAt: string
}

export interface UserTournament {
  id: number
  name: string
  description: string | null
  status: TournamentStatus
  kind: TournamentKind
  polemicaCompetitionId: number | null
  createdAt: string
}

export interface UserSeriesSummary {
  id: number
  tournamentId: number
  name: string
  namePrefix: string | null
  gameNumFrom: number | null
  gameNumTo: number | null
  status: SeriesStatus
  startsAt: string
  teamDeadline: string
}

export interface UserTournamentDetail extends UserTournament {
  series: UserSeriesSummary[]
}

export interface UserSeriesDetail {
  id: number
  tournamentId: number
  name: string
  namePrefix: string | null
  gameNumFrom: number | null
  gameNumTo: number | null
  status: SeriesStatus
  startsAt: string
  teamDeadline: string
  players: { tournamentPlayerId: number; nickname: string; photoUrl: string | null }[]
  games: { polemicaGameId: number; gameName: string; scored: boolean }[]
}

export interface LeaderboardEntry {
  rank: number
  totalScore: number | null
  user: { telegramId: number; username: string | null; firstName: string | null }
}

export interface UserCardItem {
  id: number
  acquiredAt: string
  cardTemplateId: number
  fantasyPlayerId: number
  rarity: Rarity
  imageUrl: string | null
  description: string | null
  playerNickname: string
  playerPhotoUrl: string | null
  achievements: { achievementType: string; bonusPoints: number }[]
}

export interface FantasyTeamDto {
  seriesId: number
  tournamentId: number
  totalScore: number | null
  submittedAt: string
  slots: { slot: number; userCardId: number; score: number | null }[]
}
