import type { CardGameBreakdown } from '../api/types'

export function ScoreBreakdownBlock({ b }: { b: CardGameBreakdown }) {
  const base = b.basePoints
  const perk = b.perkBonus
  const mod = b.rarityModifier
  const total = b.totalScore
  return (
    <div className="pf-score-breakdown">
      <div className="pf-score-breakdown__row">
        <span>База</span>
        <strong>{base != null ? base.toFixed(2) : '—'}</strong>
      </div>
      {b.scoredViaReplacement && (
        <div className="pf-score-breakdown__row">
          <span>Замена</span>
          <strong>{b.scoredPlayerName || b.scoredPolemicaUserId || '—'}</strong>
        </div>
      )}
      <div className="pf-score-breakdown__row">
        <span>Перки</span>
        <strong>{perk != null ? perk.toFixed(2) : '—'}</strong>
      </div>
      {b.perks.length > 0 && (
        <ul className="pf-modal__ach" style={{ marginTop: 6 }}>
          {b.perks.map((a) => (
            <li key={`${a.perkId}-${a.bonusPoints}`}>
              {a.perkName}: +{a.bonusPoints.toFixed(2)}
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
