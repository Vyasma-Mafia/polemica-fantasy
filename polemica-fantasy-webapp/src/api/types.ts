export type TournamentStatus = 'DRAFT' | 'ACTIVE' | 'FINISHED'
export type TournamentKind = 'STANDALONE' | 'POLEMICA_COMPETITION'
export type SeriesStatus = 'UPCOMING' | 'ACTIVE' | 'SCORING' | 'FINISHED'
export type Rarity = 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY'
export type OccurrenceType = 'ONCE_PER_GAME' | 'MULTIPLE_PER_GAME'
export type AchievementState = 'LOCKED' | 'IN_PROGRESS' | 'COMPLETED_UNCLAIMED' | 'CLAIMED'

export interface AchievementReward {
  id: number | null
  type: string
  amount: number | null
  code: string | null
  metadata: string | null
}

export interface AchievementItem {
  code: string
  title: string
  description: string | null
  category: string
  conditionType: string
  state: AchievementState
  progressValue: number
  targetValue: number
  completedAt: string | null
  claimedAt: string | null
  historyPolicy: string
  rarity: Rarity
  visibility: string
  iconUrl: string | null
  accentColor: string | null
  rewards: AchievementReward[]
}

export interface AchievementCategory {
  code: string
  name: string
  achievements: AchievementItem[]
}

export interface AchievementSummary {
  completed: number
  claimed: number
  totalVisible: number
  unclaimedRewards: number
}

export interface AchievementCatalog {
  categories: AchievementCategory[]
  summary: AchievementSummary
}

export interface AchievementClaimResult {
  achievementCode: string
  claimedAt: string | null
  fantikiDelta: number
  newFantikiBalance: number
  cosmeticUnlocks: { type: string; code: string }[]
  grantedCards: AchievementGrantedCard[]
  pendingChoices: AchievementPendingCardChoice[]
}

export interface AchievementGrantedCard {
  userCardId: number
  fantasyPlayerId: number
  playerName: string
  playerPhotoUrl: string | null
  rarity: Rarity
  skinCode: string | null
}

export interface AchievementPendingCardChoice {
  rewardId: number
  requiredCount: number
  options: AchievementCardChoiceOption[]
}

export interface AchievementCardChoiceOption {
  optionId: string
  fantasyPlayerId: number
  playerName: string
  playerPhotoUrl: string | null
  rarity: Rarity
  skinCode: string | null
  perks: UserCardItem['perks']
}

export interface ProfileFrame {
  code: string
  name: string
  assetUrl: string | null
}

export interface AchievementBadge {
  code: string
  title: string
  iconUrl: string | null
  rarity: Rarity
  accentColor: string | null
}

export interface ProfileCustomization {
  profileFrameCode: string | null
  unlockedFrames: ProfileFrame[]
  featuredAchievementCodes: string[]
  availableFeaturedAchievements: AchievementBadge[]
}

export interface UpdateProfileCustomizationRequest {
  profileFrameCode: string | null
  featuredAchievementCodes: string[]
}

export interface PlayerAchievementSummary {
  completed: number
  claimed: number
  totalVisible: number
}

export interface PlayerNextAchievement {
  code: string
  title: string
  progressValue: number
  targetValue: number
}

export interface PerkCatalogItem {
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
  /** Успешные открытия паков (магазин). */
  packOpensCount: number
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
  /** Max total opens per user; 0 = unlimited. */
  maxOpensPerUser: number
  /** Cards already received from this pack (opens used). */
  packOpensUsed: number
  rarityLayout: StorePackRaritySlot[]
}

export interface BuyPackResponse {
  fantiki: number
  cards: UserCardItem[]
  /** Cards shown in pack opening animation; can include visual-only companions. */
  openingCards?: PackOpeningCard[]
}

export interface PackOpeningUserCard {
  kind: 'USER_CARD'
  card: UserCardItem
  rarity: Rarity
  value: number
  relatedUserCardId?: number | null
  companionCardName?: null
  companionCardImageUrl?: null
}

export interface PackOpeningCompanionCard {
  kind: 'COMPANION'
  card?: null
  companionCardName: string
  companionCardImageUrl: string | null
  rarity: Rarity
  value: number
  relatedUserCardId: number
}

export type PackOpeningCard = PackOpeningUserCard | PackOpeningCompanionCard

export interface FantasyTeamSeriesGameInfo {
  seriesGameId: number
  polemicaGameId: number
  gameName: string
  scored: boolean
}

export interface PerkInGame {
  perkId: string
  perkName: string
  bonusPoints: number
}

