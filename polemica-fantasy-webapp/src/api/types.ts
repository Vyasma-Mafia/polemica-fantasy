export type TournamentStatus = 'DRAFT' | 'ACTIVE' | 'FINISHED'
export type TournamentKind = 'STANDALONE' | 'POLEMICA_COMPETITION'
export type SeriesStatus = 'UPCOMING' | 'ACTIVE' | 'SCORING' | 'FINISHED'
export type Rarity = 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY'
export type OccurrenceType = 'ONCE_PER_GAME' | 'MULTIPLE_PER_GAME'

export interface AchievementCatalogItem {
  id: string
  name: string
  description: string | null
  bonusPoints: number
  occurrenceType: OccurrenceType
  applicableRoles: string[]
  canAppearOnRandomCards: boolean
}

export interface UserProfile {
  id: number
  telegramId: number
  username: string | null
  firstName: string | null
  displayName: string | null
  createdAt: string
  fantiki: number
}

export interface StorePackRaritySlot {
  rarity: Rarity
  cardsCount: number
}

export interface StorePackItem {
  id: number
  name: string
  priceFantiki: number
  /** Remaining free opens for current user; 0 when not applicable. */
  freeOpensRemaining: number
  rarityLayout: StorePackRaritySlot[]
}

export interface BuyPackResponse {
  fantiki: number
  cards: UserCardItem[]
}

export interface FantasyTeamSeriesGameInfo {
  seriesGameId: number
  polemicaGameId: number
  gameName: string
  scored: boolean
}

export interface AchievementInGame {
  achievementId: string
  achievementName: string
  bonusPoints: number
}

export interface CardGameBreakdown {
  basePoints: number | null
  achievementBonus: number | null
  rarityModifier: number | null
  totalScore: number | null
  achievements: AchievementInGame[]
}

export interface FantasyTeamDetailSlot {
  slot: number
  userCardId: number
  cells: (CardGameBreakdown | null)[]
}

export interface FantasyTeamSeriesDetails {
  seriesId: number
  games: FantasyTeamSeriesGameInfo[]
  columns: FantasyTeamDetailSlot[]
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

/** GET /api/v1/tournaments/series-open-for-team — серии с открытым дедлайном состава. */
export interface SeriesOpenForTeam {
  seriesId: number
  tournamentId: number
  tournamentName: string
  seriesName: string
  gameNumFrom: number | null
  gameNumTo: number | null
  teamDeadline: string
}

/** Tournament roster / series players share this shape in the API. */
export interface SeriesPlayerEntry {
  tournamentPlayerId: number
  fantasyPlayerId: number
  nickname: string
  photoUrl: string | null
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
  players: SeriesPlayerEntry[]
  games: { polemicaGameId: number; gameName: string; scored: boolean }[]
}

/** Public profile snippet (лидерборд, чужая команда). */
export interface UserPublic {
  telegramId: number
  username: string | null
  firstName: string | null
  displayName: string | null
}

export interface LeaderboardEntry {
  rank: number
  totalScore: number | null
  user: UserPublic
}

/** Ответ GET /api/v1/series/{seriesId}/users/{telegramId}/fantasy-team */
export interface PublicFantasyTeamSlot {
  slot: number
  score: number | null
  card: UserCardItem
}

export interface PublicFantasyTeam {
  owner: UserPublic
  seriesId: number
  tournamentId: number
  totalScore: number | null
  submittedAt: string
  slots: PublicFantasyTeamSlot[]
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
  achievements: { achievementId: string; achievementName: string; bonusPoints: number }[]
  usesRemaining: number
  timesRenewed: number
  /** Present when the card came from opening a pack */
  sourceCardPackId?: number | null
  /** Telegram user id of the crafter; set after legendary upgrade from a pack card */
  craftedByTelegramUserId?: number | null
}

/** GET /api/v1/legendary-upgrade/info */
export interface LegendaryUpgradeInfo {
  cost: number
  balance: number
  canAfford: boolean
}

export interface RewardTier {
  label: string
  fantiki: number
}

export interface EconomyInfo {
  usesPerRarity: Record<Rarity, number>
  recycleValues: Record<Rarity, number>
  renewalCosts: Record<Rarity, number>
  maxRenewals: number
  seriesRewards: RewardTier[]
  /** Комиссия маркетплейса при продаже карты, % (экономика). */
  marketplaceCommissionPercent: number
}

export interface RecycleResult {
  fantikiEarned: number
  newBalance: number
}

export interface RenewResult {
  cost: number
  newBalance: number
  newUsesRemaining: number
}

export interface FantasyTeamDto {
  seriesId: number
  tournamentId: number
  totalScore: number | null
  submittedAt: string
  slots: { slot: number; userCardId: number; score: number | null }[]
}

/** GET /api/v1/marketplace/listings */
export type MarketplaceSortBy = 'price_asc' | 'price_desc' | 'created_at_desc'

export interface MarketplaceCardAchievement {
  achievementId: string
  name: string
  bonusPoints: number
}

export interface MarketplaceListingCard {
  userCardId: number
  fantasyPlayerId: number
  playerName: string
  playerPhotoUrl: string | null
  rarity: Rarity
  achievements: MarketplaceCardAchievement[]
}

export interface MarketplaceListingEntry {
  listingId: number
  price: number
  createdAt: string
  card: MarketplaceListingCard
  seller: { displayName: string }
  canBuy: boolean
  canBuyReason: string | null
}

export interface MarketplaceListingsPage {
  content: MarketplaceListingEntry[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface MarketplaceFeedItem {
  playerName: string
  rarity: Rarity
  price: number
  soldAt: string
  buyerDisplayName: string
  /** Карта сделки — превью в ленте */
  card: MarketplaceListingCard
}

export interface MarketplaceFeed {
  items: MarketplaceFeedItem[]
}

export interface BuyCardResult {
  listing: MarketplaceListingEntry
  card: UserCardItem
  pricePaid: number
  sellerReceived: number
  commission: number
  newBalance: number
}

export type CardAcquisitionType = 'PACK_OPENING' | 'ADMIN_GRANT' | 'MARKETPLACE_PURCHASE'

export interface CardOwnershipHistoryEntry {
  ownerDisplayName: string
  acquisitionType: CardAcquisitionType
  acquisitionLabel: string
  acquiredAt: string
}
