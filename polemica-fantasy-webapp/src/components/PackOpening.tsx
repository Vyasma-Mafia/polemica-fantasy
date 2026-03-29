import { useEffect, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import type { UserCardItem } from '../api/types'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { rarityClass } from '../lib/rarity'

const PACK_PHASE_MS = 1200
const CARD_STAGGER_MS = 700

type Phase = 'pack' | 'reveal' | 'summary'

export interface PackOpeningProps {
  cards: UserCardItem[]
  packName?: string
  /** «В коллекцию» — закрыть overlay (родитель сбрасывает state / ведёт на /cards). */
  onDismiss: () => void
}

/**
 * Полноэкранная анимация открытия пака: тряска → поочерёдное раскрытие карт → сводка.
 * При `cards.length === 0` ничего не рендерится.
 */
export function PackOpening({ cards, packName, onDismiss }: PackOpeningProps) {
  const [phase, setPhase] = useState<Phase>('pack')
  const [visibleIndex, setVisibleIndex] = useState(0)
  const revealRunId = useRef(0)

  useEffect(() => {
    if (cards.length === 0) return undefined
    setPhase('pack')
    setVisibleIndex(0)
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
          <ul className="pf-pack-open__summary-grid">
            {cards.map((c) => {
              const img = cardDisplayImageUrl(c)
              const rc = rarityClass(c.rarity)
              return (
                <li key={c.id} className={`pf-pack-open__summary-card pf-pack-open__summary-card--${rc}`}>
                  {img ? (
                    <img src={img} alt="" className="pf-pack-open__summary-card-img" />
                  ) : (
                    <div className="pf-pack-open__summary-card-ph">{c.rarity}</div>
                  )}
                  <span className="pf-pack-open__summary-card-name">{c.playerNickname}</span>
                </li>
              )
            })}
          </ul>
          <button type="button" className="pf-btn pf-pack-open__cta" onClick={onDismiss}>
            В коллекцию
          </button>
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
    <>
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
      <div className="pf-pack-open__card-inner">
        {img ? (
          <img src={img} alt="" className="pf-pack-open__card-img" />
        ) : (
          <div className="pf-pack-open__card-ph">{card.rarity}</div>
        )}
        <span className="pf-pack-open__card-name">{card.playerNickname}</span>
      </div>
    </>
  )

  return (
    <div className={`pf-pack-open__card-wrap pf-pack-open__card-wrap--${rc} pf-pack-open__card-wrap--enter`}>
      {rarity === 'COMMON' ? inner : <div className="pf-pack-open__flip">{inner}</div>}
    </div>
  )
}