export interface CardGameBreakdown {
  basePoints: number | null
  perkBonus: number | null
  rarityModifier: number | null
  totalScore: number | null
  scoredPolemicaUserId: number | null
  scoredPlayerName: string | null
  scoredViaReplacement: boolean
  perks: PerkInGame[]
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
  leagues: SeriesLeagueBrief[]
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
  leagues: SeriesLeagueBrief[]
}

export interface SeriesLeagueBrief {
  code: string
  name: string
  hasTeam: boolean
  valueCap: number | null
}

export interface SeriesLeagueInfo {
  code: string
  name: string
  description: string | null
  valueCap: number | null
  maxLegendaryCount: number | null
  minTeamSize: number
  maxTeamSize: number
  rewardScale: number
  hasTeam: boolean
}

/** Public profile snippet (лидерборд, чужая команда). */
export interface UserPublic {
  telegramId: number
  username: string | null
  firstName: string | null
  displayName: string | null
  profileFrameCode: string | null
}

/** GET /api/v1/rating — строка глобального рейтинга */
export interface RatingEntry {
  rank: number
  user: UserPublic
  fantikiBalance: number
  cardsValue: number
  totalValue: number
  cardsCount: number
  /** Сумма начислений за лидерборд серий (SERIES_REWARD), не входит в totalValue. */
  prizeWinnings: number
}

export interface GlobalRating {
  entries: RatingEntry[]
  currentUser: RatingEntry | null
}

export interface LeaderboardEntry {
  rank: number
  totalScore: number | null
  user: UserPublic
  fantasyPlayerIds: number[]
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
  seriesName?: string
  leagueCode: string
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
  perks: { perkId: string; perkName: string; bonusPoints: number }[]
  usesRemaining: number
  timesRenewed: number
  /** Effective marketplace minimum for this concrete card. */
  minListingPrice?: number | null
  /** Present when the card came from opening a pack */
  sourceCardPackId?: number | null
  /** Telegram user id of the crafter; set after legendary upgrade from a pack card */
  craftedByTelegramUserId?: number | null
  /** Вычисляемая ценность (база по редкости + бонус за перки). */
  value: number
  /** Коды лиг этой серии, где карта уже используется (при `seriesId` запросе). */
  leaguesInSeries?: string[] | null
  /** Можно ли поставить карту ещё хотя бы в одну лигу текущей серии. */
  canJoinMoreLeagues?: boolean | null
  /** Активный лот на маркетплейсе; null/undefined если карта не выставлена. */
  activeMarketplaceListing?: { listingId: number; price: number } | null
  /** Cosmetic skin code; null/undefined means default visual. */
  skinCode?: string | null
}

/** GET /api/v1/legendary-upgrade/info */
export interface LegendaryUpgradeInfo {
  cost: number
  balance: number
  canAfford: boolean
}

export interface LegendaryUpgradeResponse {
  card: UserCardItem
  easterEgg?: {
    message: string
    bonusFantiki: number
    companionCardName: string
    companionCardImageUrl: string | null
  } | null
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
  /** Минимальная цена листинга по редкости. */
  marketplaceMinPrices: Record<Rarity, number>
  /** Максимальная цена листинга по редкости. */
  marketplaceMaxPrices: Record<Rarity, number>
  /** Сколько паков нужно открыть до первой покупки на маркетплейсе. */
  minPackOpensBeforeMarketplacePurchase: number
  cardValues: {
    baseValues: Record<Rarity, number>
    perkBonus: number
  }
  leagues: Record<string, { valueCap: number | null; rewardScale: number }>
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
  leagueCode: string
  totalScore: number | null
  submittedAt: string
  slots: { slot: number; userCardId: number; score: number | null }[]
}

/** GET /api/v1/fantasy-players */
export interface FantasyPlayerBrief {
  id: number
  nickname: string
  /** Фото в масти (Polemica), может отсутствовать. */
  photoUrl: string | null
}

/** GET /api/v1/marketplace/listings */
export type MarketplaceSortBy = 'price_asc' | 'price_desc' | 'created_at_desc'

export interface MarketplaceCardPerk {
  perkId: string
  name: string
  bonusPoints: number
}

export interface MarketplaceListingCard {
  userCardId: number
  fantasyPlayerId: number
  playerName: string
  playerPhotoUrl: string | null
  rarity: Rarity
  perks: MarketplaceCardPerk[]
  value: number | null
  skinCode?: string | null
  timesRenewed: number
  maxRenewals: number
  minListingPrice: number
}

