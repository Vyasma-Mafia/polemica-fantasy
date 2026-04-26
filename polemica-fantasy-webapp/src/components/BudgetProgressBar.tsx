type BudgetProgressBarProps = {
  currentValue: number
  maxValue: number
}

export function BudgetProgressBar({ currentValue, maxValue }: BudgetProgressBarProps) {
  const ratio = maxValue > 0 ? currentValue / maxValue : 0
  const percentage = Math.max(0, Math.min(100, ratio * 100))
  const fillClass =
    ratio > 1 ? 'pf-budget-bar__fill--over' : ratio >= 0.8 ? 'pf-budget-bar__fill--warning' : ''

  return (
    <div className="pf-budget-bar-wrap" aria-live="polite">
      <div className="pf-budget-bar-wrap__label">
        <span>Бюджет</span>
        <strong>
          {currentValue} / {maxValue}₱
        </strong>
      </div>
      <div className="pf-budget-bar" role="progressbar" aria-valuemin={0} aria-valuemax={maxValue} aria-valuenow={currentValue}>
        <div className={`pf-budget-bar__fill ${fillClass}`.trim()} style={{ width: `${percentage}%` }} />
      </div>
    </div>
  )
}
