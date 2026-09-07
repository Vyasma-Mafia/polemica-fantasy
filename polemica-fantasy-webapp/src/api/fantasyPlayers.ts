import { apiGet } from './client'
import type { FantasyPlayerIdentity } from './types'

/** Resolve a card/choose-option's Fantasy player ID to the primary Polemica identity. */
export function fetchFantasyPlayer(fantasyPlayerId: number, initData: string | undefined) {
  if (!Number.isSafeInteger(fantasyPlayerId) || fantasyPlayerId < 1) {
    throw new Error('fantasyPlayerId must be a positive integer')
  }
  return apiGet<FantasyPlayerIdentity>(`/api/v1/players/${fantasyPlayerId}`, initData)
}
