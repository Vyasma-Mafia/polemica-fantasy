import type { Rarity } from '../api/types'

/** Множитель очков в фэнтези (совпадает с backend `Rarity.scoreModifier`). */
export const RARITY_SCORE_MODIFIER: Record<Rarity, number> = {
  COMMON: 1.0,
  RARE: 1.1,
  EPIC: 1.15,
  LEGENDARY: 1.25,
}

export function rarityScoreModifierLabel(r: Rarity): string {
  const m = RARITY_SCORE_MODIFIER[r]
  return `×${m.toFixed(2)}`
}

/** CSS modifier suffix (lowercase rarity). */
export function rarityClass(r: Rarity | undefined): string {
  if (!r) return 'common'
  return r.toLowerCase()
}

/** Для сортировки: больше = реже; легендарные выше обычных. */
export const RARITY_SORT_ORDER: Record<Rarity, number> = {
  COMMON: 0,
  RARE: 1,
  EPIC: 2,
  LEGENDARY: 3,
}

export function compareRarityDesc(a: Rarity, b: Rarity): number {
  return RARITY_SORT_ORDER[b] - RARITY_SORT_ORDER[a]
}

export const RARITY_UI: { value: Rarity | ''; label: string }[] = [
  { value: '', label: 'Все' },
  { value: 'COMMON', label: 'Обычная' },
  { value: 'RARE', label: 'Редкая' },
  { value: 'EPIC', label: 'Эпик' },
  { value: 'LEGENDARY', label: 'Легенда' },
]
