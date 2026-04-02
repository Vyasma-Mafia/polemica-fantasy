import type { CardGameBreakdown } from '../api/types'

export function ScoreBreakdownBlock({ b }: { b: CardGameBreakdown }) {
  const base = b.basePoints
  const ach = b.achievementBonus
  const mod = b.rarityModifier
  const total = b.totalScore
  return (
    <div className="pf-score-breakdown">
      <div className="pf-score-breakdown__row">
        <span>База</span>
        <strong>{base != null ? base.toFixed(2) : '—'}</strong>
      </div>
      <div className="pf-score-breakdown__row">
        <span>Достижения</span>
        <strong>{ach != null ? ach.toFixed(2) : '—'}</strong>
      </div>
      {b.achievements.length > 0 && (
        <ul className="pf-modal__ach" style={{ marginTop: 6 }}>
          {b.achievements.map((a) => (
            <li key={`${a.achievementId}-${a.bonusPoints}`}>
              {a.achievementName}: +{a.bonusPoints.toFixed(2)}
            </li>
          ))}
        </ul>
      )}
      <div className="pf-score-breakdown__row">
        <span>× редкость</span>
        <strong>{mod != null ? mod.toFixed(2) : '—'}</strong>
      </div>
      <div className="pf-score-breakdown__row">
        <span>Итого</span>
        <strong>{total != null ? total.toFixed(2) : '—'}</strong>
      </div>
    </div>
  )
}
