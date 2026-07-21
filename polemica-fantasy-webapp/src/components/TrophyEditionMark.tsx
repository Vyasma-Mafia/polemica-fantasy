import type { UserCardItem } from '../api/types'

type TrophyProvenance = NonNullable<UserCardItem['trophyProvenance']>

export function TrophyEditionMark({ trophy }: { trophy: TrophyProvenance }) {
  return (
    <span className="pf-trophy-edition-mark" title={`Трофей рейтинга · ${trophy.serial}`}>
      <span className="pf-trophy-edition-mark__medal" aria-label={`${trophy.rank} место`}>
        <i aria-hidden>✦</i>
        <b>#{trophy.rank}</b>
      </span>
      <span className="pf-trophy-edition-mark__copy">
        <strong>Трофей рейтинга</strong>
        <small>{trophy.periodTitle}</small>
      </span>
    </span>
  )
}
