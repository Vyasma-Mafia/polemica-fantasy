export type TournamentStatus = 'DRAFT' | 'ACTIVE' | 'FINISHED'

export type TournamentKind = 'STANDALONE' | 'POLEMICA_COMPETITION'

export type SeriesStatus = 'UPCOMING' | 'ACTIVE' | 'SCORING' | 'FINISHED'

export type Rarity = 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY'

export type OccurrenceType = 'ONCE_PER_GAME' | 'MULTIPLE_PER_GAME'

export type AdminRole = 'ADMIN' | 'MODERATOR'

export interface AdminMeDto {
  username: string
  role: AdminRole
}

/** `perk.id` from the backend catalog (Flyway V10: sniper, voteForBlack, …). */
export type PerkId = string

export interface PerkAdminDto {
  id: string
  name: string
  description: string | null
  bonusPoints: number
  occurrenceType: OccurrenceType
  applicableRoles: string[]
  canAppearOnRandomCards: boolean
}

export type AchievementVisibility = 'PUBLIC' | 'HIDDEN' | 'SECRET' | 'PRIVATE'

export type AchievementRewardType =
  | 'FANTIKI'
  | 'PROFILE_FRAME'
  | 'COSMETIC_UNLOCK'
  | 'BADGE_STYLE'
  | 'RANDOM_CARD'
  | 'CARD_CHOICE_ROLL'

export interface AchievementAdminStatsDto {
  completedUsers: number
  claimedUsers: number
  unclaimedUsers: number
  totalProgress: number
  averageProgress: number
  nearCompletionUsers: number
  lastCompletedAt: string | null
}

export interface AchievementAdminRewardDto {
  type: AchievementRewardType | string
  amount: number | null
  code: string | null
  metadata: string | null
  displayOrder: number
}

export interface AchievementAdminDefinitionDto {
  code: string
  category: string
  conditionType: string
  historyPolicy: string
  targetValue: number
  chainGroup: string | null
  chainLevel: number | null
  title: string
  description: string | null
  iconUrl: string | null
  accentColor: string | null
  rarity: Rarity
  visibility: AchievementVisibility | string
  enabled: boolean
  trackingStartedAt: string | null
  displayOrder: number
  createdAt: string
  updatedAt: string
  rewards: AchievementAdminRewardDto[]
  stats: AchievementAdminStatsDto
}

export interface AchievementAdminListResponseDto {
  achievements: AchievementAdminDefinitionDto[]
}

export interface UpdateAchievementAdminRewardRequest {
  type: AchievementRewardType | string
  amount: number | null
  code: string | null
  metadata: string | null
  displayOrder: number
}

export interface UpdateAchievementAdminRequest {
  title: string
  description: string | null
  iconUrl: string | null
  accentColor: string | null
  rarity: Rarity
  visibility: AchievementVisibility
  enabled: boolean
  displayOrder: number
  rewards: UpdateAchievementAdminRewardRequest[]
}

export interface AchievementDryRunRowDto {
  code: string
  enabled: boolean
  instantCompleted: number
  instantFantikiLiability: number
}

export interface AchievementDryRunResponseDto {
  instantCompleted: number
  instantFantikiLiability: number
  rows: AchievementDryRunRowDto[]
}

export interface UserProfileDto {
  id: number
  telegramId: number
  username: string | null
  firstName: string | null
  displayName: string | null
  createdAt: string
  fantiki: number
}

export interface FantikiTransactionDto {
  id: number
  createdAt: string
  telegramId: number
  amount: number
  reason: string
  adminReason: string | null
}

