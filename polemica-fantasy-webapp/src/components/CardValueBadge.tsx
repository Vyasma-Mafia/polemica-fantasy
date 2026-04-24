import type { ReactNode } from 'react'

type CardValueBadgeLayout = 'collection' | 'playerStack' | 'team'

const LAYOUT_MOD: Record<CardValueBadgeLayout, string> = {
  collection: 'pf-card-value-badge--layout-collection',
  'playerStack': 'pf-card-value-badge--layout-player-stack',
  team: 'pf-card-value-badge--layout-team',
}

type Props = {
  value: number
  layout: CardValueBadgeLayout
  /** When the card is expired, shift down so the badge does not sit under the expired label. */
  expired?: boolean
  /** Team grid: when the card is "dead" (no uses), leave room for the dead label. */
  dead?: boolean
  className?: string
  children?: ReactNode
}

export function CardValueBadge({ value, layout, expired, dead, className, children }: Props) {
  const mod = LAYOUT_MOD[layout]
  const state = (expired || dead ? ' pf-card-value-badge--nudge-down' : '') + (className ? ` ${className}` : '')
  return (
    <span
      className={`pf-card-value-badge ${mod}${state}`}
      title="Ценность карты"
    >
      {children ?? `₱${value}`}
    </span>
  )
}

export function cardValuePeso(n: number): string {
  return `₱${n}`
}
