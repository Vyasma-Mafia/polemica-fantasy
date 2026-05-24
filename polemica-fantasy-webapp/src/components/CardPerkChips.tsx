import type { UserCardItem } from '../api/types'

type Perk = UserCardItem['perks'][number]

export function CardPerkChips({
  perks,
  max = 6,
  className = '',
}: {
  perks: Perk[]
  max?: number
  /** e.g. pf-card-perk-chips--compact for small thumbnails */
  className?: string
}) {
  if (perks.length === 0) return null
  const list = perks.slice(0, max)
  const cn = ['pf-card-perk-chips', className].filter(Boolean).join(' ')
  return (
    <ul className={cn} aria-label="Перки">
      {list.map((a) => (
        <li key={a.perkId} className="pf-card-perk-chip">
          <span className="pf-card-perk-chip__name">{a.perkName}</span>
          <span className="pf-card-perk-chip__bonus">+{a.bonusPoints}</span>
        </li>
      ))}
    </ul>
  )
}
