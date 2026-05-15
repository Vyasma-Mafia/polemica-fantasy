import type { UserCardItem } from '../api/types'
import { rarityClass } from './rarity'

export function skinClass(skinCode: string | null | undefined): string {
  if (!skinCode) return ''
  return `--skin-${skinCode}`
}

/** Root class for collection grid `<li>`. */
export function collectionCardRootClass(c: UserCardItem, opts?: { expired?: boolean }): string {
  let cls = `pf-collection-card pf-collection-card--${rarityClass(c.rarity)}`
  const skinMod = skinClass(c.skinCode)
  if (skinMod) cls += ` pf-collection-card${skinMod}`
  if (opts?.expired) cls += ' pf-collection-card--expired'
  if (c.rarity === 'LEGENDARY' && c.craftedByTelegramUserId != null) {
    cls += ' pf-collection-card--legendary-crafted'
  }
  return cls
}

export function teamCardRootClass(
  c: UserCardItem,
  extra: string,
): string {
  let cls = `pf-team-card pf-team-card--${rarityClass(c.rarity)}`
  const skinMod = skinClass(c.skinCode)
  if (skinMod) cls += ` pf-team-card${skinMod}`
  if (c.rarity === 'LEGENDARY' && c.craftedByTelegramUserId != null) {
    cls += ' pf-team-card--legendary-crafted'
  }
  if (extra) cls += ` ${extra}`
  return cls
}

export function modalImgFrameClass(c: UserCardItem): string {
  let cls = `pf-modal__img-frame pf-modal__img-frame--${rarityClass(c.rarity)}`
  const skinMod = skinClass(c.skinCode)
  if (skinMod) cls += ` pf-modal__img-frame${skinMod}`
  if (c.rarity === 'LEGENDARY' && c.craftedByTelegramUserId != null) {
    cls += ' pf-modal__img-frame--legendary-crafted'
  }
  return cls
}

export function miniCardClass(c: UserCardItem): string {
  let cls = `pf-mini-card pf-mini-card--${rarityClass(c.rarity)}`
  const skinMod = skinClass(c.skinCode)
  if (skinMod) cls += ` pf-mini-card${skinMod}`
  if (c.rarity === 'LEGENDARY' && c.craftedByTelegramUserId != null) {
    cls += ' pf-mini-card--legendary-crafted'
  }
  return cls
}