export interface MarketplaceSellerBrief {
  displayName: string
}

export interface MarketplaceListingEntry {
  listingId: number
  price: number
  createdAt: string
  card: MarketplaceListingCard
  seller: MarketplaceSellerBrief | null
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
  listingId: number
  playerName: string
  rarity: Rarity
  price: number
  soldAt: string
  sellerDisplayName: string
  buyerDisplayName: string
  sanctioned: boolean
  /** Карта сделки — превью в ленте */
  card: MarketplaceListingCard
}

export interface MarketplaceFeed {
  items: MarketplaceFeedItem[]
}

export interface MarketplaceTransactionDetail {
  listingId: number
  price: number
  soldAt: string
  commission: number
  sellerReceived: number
  seller: TransactionParticipant
  buyer: TransactionParticipant
  card: TransactionCard
  complaint: TransactionComplaintInfo
  sanction: TransactionSanctionInfo | null
}

export interface TransactionParticipant {
  telegramId: number
  displayName: string
}

export interface TransactionCard {
  fantasyPlayerId: number
  playerName: string
  playerPhotoUrl: string | null
  rarity: Rarity
  perks: MarketplaceCardPerk[]
  skinCode?: string | null
}

export interface TransactionComplaintInfo {
  totalComplaints: number
  userAlreadyComplained: boolean
}

export interface TransactionSanctionInfo {
  sanctionedAt: string
  reason: string
}

export interface ComplainResult {
  listingId: number
  totalComplaints: number
  remainingToday: number
}

export interface BuyCardResult {
  listing: MarketplaceListingEntry
  card: UserCardItem
  pricePaid: number
  sellerReceived: number
  commission: number
  newBalance: number
}

export interface MarketplaceAnalyticsSummaryItem {
  fantasyPlayerId: number
  rarity: Rarity
  activeCount: number
  minActivePrice: number | null
}

export interface MarketplaceAnalyticsSummary {
  items: MarketplaceAnalyticsSummaryItem[]
}

export interface MarketplaceRecentSale {
  price: number
  soldAt: string
}

export interface MarketplaceAnalyticsDetail {
  fantasyPlayerId: number
  rarity: Rarity
  activeCount: number
  activeMinPrice: number | null
  activeMaxPrice: number | null
  recentSales: MarketplaceRecentSale[]
  avgSalePrice: number | null
}

export type CardAcquisitionType = 'PACK_OPENING' | 'ADMIN_GRANT' | 'MARKETPLACE_PURCHASE' | 'ACHIEVEMENT_REWARD'

export interface CardOwnershipHistoryEntry {
  ownerDisplayName: string
  acquisitionType: CardAcquisitionType
  acquisitionLabel: string
  acquiredAt: string
}

/** GET /api/v1/players/{telegramId}/profile */
export interface PlayerRatingSnapshot {
  rank: number
  fantikiBalance: number
  cardsValue: number
  cardsCount: number
  prizeWinnings: number
  totalValue: number
}

export interface PlayerLeagueWins {
  leagueCode: string
  leagueName: string
  winsCount: number
}

export interface PlayerSeriesWins {
  total: number
  byLeague: PlayerLeagueWins[]
}

export interface PlayerSeriesResult {
  seriesId: number
  seriesName: string
  tournamentId: number
  tournamentName: string
  leagueCode: string
  leagueName: string
  rank: number | null
  totalScore: number | null
  participantsCount: number
  status: SeriesStatus
}

export interface PlayerCollectionSummary {
  totalCards: number
  byRarity: Record<string, number>
}

export interface PlayerMarketplaceStats {
  activeSalesCount: number
  totalSoldCount: number
  totalPurchasedCount: number
}

export type TradeType = 'SALE' | 'PURCHASE'

export interface PlayerMarketplaceTrade {
  listingId: number
  playerName: string
  rarity: Rarity
  price: number
  date: string
  counterpartyDisplayName: string
  type: TradeType
  sanctioned: boolean
}

export interface PlayerProfile {
  user: UserPublic
  memberSince: string
  rating: PlayerRatingSnapshot | null
  seriesWins: PlayerSeriesWins
  achievementSummary: PlayerAchievementSummary
  profileFrame: ProfileFrame | null
  featuredAchievements: AchievementBadge[]
  nextAchievement: PlayerNextAchievement | null
  seriesHistory: PlayerSeriesResult[]
  collectionSummary: PlayerCollectionSummary
  marketplaceStats: PlayerMarketplaceStats
  recentTrades: PlayerMarketplaceTrade[]
}
