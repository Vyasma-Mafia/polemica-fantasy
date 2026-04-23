import { Fragment } from 'react'
import type { Rarity, UserCardItem } from '../api/types'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { collectionCardRootClass, miniCardClass } from '../lib/cardFrameClasses'
import { pickBestUserCard } from '../lib/collectionByPlayer'
import { compareRarityDesc, rarityScoreModifierLabel } from '../lib/rarity'
import { CardAchievementChips } from './CardAchievementChips'

function maxUsesForCard(c: UserCardItem, usesPerRarity: Record<Rarity, number> | undefined): number {
  if (!usesPerRarity) return Math.max(c.usesRemaining, 1)
  return usesPerRarity[c.rarity] ?? c.usesRemaining
}

function stackLayerCount(n: number): 0 | 1 | 2 {
  if (n <= 1) return 0
  if (n === 2) return 1
  return 2
}

function sortCardsForStrip(cards: UserCardItem[]): UserCardItem[] {
  return [...cards].sort((a, b) => {
    const r = compareRarityDesc(a.rarity, b.rarity)
    if (r !== 0) return r
    return a.id - b.id
  })
}

type PlayerCardEmptyProps = {
  mode: 'empty'
  fantasyPlayerId: number
  nickname: string
  photoUrl: string | null
}

type PlayerCardWithProps = {
  mode: 'with'
  fantasyPlayerId: number
  nickname: string
  /** Для согласованности с фото на плейсхолдере пустой группы */
  photoUrl: string | null
  cards: UserCardItem[]
  expanded: boolean
  onToggle: () => void
  onOpenCard: (userCardId: number) => void
  usesPerRarity: Record<Rarity, number> | undefined
}

export type PlayerCardProps = PlayerCardEmptyProps | PlayerCardWithProps

/**
 * Ячейка сетки «по игрокам»: стопка с лучшей картой или плейсхолдер без карт;
 * при mode=with — строка-аккордеон со скроллом миниатюр.
 */
export function PlayerCard(props: PlayerCardProps) {
  if (props.mode === 'empty') {
    return (
      <li className="pf-player-cell pf-player-cell--empty">
        <div className="pf-player-cell__empty-card">
          {props.photoUrl ? (
            <img src={props.photoUrl} alt="" className="pf-player-cell__empty-img" />
          ) : (
            <div className="pf-player-cell__empty-ph" aria-hidden>
              <span className="pf-player-cell__empty-ico" />
            </div>
          )}
        </div>
        <div className="pf-player-cell__cap">
          <span className="pf-player-cell__name">{props.nickname}</span>
          <span className="pf-player-cell__hint pf-muted">Нет карт</span>
        </div>
      </li>
    )
  }

  const { cards, expanded, onToggle, onOpenCard, usesPerRarity, nickname } = props
  const best = pickBestUserCard(cards)
  const n = cards.length
  const layers = stackLayerCount(n)
  const imgSrc = cardDisplayImageUrl(best)
  const expired = best.usesRemaining <= 0
  const maxU = maxUsesForCard(best, usesPerRarity)
  const strip = sortCardsForStrip(cards)

  return (
    <Fragment>
      <li
        className={`pf-player-cell ${collectionCardRootClass(best, { expired })}`}
        onClick={onToggle}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            onToggle()
          }
        }}
        role="button"
        tabIndex={0}
      >
        <div className="pf-player-cell__stack">
          {layers >= 2 && <div className="pf-player-cell__stack-layer pf-player-cell__stack-layer--2" aria-hidden />}
          {layers >= 1 && <div className="pf-player-cell__stack-layer pf-player-cell__stack-layer--1" aria-hidden />}
          <div className="pf-collection-card__frame">
            <div className="pf-collection-card__open">
              {imgSrc ? (
                <img src={imgSrc} alt="" className="pf-collection-card__img" />
              ) : (
                <div className="pf-collection-card__ph">{best.rarity}</div>
              )}
              <span className="pf-player-card-count" title="Карт в коллекции">
                ×{n}
              </span>
              <span className="pf-uses-badge" title="Осталось использований (лучшая карта)">
                ⚡{best.usesRemaining}/{maxU}
              </span>
              {expired && <span className="pf-expired-badge">Истекла</span>}
              <div className="pf-collection-card__cap">
                <span className="pf-collection-card__name">{nickname}</span>
                <span className="pf-collection-card__rarity">
                  {best.rarity}{' '}
                  <span className="pf-rarity-mod" title="Множитель очков в фэнтези">
                    {rarityScoreModifierLabel(best.rarity)}
                  </span>
                </span>
                <CardAchievementChips achievements={best.achievements} max={4} />
              </div>
            </div>
          </div>
        </div>
      </li>
      {expanded && (
        <li className="pf-player-cell-expand" style={{ gridColumn: '1 / -1' }}>
          <div className="pf-player-cell-expand__scroller" role="region" aria-label="Карты игрока">
            {strip.map((c) => {
              const s = cardDisplayImageUrl(c)
              const ex = c.usesRemaining <= 0
              return (
                <button
                  type="button"
                  key={c.id}
                  className={`${miniCardClass(c)} pf-player-cell-expand__mini`}
                  onClick={() => onOpenCard(c.id)}
                >
                  {s ? (
                    <img src={s} alt="" />
                  ) : (
                    <div className="pf-mini-card__ph" />
                  )}
                  {ex && <span className="pf-expired-badge">Истекла</span>}
                </button>
              )
            })}
          </div>
        </li>
      )}
    </Fragment>
  )
}
