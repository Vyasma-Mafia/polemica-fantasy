export type TournamentStatus = 'DRAFT' | 'ACTIVE' | 'FINISHED'

export type TournamentKind = 'STANDALONE' | 'POLEMICA_COMPETITION'

export type SeriesStatus = 'UPCOMING' | 'ACTIVE' | 'SCORING' | 'FINISHED'

export type Rarity = 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY'

export type AchievementType =
  | 'SHERIFF_FOUND_BLACK'
  | 'DON_FOUND_SHERIFF'
  | 'FIRST_NIGHT_SURVIVED'
  | 'WON_GAME'
  | 'BEST_MOVE'
  | 'SURVIVED_TILL_END'
  | 'VOTED_OUT_BLACK'
  | 'CORRECT_GUESS'
  | 'NO_FOULS'

export interface TournamentDto {
  id: number
  name: string
  description: string | null
  status: TournamentStatus
  kind: TournamentKind
  polemicaCompetitionId: number | null
  createdAt: string
}

export interface TournamentPlayerDto {
  id: number
  tournamentId: number
  fantasyPlayerId: number
  polemicaUserId: number
  nickname: string
  photoUrl: string | null
}

export interface TournamentDetailDto extends TournamentDto {
  players: TournamentPlayerDto[]
}

export interface SeriesDto {
  id: number
  tournamentId: number
  name: string
  namePrefix: string | null
  gameNumFrom: number | null
  gameNumTo: number | null
  status: SeriesStatus
  startsAt: string
  teamDeadline: string
  /** tournament_player.id assigned to this series */
  tournamentPlayerIds: number[]
}

export interface CardTemplateAchievementDto {
  id: number
  achievementType: AchievementType
  bonusPoints: number
}

export interface CardTemplateDto {
  id: number
  fantasyPlayerId: number
  rarity: Rarity
  imageUrl: string | null
  description: string | null
  achievements: CardTemplateAchievementDto[]
}

export interface CardPackRarityConfigResponseDto {
  id: number
  rarity: Rarity
  probability: number
  cardsCount: number
}

export interface CardPackDto {
  id: number
  name: string
  tournamentId: number
  active: boolean
  rarityConfigs: CardPackRarityConfigResponseDto[]
}

export interface UserCardDto {
  id: number
  telegramUserId: number
  cardTemplateId: number
  acquiredAt: string
}

export interface OpenPackResultDto {
  userCards: UserCardDto[]
}
