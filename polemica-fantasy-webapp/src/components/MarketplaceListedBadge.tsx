import type { UserCardItem } from '../api/types'

type Props = {
  listing: NonNullable<UserCardItem['activeMarketplaceListing']>
  /** collection: on card overlay; team: team grid; inline: modal / text flow (no absolute positioning) */
  layout?: 'collection' | 'team' | 'inline'
}

export function MarketplaceListedBadge({ listing, layout = 'collection' }: Props) {
  const title = 'В продаже на маркетплейсе'
  if (layout === 'inline') {
    return (
      <span className="pf-marketplace-listed-badge pf-marketplace-listed-badge--inline" title={title}>
        В продаже {listing.price}₣
      </span>
    )
  }
  return (
    <span
      className={`pf-marketplace-listed-badge ${layout === 'team' ? 'pf-marketplace-listed-badge--team' : ''}`}
      title={title}
    >
      В продаже {listing.price}₣
    </span>
  )
}
