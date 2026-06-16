import { useMemo, useRef, useState } from 'react'
import type { PackChoice, PackChoiceCard } from '../api/types'
import { skinClass } from '../lib/cardFrameClasses'
import { rarityClass } from '../lib/rarity'
import { CardPerkChips } from './CardPerkChips'
import { PlayerImage } from './PlayerImage'

interface PackChoiceOverlayProps {
  choice: PackChoice
  isSubmitting: boolean
  error: string | null
  onSelect: (optionId: string) => void
  onDismiss: () => void
}

export function PackChoiceOverlay({ choice, isSubmitting, error, onSelect, onDismiss }: PackChoiceOverlayProps) {
  const [activeIndex, setActiveIndex] = useState(0)
  const viewportRef = useRef<HTMLDivElement | null>(null)
  const activeOption = choice.options[activeIndex] ?? choice.options[0]

  const activeLabel = useMemo(
    () => `Набор ${Math.min(activeIndex + 1, choice.options.length)} из ${choice.options.length}`,
    [activeIndex, choice.options.length],
  )

  function scrollToIndex(index: number) {
    const next = Math.max(0, Math.min(index, choice.options.length - 1))
    setActiveIndex(next)
    const viewport = viewportRef.current
    const slide = viewport?.querySelector<HTMLElement>(`[data-pack-choice-index="${next}"]`)
    slide?.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
  }

  function handleScroll() {
    const viewport = viewportRef.current
    if (!viewport) return
    const center = viewport.scrollLeft + viewport.clientWidth / 2
    let bestIndex = activeIndex
    let bestDistance = Number.POSITIVE_INFINITY
    viewport.querySelectorAll<HTMLElement>('[data-pack-choice-index]').forEach((node) => {
      const nodeCenter = node.offsetLeft + node.offsetWidth / 2
      const distance = Math.abs(nodeCenter - center)
      if (distance < bestDistance) {
        bestDistance = distance
        bestIndex = Number(node.dataset.packChoiceIndex ?? 0)
      }
    })
    if (bestIndex !== activeIndex) setActiveIndex(bestIndex)
  }

  if (!activeOption) return null

  return (
    <div className="pf-pack-choice" role="dialog" aria-modal aria-labelledby="pf-pack-choice-title">
      <div className="pf-pack-choice__backdrop" />
      <section className="pf-pack-choice__panel">
        <div className="pf-pack-choice__head">
          <div>
            <p className="pf-pack-choice__eyebrow">{choice.packName}</p>
            <h3 id="pf-pack-choice-title">Выберите набор</h3>
          </div>
          <button type="button" className="pf-pack-choice__close" aria-label="Выбрать позже" onClick={onDismiss}>
            ×
          </button>
        </div>
        <p className="pf-pack-choice__copy">
          Один из трёх наборов попадёт в коллекцию. Остальные исчезнут.
        </p>
        <div className="pf-pack-choice__nav" aria-label="Навигация по наборам">
          <button type="button" onClick={() => scrollToIndex(activeIndex - 1)} disabled={activeIndex === 0}>
            ‹
          </button>
          <span>{activeLabel}</span>
          <button
            type="button"
            onClick={() => scrollToIndex(activeIndex + 1)}
            disabled={activeIndex >= choice.options.length - 1}
          >
            ›
          </button>
        </div>
        <div className="pf-pack-choice__viewport" ref={viewportRef} onScroll={handleScroll}>
          <div className="pf-pack-choice__track">
            {choice.options.map((option, index) => (
              <article
                key={option.optionId}
                className={`pf-pack-choice__slide${index === activeIndex ? ' is-active' : ''}`}
                data-pack-choice-index={index}
                role="group"
                aria-label={`Набор ${index + 1} из ${choice.options.length}`}
              >
                <div className="pf-pack-choice__slide-head">
                  <strong>Набор {index + 1}</strong>
                  <span>{option.cards.length} карт</span>
                </div>
                <ul className="pf-pack-choice__grid">
                  {option.cards.map((card, cardIndex) => (
                    <PackChoicePreviewCard key={`${option.optionId}-${card.fantasyPlayerId}-${cardIndex}`} card={card} />
                  ))}
                </ul>
              </article>
            ))}
          </div>
        </div>
        <div className="pf-pack-choice__dots" aria-label="Выбор набора">
          {choice.options.map((option, index) => (
            <button
              type="button"
              key={option.optionId}
              className={index === activeIndex ? 'is-active' : ''}
              aria-label={`Показать набор ${index + 1}`}
              aria-current={index === activeIndex ? 'true' : undefined}
              onClick={() => scrollToIndex(index)}
            />
          ))}
        </div>
        {error && <p className="pf-err pf-pack-choice__error">{error}</p>}
        <div className="pf-pack-choice__actions">
          <button type="button" className="pf-btn pf-btn--ghost" onClick={onDismiss} disabled={isSubmitting}>
            Выбрать позже
          </button>
          <button
            type="button"
            className="pf-btn pf-pack-choice__cta"
            onClick={() => onSelect(activeOption.optionId)}
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Добавляем…' : `Выбрать набор ${activeIndex + 1}`}
          </button>
        </div>
      </section>
    </div>
  )
}

function PackChoicePreviewCard({ card }: { card: PackChoiceCard }) {
  const rc = rarityClass(card.rarity)
  const skinMod = skinClass(card.skinCode)
  return (
    <li className={`pf-pack-choice-card pf-pack-open__summary-card pf-pack-open__summary-card--${rc}${skinMod ? ` pf-pack-open__summary-card${skinMod}` : ''}`}>
      <div className="pf-pack-open__summary-card-frame">
        <PlayerImage
          src={card.playerPhotoUrl}
          seedId={card.fantasyPlayerId}
          variant="card"
          className="pf-pack-open__summary-card-img"
        />
        <div className="pf-pack-open__summary-card-cap">
          <span className="pf-pack-open__summary-card-name">{card.playerName}</span>
          <span className="pf-pack-open__summary-card-rarity">{card.rarity}</span>
          <CardPerkChips perks={card.perks} max={2} className="pf-card-perk-chips--compact pf-card-perk-chips--tight" />
        </div>
      </div>
    </li>
  )
}