export interface PagedFantikiTransactionsDto {
  content: FantikiTransactionDto[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface BroadcastAcceptedDto {
  recipientCount: number
}

export interface DirectMessageResultDto {
  telegramUserId: number
  sent: boolean
  skippedBlocked: boolean
  skippedPreference: boolean
  failed: boolean
}

export type ProductAudience =
  | 'ALL'
  | 'NEVER_ACTIVATED'
  | 'ACTION_NO_TEAM'
  | 'AT_RISK'
  | 'ACTIVE_CORE'

export interface ProductCampaignAudienceCountDto {
  audience: ProductAudience | string
  rawCount: number
  eligibleCount: number
}

export interface ProductCampaignPreviewDto extends ProductCampaignAudienceCountDto {
  text: string
  buttonText: string | null
  buttonUrl: string | null
}

export interface ProductCampaignDto {
  id: number
  title: string
  text: string
  audience: ProductAudience | string
  buttonText: string | null
  buttonUrl: string | null
  status: 'DRAFT' | 'QUEUED' | 'SENT' | 'FAILED' | string
  rawRecipientCount: number
  eligibleRecipientCount: number
  sentCount: number
  skippedBlockedCount: number
  skippedPreferenceCount: number
  failedCount: number
  createdAt: string
  sentAt: string | null
}

export interface ReleaseNoteAdminDto {
  id: number
  title: string
  body: string
  buttonText: string | null
  buttonUrl: string | null
  audience: ProductAudience | string
  minAppVersion: string | null
  active: boolean
  publishedAt: string
  createdAt: string
}

export interface ProductAnalyticsSummaryDto {
  totalUsers: number
  botBlockedUsers: number
  botBlockedPercent: number
  startToFirstAction24h: number
  startToFirstTeam7d: number
  actionNoTeamUsers: number
  actionNoTeamTeamSubmit7d: number
  checklistCompletedUsers: number
  checklistCompletedPercent: number
}

export interface ProductCampaignAnalyticsDto {
  campaignId: number
  title: string
  audience: string
  sentCount: number
  openedCount: number
  clickedCount: number
  actedCount: number
}

export interface ReleaseNoteAnalyticsDto {
  releaseNoteId: number
  title: string
  audience: string
  seenCount: number
  featureUsedCount: number
}

/** Admin user list row; cardsInSeries is null when no tournament+series filter is applied. */
export interface AdminUserListItemDto {
  id: number
  telegramId: number
  username: string | null
  displayName: string | null
  fantiki: number
  botBlocked: boolean
  cardsInSeries: number | null
}

export interface ActiveSeriesBriefDto {
  id: number
  name: string
  status: SeriesStatus
}

export interface StreamLinkDto {
  label: string | null
  url: string
}

export interface TournamentDto {
  id: number
  name: string
  description: string | null
  status: TournamentStatus
  kind: TournamentKind
  polemicaCompetitionId: number | null
  defaultExpectedGameCount: number | null
  createdAt: string
  streamLinks: StreamLinkDto[]
  /** Non-finished series; set on list tournaments. Omitted on single-tournament responses. */
  activeSeries?: ActiveSeriesBriefDto[]
}

export interface TournamentPlayerDto {
  id: number
  tournamentId: number
  fantasyPlayerId: number
  polemicaUserId: number
  nickname: string
  photoUrl: string | null
  /** If true, player is not included in random pack draws for this tournament. */
  excludedFromPackPool: boolean
}

export interface FantasyPlayerAdminDto {
  id: number
  polemicaUserId: number
  nickname: string
  photoUrl: string | null
  aliases: FantasyPlayerAliasDto[]
  tournamentIds: number[]
  tournamentCount: number
  cardTemplateCount: number
}

export interface FantasyPlayerAliasDto {
  id: number
  polemicaUserId: number
  primary: boolean
}

export interface FantasyPlayerMergeIssueDto {
  code: string
  severity: 'BLOCKER' | 'WARNING' | string
  message: string
  count: number
}

export interface FantasyPlayerMergePreviewDto {
  source: FantasyPlayerAdminDto
  target: FantasyPlayerAdminDto
  sourceAliases: number[]
  targetAliases: number[]
  blockers: FantasyPlayerMergeIssueDto[]
  warnings: FantasyPlayerMergeIssueDto[]
  canMerge: boolean
}

export interface FantasyPlayerMergeResultDto {
  auditId: number
  target: FantasyPlayerAdminDto
}

export interface TournamentDetailDto extends TournamentDto {
  players: TournamentPlayerDto[]
}

export interface SeriesDto {
  id: number
  tournamentId: number
  name: string
  publicNumber: number
  namePrefix: string | null
  gameNumFrom: number | null
  gameNumTo: number | null
  /** POLEMICA_COMPETITION filter: 0/1/2, null = all phases. */
  gamePhase: number | null
  /** STANDALONE filter by game.started calendar day, format YYYY-MM-DD; null = all days. */
  gameStartedOn: string | null
  status: SeriesStatus
  startsAt: string
  teamDeadline: string
  expectedGameCount: number | null
  finalized: boolean
  streamLinks: StreamLinkDto[]
  /** Games synced from Polemica (`series_game` rows). */
  syncedGamesCount: number
  /** Games with calculated fantasy scores (`scored`). */
  scoredGamesCount: number
  /** tournament_player.id assigned to this series */
  tournamentPlayerIds: number[]
  /** Optional replacement Polemica user id by tournament_player.id. */
  replacementPolemicaUserIds: Record<number, number>
}

export interface SeriesCompletionPreviewDto {
  ready: boolean
  reason: string | null
}

export interface ResultMafiaOverrideDto {
  id: number
  seriesId: number
  importItemId: number
  gameNumber: number
  originalMafiaLine: string
  correctedMafiaLine: string
  reason: string
  adminActor: string
  createdAt: string
}

export interface AdminSeriesGameDto {
  id: number
  polemicaGameId: number
  displayName: string
  gameName: string
  gameNum: number | null
  table: number | null
  phase: number | null
  playedAt: string | null
  finished: boolean
  scored: boolean
}

export type SeriesResultsPointsStatus =
  | 'AVAILABLE'
  | 'PARTIAL'
  | 'UNFINISHED'
  | 'CACHE_MISSING'
  | 'CACHE_INVALID'
  | 'LOAD_FAILED'
  | 'EMPTY'

export interface SeriesResultsGameDto {
  seriesGameId: number
  polemicaGameId: number
  columnLabel: string
  gameNum: number | null
  table: number | null
  phase: number | null
  playedAt: string | null
  finished: boolean
  pointsStatus: SeriesResultsPointsStatus
}

export interface SeriesResultsCellDto {
  seriesGameId: number
  participated: boolean
  points: number | null
}

export interface SeriesResultsPlayerDto {
  playerKey: string
  polemicaUserId: number | null
  nickname: string
  cells: SeriesResultsCellDto[]
  totalPoints: number
  gamesPlayed: number
  complete: boolean
}

export interface SeriesResultsDto {
  seriesId: number
  tournamentKind: TournamentKind
  games: SeriesResultsGameDto[]
  players: SeriesResultsPlayerDto[]
  warnings: string[]
}

export interface SeriesPlayerMarketplaceUnlistResultDto {
  tournamentPlayerId: number
  fantasyPlayerId: number
  playerNickname: string
  cancelledListings: number
}

export interface EconomyConfigItemDto {
  key: string
  value: string
  description: string | null
}

export interface UpdateEconomyConfigRequest {
  value: string
}

export interface BulkUpdateEconomyConfigRequest {
  items: { key: string; value: string }[]
}

export interface SeriesFinalizationResultDto {
  rewardsDistributed: number
  cardsDecremented: number
}

export interface StartedSeriesEntryDto {
  seriesId: number
  name: string
  tournamentName: string
  previousStatus: string
}

export interface SkippedSeriesEntryDto {
  seriesId: number
  reason: string
}

export interface BatchStartSeriesResponseDto {
  startedSeries: StartedSeriesEntryDto[]
  skipped: SkippedSeriesEntryDto[]
  notificationRecipientCount: number
}

export interface CardTemplatePerkDto {
  id: number
  perkId: string
  perkName: string
  bonusPoints: number
}

export interface CardTemplateDto {
  id: number
  fantasyPlayerId: number
  rarity: Rarity
  imageUrl: string | null
  description: string | null
  perks: CardTemplatePerkDto[]
}

export interface CardPackRarityConfigResponseDto {
  id: number
  rarity: Rarity
  cardsCount: number
}

export interface CardSkinDto {
  id: number
  code: string
  name: string
}

export type CardPackOpeningMode = 'INSTANT' | 'CHOOSE'

export interface CardPackDto {
  id: number
  name: string
  tournamentId: number
  active: boolean
  autoGenerated: boolean
  openingMode: CardPackOpeningMode
  priceFantiki: number
  freeOpensPerUser: number
  maxOpensPerUser: number
  perkIds: string[]
  useAllTournamentPlayers: boolean
  playerIds: number[]
  rarityConfigs: CardPackRarityConfigResponseDto[]
  skinId: number | null
  skinCode: string | null
}

export interface UserCardDto {
  id: number
  telegramUserId: number
  cardTemplateId: number
  acquiredAt: string
  sourceCardPackId: number | null
}

export interface OpenPackResultDto {
  userCards: UserCardDto[]
}

export interface PairAnalysisDto {
  userATelegramId: number
  userBTelegramId: number
  tradesAtoB: number
  tradesTotalAtoB: number
  tradesBtoA: number
  tradesTotalBtoA: number
  netTransfer: number
  bidirectional: boolean
  cleared: boolean
  clearedAt: string | null
  clearedNote: string | null
}

export interface PairTradeDto {
  listingId: number
  price: number
  sellerReceived: number
  createdAt: string
  soldAt: string | null
  sellerTelegramId: number
  buyerTelegramId: number
  userCardId: number
  playerName: string
  rarity: Rarity
  currentOwnerTelegramId: number
  /** False if the card was resold: not deleted at pair ban; seller net is still recovered. */
  buyerStillOwnsCard: boolean
  complaintsCount: number
}

export interface PairTradesUserBriefDto {
  username: string | null
  telegramId: number
  displayName: string
  fantiki: number
}

export interface PairTradesResultDto {
  userA: PairTradesUserBriefDto
  userB: PairTradesUserBriefDto
  trades: PairTradeDto[]
  totalTrades: number
  totalGrossFantiki: number
  totalSellerReceived: number
}

export interface BanPairConfiscatedCardDto {
  userCardId: number
  playerName: string
  rarity: Rarity
}

export interface BanPairUserResultDto {
  telegramId: number
  displayName: string
  fantikiConfiscated: number
  newBalance: number
  cardsConfiscated: BanPairConfiscatedCardDto[]
  listingsCancelled: number
}

export interface BanPairResultDto {
  userA: BanPairUserResultDto
  userB: BanPairUserResultDto
  reason: string
}

export interface BanPairPreviewUserDto {
  telegramId: number
  displayName: string
  balance: number
  fantikiToConfiscate: number
  balanceAfter: number
  cardsToConfiscate: BanPairConfiscatedCardDto[]
}

export interface BanPairPreviewDto {
  userA: BanPairPreviewUserDto
  userB: BanPairPreviewUserDto
}

export interface PairSanctionHistoryItemDto {
  id: number
  createdAt: string
  reason: string
  userLowTelegramId: number
  userHighTelegramId: number
  userLowDisplayName: string
  userHighDisplayName: string
  fantikiTakenLow: number
  fantikiTakenHigh: number
  cardsCountLow: number
  cardsCountHigh: number
}

export interface PagedPairSanctionHistoryDto {
  content: PairSanctionHistoryItemDto[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TransactionParticipantDto {
  telegramId: number
  displayName: string
}

export interface ComplainedTransactionDto {
  listingId: number
  playerName: string
  rarity: Rarity
  price: number
  createdAt: string
  soldAt: string
  seller: TransactionParticipantDto
  buyer: TransactionParticipantDto
  complaintsCount: number
  sanctioned: boolean
}

export interface PagedComplainedTransactionsDto {
  content: ComplainedTransactionDto[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

export interface TransactionComplaintDetailDto {
  userId: number
  displayName: string
  telegramId: number
  complainedAt: string
}

export interface ConcurrentListingDto {
  listingId: number
  sellerDisplayName: string
  sellerTelegramId: number
  price: number
  createdAt: string
  soldAt: string | null
  active: boolean
  sameTemplate: boolean
}

export interface TransactionMarketContextDto {
  listingCreatedAt: string
  concurrentSameTemplate: ConcurrentListingDto[]
  concurrentSamePlayerRarity: ConcurrentListingDto[]
}

export interface TransactionComplaintsListDto {
  complaints: TransactionComplaintDetailDto[]
  marketContext: TransactionMarketContextDto
}

export interface BanDurationRequest {
  days: number
}

export interface SanctionTransactionRequest {
  reason: string
  sellerFine: number
  buyerFine: number
  complainantReward: number
  banSeller: BanDurationRequest | null
  banBuyer: BanDurationRequest | null
}

export interface SanctionTransactionResultDto {
  listingId: number
  sellerFined: number
  sellerNewBalance: number
  sellerBannedUntil: string | null
  buyerFined: number
  buyerNewBalance: number
  buyerBannedUntil: string | null
  complainantsRewarded: number
  totalRewardPaid: number
}

export interface UserByComplaintsDto {
  telegramId: number
  displayName: string
  totalComplaints: number
  transactionsWithComplaints: number
  avgComplaintsPerTransaction: number
  sanctionedTransactions: number
  marketplaceBanned: boolean
  marketplaceBannedUntil: string | null
}

export interface PagedUsersByComplaintsDto {
  content: UserByComplaintsDto[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

export interface BanUserRequest {
  days: number | null
}

export type CardMergeOperation = 'COMMON_TO_RARE' | 'RARE_TO_EPIC' | string

export interface AdminCardMergeListItemDto {
  id: number
  telegramUserId: number
  telegramUserDisplayName?: string | null
  previewId?: number | null
  resultUserCardId: number
  operation: CardMergeOperation
  sourceRarity: Rarity | string
  resultRarity: Rarity | string
  fantasyPlayerId: number
  fantasyPlayerNickname: string
  selectedPerkIds: string[]
  offeredPerkIds?: string[] | null
  costFantiki: number
  createdAt: string
}

export interface AdminCardMergeInputDto {
  inputUserCardId: number
  inputCardTemplateId: number
  inputRarity: Rarity | string
  inputPerkIds: string[]
  inputUsesRemaining: number
  inputTimesRenewed: number
  inputSkinCode?: string | null
}

export interface AdminCardMergeDetailDto extends AdminCardMergeListItemDto {
  inputs: AdminCardMergeInputDto[]
}

export interface AdminCardMergePageDto {
  content: AdminCardMergeListItemDto[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
