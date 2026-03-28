import type { AchievementType, Rarity } from './types'

export interface CreateCardTemplateRequest {
  fantasyPlayerId: number
  rarity: Rarity
  description?: string | null
}

export interface UpdateCardTemplateRequest {
  rarity?: Rarity | null
  description?: string | null
}

export interface AddCardTemplateAchievementRequest {
  achievementType: AchievementType
  bonusPoints: number
}

export interface GiveCardsRequest {
  cardTemplateIds: number[]
}
