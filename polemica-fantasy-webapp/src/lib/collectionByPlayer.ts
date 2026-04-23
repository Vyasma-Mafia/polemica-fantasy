import type { UserCardItem } from '../api/types'
import { compareRarityDesc } from './rarity'

export function pickBestUserCard(cards: UserCardItem[]): UserCardItem {
  if (cards.length === 0) {
    throw new Error('pickBestUserCard: empty list')
  }
  return [...cards].sort((a, b) => {
    const r = compareRarityDesc(a.rarity, b.rarity)
    if (r !== 0) return r
    return a.id - b.id
  })[0]!
}

export function groupCardsByPlayerId(cards: UserCardItem[]): Map<number, UserCardItem[]> {
  const m = new Map<number, UserCardItem[]>()
  for (const c of cards) {
    const id = c.fantasyPlayerId
    if (!m.has(id)) m.set(id, [])
    m.get(id)!.push(c)
  }
  return m
}
