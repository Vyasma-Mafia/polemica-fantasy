import { apiGet } from './client'
import type { PlayerProfile } from './types'

export async function fetchPlayerProfile(telegramId: number, initData: string): Promise<PlayerProfile> {
  return apiGet<PlayerProfile>(`/api/v1/players/${telegramId}/profile`, initData)
}
