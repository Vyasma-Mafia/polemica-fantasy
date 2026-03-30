import type { UserCardItem } from '../api/types'

type Achievement = UserCardItem['achievements'][number]

export function CardAchievementChips({
  achievements,
  max = 6,
  className = '',
}: {
  achievements: Achievement[]
  max?: number
  /** e.g. pf-card-ach-chips--compact for small thumbnails */
  className?: string
}) {
  if (achievements.length === 0) return null
  const list = achievements.slice(0, max)
  const cn = ['pf-card-ach-chips', className].filter(Boolean).join(' ')
  return (
    <ul className={cn} aria-label="Достижения">
      {list.map((a) => (
        <li key={a.achievementId} className="pf-card-ach-chip">
          <span className="pf-card-ach-chip__name">{a.achievementName}</span>
          <span className="pf-card-ach-chip__bonus">+{a.bonusPoints}</span>
        </li>
      ))}
    </ul>
  )
}
