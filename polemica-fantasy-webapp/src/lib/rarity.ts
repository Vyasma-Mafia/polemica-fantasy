import type { Rarity } from '../api/types'

/** CSS modifier suffix (lowercase rarity). */
export function rarityClass(r: Rarity | undefined): string {
  if (!r) return 'common'
  return r.toLowerCase()
}

export const RARITY_UI: { value: Rarity | ''; label: string }[] = [
  { value: '', label: 'Все' },
  { value: 'COMMON', label: 'Обычная' },
  { value: 'RARE', label: 'Редкая' },
  { value: 'EPIC', label: 'Эпик' },
  { value: 'LEGENDARY', label: 'Легенда' },
]
