import type { Rarity } from './types'

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
  achievementId: string
  bonusPoints?: number | null
}

export interface GiveCardsRequest {
  cardTemplateIds: number[]
}
