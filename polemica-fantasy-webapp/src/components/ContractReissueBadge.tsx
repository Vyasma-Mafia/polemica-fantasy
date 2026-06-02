interface ContractReissueBadgeProps {
  timesRenewed: number
  maxRenewals: number
}

export function ContractReissueBadge({ timesRenewed, maxRenewals }: ContractReissueBadgeProps) {
  const capped = Math.max(0, timesRenewed)
  const max = Math.max(0, maxRenewals)
  return (
    <span
      className={`pf-contract-badge${capped <= 0 ? ' pf-contract-badge--fresh' : ''}${max > 0 && capped >= max ? ' pf-contract-badge--limit' : ''}`}
      title="Перезаключения контракта"
      aria-label={`Перезаключений контракта: ${capped} из ${max}`}
    >
      ↻ {capped}/{max}
    </span>
  )
}
