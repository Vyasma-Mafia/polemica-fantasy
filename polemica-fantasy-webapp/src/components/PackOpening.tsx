import { useEffect, useMemo, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import type { UserCardItem } from '../api/types'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { CardAchievementChips } from './CardAchievementChips'
import { rarityClass, rarityScoreModifierLabel } from '../lib/rarity'

const PACK_PHASE_MS = 1200
const CARD_STAGGER_MS = 1050

type Phase = 'pack' | 'reveal' | 'summary'

export interface PackOpeningProps {
  cards: UserCardItem[]
  packName?: string
  /** «В коллекцию» — закрыть overlay (родитель сбрасывает state / ведёт на /cards). */
  onDismiss: () => void
  /** «Купить ещё» — закрыть overlay и снова предложить покупку того же пака (например, открыть модалку в магазине). */
  onBuyMore?: () => void
}

/**
 * Полноэкранная анимация открытия пака: тряска → поочерёдное раскрытие карт → сводка.
 * При `cards.length === 0` ничего не рендерится.
 */
export function PackOpening({ cards, packName, onDismiss, onBuyMore }: PackOpeningProps) {
  const [phase, setPhase] = useState<Phase>('pack')
  const [visibleIndex, setVisibleIndex] = useState(0)
  const revealRunId = useRef(0)

  useEffect(() => {
    if (cards.length === 0) return undefined
    queueMicrotask(() => {
      setPhase('pack')
      setVisibleIndex(0)
    })
    const t = setTimeout(() => {
      setPhase('reveal')
      setVisibleIndex(0)
    }, PACK_PHASE_MS)
    return () => clearTimeout(t)
  }, [cards])

  useEffect(() => {
    if (phase !== 'reveal' || cards.length === 0) return undefined

    revealRunId.current += 1
    const run = revealRunId.current
    const ids: ReturnType<typeof setTimeout>[] = []

    for (let i = 1; i < cards.length; i++) {
      const idx = i
      ids.push(
        setTimeout(() => {
          if (revealRunId.current === run) setVisibleIndex(idx)
        }, CARD_STAGGER_MS * idx),
      )
    }

    ids.push(
      setTimeout(() => {
        if (revealRunId.current === run) setPhase('summary')
      }, CARD_STAGGER_MS * cards.length),
    )

    return () => {
      ids.forEach(clearTimeout)
    }
  }, [phase, cards.length])

  const totalPulledValue = useMemo(
    () =>
      cards.length === 0
        ? 0
        : cards.reduce((sum, x) => sum + (Number.isFinite(x.value) ? x.value : 0), 0),
    [cards],
  )

  if (cards.length === 0) {
    return null
  }

  const card = cards[visibleIndex]

  return (
    <div
      className="pf-pack-open"
      role="dialog"
      aria-modal
      aria-labelledby="pf-pack-open-title"
      aria-live="polite"
    >
      <div className="pf-pack-open__backdrop" />

      {phase === 'pack' && (
        <div className="pf-pack-open__pack-stage">
          <p id="pf-pack-open-title" className="pf-pack-open__pack-label">
            {packName ?? 'Пак'}
          </p>
          <div className="pf-pack-open__pack pf-pack-open__pack--shake">
            <span className="pf-pack-open__pack-icon" aria-hidden>
              🎴
            </span>
            <span className="pf-pack-open__pack-tear" aria-hidden />
          </div>
        </div>
      )}

      {phase === 'reveal' && card && (
        <div className="pf-pack-open__reveal-stage">
          <p className="pf-pack-open__reveal-hint">{packName ?? 'Пак открыт'}</p>
          <PackOpeningCardReveal key={card.id} card={card} />
        </div>
      )}

      {phase === 'summary' && (
        <div className="pf-pack-open__summary">
          <h3 className="pf-pack-open__summary-title">Вы получили</h3>
          <p className="pf-pack-open__summary-total-value">Суммарная ценность: {totalPulledValue}₱</p>
          <ul className="pf-pack-open__summary-grid">
            {cards.map((c) => {
              const img = cardDisplayImageUrl(c)
              const rc = rarityClass(c.rarity)
              return (
                <li key={c.id} className={`pf-pack-open__summary-card pf-pack-open__summary-card--${rc}`}>
                  <div className="pf-pack-open__summary-card-frame">
                    {img ? (
                      <img src={img} alt="" className="pf-pack-open__summary-card-img" />
                    ) : (
                      <div className="pf-pack-open__summary-card-ph">{c.rarity}</div>
                    )}
                    <div className="pf-pack-open__summary-card-cap">
                      <span className="pf-pack-open__summary-card-name">{c.playerNickname}</span>
                      <span className="pf-pack-open__summary-card-rarity">
                        {c.rarity}{' '}
                        <span className="pf-rarity-mod">{rarityScoreModifierLabel(c.rarity)}</span>
                      </span>
                      <CardAchievementChips achievements={c.achievements} className="pf-card-ach-chips--compact" />
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
          <div className="pf-pack-open__actions">
            {onBuyMore && (
              <button type="button" className="pf-btn pf-btn--ghost pf-pack-open__cta" onClick={onBuyMore}>
                Купить ещё
              </button>
            )}
            <button type="button" className="pf-btn pf-pack-open__cta" onClick={onDismiss}>
              В коллекцию
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function PackOpeningCardReveal({ card }: { card: UserCardItem }) {
  const img = cardDisplayImageUrl(card)
  const rc = rarityClass(card.rarity)
  const rarity = card.rarity

  const particleCount = rarity === 'LEGENDARY' ? 14 : rarity === 'EPIC' ? 10 : 0

  const inner = (
    <div className="pf-pack-open__card-inner">
      {img ? (
        <img src={img} alt="" className="pf-pack-open__card-img" />
      ) : (
        <div className="pf-pack-open__card-ph">{card.rarity}</div>
      )}
      {particleCount > 0 ? (
        <span className="pf-pack-open__particles" aria-hidden>
          {Array.from({ length: particleCount }).map((_, i) => (
            <span
              key={i}
              className="pf-pack-open__particle"
              style={{ '--i': i } as CSSProperties}
            />
          ))}
        </span>
      ) : null}
      <div className="pf-pack-open__card-cap">
        <span className="pf-pack-open__card-name">{card.playerNickname}</span>
        <span className="pf-pack-open__card-rarity">
          {card.rarity}{' '}
          <span className="pf-rarity-mod">{rarityScoreModifierLabel(card.rarity)}</span>
        </span>
        <CardAchievementChips achievements={card.achievements} />
      </div>
    </div>
  )

  return (
    <div className={`pf-pack-open__card-wrap pf-pack-open__card-wrap--${rc} pf-pack-open__card-wrap--enter`}>
      {rarity === 'COMMON' ? inner : <div className="pf-pack-open__flip">{inner}</div>}
    </div>
  )
}
