import type { PerkCatalogItem, Rarity } from '../api/types'
import type { PeriodicRatingRewardPlayer } from '../api/periodicRatings'
import { rarityScoreModifierLabel } from '../lib/rarity'
import { CardPerkChips } from './CardPerkChips'
import { PlayerImage } from './PlayerImage'

type Props = {
  player: PeriodicRatingRewardPlayer | null
  rarity: Rarity
  skinCode: string
  perks: PerkCatalogItem[]
  editionTier: string
  serial: string
  rank: number
}

export function TrophyCardPreview({ player, rarity, skinCode, perks, editionTier, serial, rank }: Props) {
  const previewPerks = perks.map((perk) => ({
    perkId: perk.id,
    perkName: perk.name,
    bonusPoints: perk.bonusPoints,
  }))
  return (
    <article className={`pf-trophy-card pf-trophy-card--${rarity.toLowerCase()} pf-trophy-card--skin-${skinCode}`}>
      <div className="pf-trophy-card__edition">{editionTier} · место #{rank}</div>
      <div className="pf-trophy-card__media">
        <PlayerImage
          src={player?.photoUrl}
          seedId={player?.id ?? rank}
          variant="card"
          className="pf-trophy-card__image"
          alt={player?.nickname ?? 'Предпросмотр игрока'}
        />
        <span className="pf-trophy-card__serial">{serial}</span>
      </div>
      <div className="pf-trophy-card__cap">
        <strong>{player?.nickname ?? 'Выберите игрока'}</strong>
        <span>{rarity} {rarityScoreModifierLabel(rarity)}</span>
        <CardPerkChips perks={previewPerks} max={2} />
      </div>
    </article>
  )
}
