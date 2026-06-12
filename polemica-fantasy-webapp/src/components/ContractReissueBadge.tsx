interface ContractReissueBadgeProps {
  timesRenewed: number
  maxRenewals: number
  layout?: 'default' | 'collection'
}

export function ContractReissueBadge({ timesRenewed, maxRenewals, layout = 'default' }: ContractReissueBadgeProps) {
  const capped = Math.max(0, timesRenewed)
  const max = Math.max(0, maxRenewals)
  const layoutClass = layout === 'collection' ? ' pf-contract-badge--collection' : ''
  return (
    <span
      className={`pf-contract-badge${layoutClass}${capped <= 0 ? ' pf-contract-badge--fresh' : ''}${max > 0 && capped >= max ? ' pf-contract-badge--limit' : ''}`}
      title="Перезаключения контракта"
      aria-label={`Перезаключений контракта: ${capped} из ${max}`}
    >
      ↻ {capped}/{max}
    </span>
  )
}
