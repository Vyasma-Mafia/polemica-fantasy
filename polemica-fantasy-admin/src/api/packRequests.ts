import type { Rarity } from './types'

export interface CardPackRarityConfigDto {
  rarity: Rarity
  probability: number
  cardsCount: number
}

export interface CreateCardPackRequest {
  name: string
  tournamentId: number
  active?: boolean
  rarityConfigs: CardPackRarityConfigDto[]
}

export interface UpdateCardPackRequest {
  name?: string | null
  active?: boolean | null
  rarityConfigs?: CardPackRarityConfigDto[] | null
}
