import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type {
  CardGameBreakdown,
  FantasyTeamDetailSlot,
  FantasyTeamSeriesDetails,
  PublicFantasyTeam,
  UserCardItem,
  UserSeriesDetail,
} from '../api/types'
import { CardAchievementChips } from '../components/CardAchievementChips'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { rarityClass } from '../lib/rarity'
import { formatUserDisplayName } from '../lib/userDisplayName'

function highlightMaxes(columns: FantasyTeamDetailSlot[], gameCount: number) {
  const nC = columns.length
  const rowMax = new Array(gameCount).fill(Number.NEGATIVE_INFINITY)
  const colMax = new Array(nC).fill(Number.NEGATIVE_INFINITY)
  for (let gi = 0; gi < gameCount; gi++) {
    for (let ci = 0; ci < nC; ci++) {
      const v = columns[ci].cells[gi]?.totalScore
      if (v != null && Number.isFinite(v)) {
        if (v > rowMax[gi]) rowMax[gi] = v
        if (v > colMax[ci]) colMax[ci] = v
      }
    }
  }
  return { rowMax, colMax }
}

function BreakdownBlock({ b }: { b: CardGameBreakdown }) {
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

export function LeaderboardPlayerTeamPage() {
  const { seriesId, telegramId } = useParams<{ seriesId: string; telegramId: string }>()
  const sid = Number(seriesId)
  const initData = useInitData()
  const [detailCardId, setDetailCardId] = useState<number | null>(null)
  const [expandedCell, setExpandedCell] = useState<{ gameIndex: number; colIndex: number } | null>(null)

  const seriesMeta = useQuery({
    queryKey: ['series', sid, initData],
    queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${sid}`, initData),
    enabled: !!initData && Number.isFinite(sid),
  })

  const teamQ = useQuery({
    queryKey: ['public-fantasy-team', sid, telegramId, initData],
    queryFn: () =>
      apiGet<PublicFantasyTeam>(`/api/v1/series/${sid}/users/${telegramId}/fantasy-team`, initData),
    enabled: !!initData && Number.isFinite(sid) && !!telegramId,
  })

  const detailsQ = useQuery({
    queryKey: ['public-fantasy-team-details', sid, telegramId, initData],
    queryFn: () =>
      apiGet<FantasyTeamSeriesDetails>(
        `/api/v1/series/${sid}/users/${telegramId}/fantasy-team/details`,
        initData,
      ),
    enabled: !!initData && Number.isFinite(sid) && !!telegramId && !!teamQ.data,
  })

  const cardByUserCardId = useMemo(() => {
    const m = new Map<number, UserCardItem>()
    for (const s of teamQ.data?.slots ?? []) {
      m.set(s.card.id, s.card)
    }
    return m
  }, [teamQ.data])

  const matrixHighlights = useMemo(() => {
    const d = detailsQ.data
    if (!d || d.games.length === 0 || d.columns.length === 0) return null
    return highlightMaxes(d.columns, d.games.length)
  }, [detailsQ.data])

  const sortedSlots = useMemo(() => {
    const slots = teamQ.data?.slots ?? []
    return [...slots].sort((a, b) => a.slot - b.slot)
  }, [teamQ.data])

  if (!initData) return <p className="pf-muted">Нужен initData.</p>
  if (seriesMeta.isLoading || teamQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (seriesMeta.isError) return <p className="pf-err">{(seriesMeta.error as Error).message}</p>
  if (teamQ.isError) {
    const msg = (teamQ.error as Error).message
    return (
      <div className="pf-page">
        <PageHeader title="Команда" subtitle={seriesMeta.data?.name} backTo={`/series/${sid}/leaderboard`} />
        <p className="pf-err">{msg}</p>
        <p className="pf-footer-link">
          <Link to={`/series/${sid}/leaderboard`}>← К лидерборду</Link>
        </p>
      </div>
    )
  }

  const team = teamQ.data!
  const ownerLabel = formatUserDisplayName(team.owner)
  const s = seriesMeta.data
  const backLb = `/series/${sid}/leaderboard`

  const detailCard = detailCardId != null ? cardByUserCardId.get(detailCardId) : undefined
  const detailImgSrc = detailCard ? cardDisplayImageUrl(detailCard) : null

  const modalColumn =
    detailCardId != null && detailsQ.data ? detailsQ.data.columns.find((c) => c.userCardId === detailCardId) : undefined

  const d = detailsQ.data
  const showMatrix = d && d.games.length > 0 && matrixHighlights

  return (
    <div className="pf-page">
      <PageHeader title={ownerLabel} subtitle={s?.name ?? `Серия #${sid}`} backTo={backLb} />

      <section className="pf-history">
        <div className="pf-acc">
          <div className="pf-acc__body" style={{ paddingTop: 0 }}>
            <div className="pf-history-points">
              <span className="pf-history-points__label">Всего очков</span>
              <span className="pf-history-points__value">
                {team.totalScore != null ? team.totalScore.toFixed(2) : '—'}
              </span>
            </div>

            {detailsQ.isLoading && <p className="pf-muted">Загрузка детализации…</p>}
            {detailsQ.isError && <p className="pf-err">{(detailsQ.error as Error).message}</p>}

            {showMatrix && d && matrixHighlights && (
              <div className="pf-score-matrix-wrap">
                <table className="pf-score-matrix">
                  <thead>
                    <tr>
                      <th className="pf-score-matrix__corner">Игра</th>
                      {d.columns.map((col) => {
                        const card = cardByUserCardId.get(col.userCardId)
                        return (
                          <th key={col.slot} className="pf-score-matrix__head-card">
                            {card?.playerNickname ?? `Слот ${col.slot}`}
                          </th>
                        )
                      })}
                    </tr>
                  </thead>
                  <tbody>
                    {d.games.map((g, gi) => (
                      <tr key={g.seriesGameId}>
                        <th scope="row" className="pf-score-matrix__game">
                          {g.gameName}
                          {!g.scored && <span className="pf-score-matrix__cell-hint"> (не засчитана)</span>}
                        </th>
                        {d.columns.map((col, ci) => {
                          const cell = col.cells[gi]
                          const v = cell?.totalScore
                          const isBest =
                            v != null &&
                            Number.isFinite(v) &&
                            (v === matrixHighlights.rowMax[gi] || v === matrixHighlights.colMax[ci]) &&
                            matrixHighlights.rowMax[gi] > Number.NEGATIVE_INFINITY
                          const exp =
                            expandedCell?.gameIndex === gi && expandedCell?.colIndex === ci && cell != null
                          return (
                            <td key={col.slot} className="pf-score-matrix__cell-wrap" style={{ padding: 0 }}>
                              <button
                                type="button"
                                className={`pf-score-matrix__cell ${isBest ? 'pf-score-matrix__cell--best' : ''}`}
                                onClick={() => {
                                  if (!cell) return
                                  setExpandedCell(exp ? null : { gameIndex: gi, colIndex: ci })
                                }}
                                disabled={!cell}
                              >
                                <span className="pf-score-matrix__cell-inner">
                                  {v != null ? v.toFixed(2) : '—'}
                                </span>
                                {cell && (
                                  <span className="pf-score-matrix__cell-hint">
                                    {expandedCell?.gameIndex === gi && expandedCell?.colIndex === ci ? '▼' : '▶'}
                                  </span>
                                )}
                              </button>
                              {exp && cell && (
                                <div style={{ padding: 8 }}>
                                  <BreakdownBlock b={cell} />
                                </div>
                              )}
                            </td>
                          )
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <div className="pf-carousel" role="list">
              {sortedSlots.map((slot) => {
                const card = slot.card
                const imgSrc = cardDisplayImageUrl(card)
                return (
                  <button
                    key={slot.slot}
                    type="button"
                    className={`pf-fantasy-card pf-fantasy-card--${rarityClass(card.rarity)}`}
                    onClick={() => setDetailCardId(card.id)}
                    role="listitem"
                  >
                    {imgSrc ? (
                      <img src={imgSrc} alt="" className="pf-fantasy-card__img" />
                    ) : (
                      <div className="pf-fantasy-card__ph">{card.rarity}</div>
                    )}
                    <div className="pf-fantasy-card__cap">
                      <span className="pf-fantasy-card__name">{card.playerNickname}</span>
                      <span className="pf-fantasy-card__pts">
                        {slot.score != null ? `${slot.score.toFixed(2)} pts` : '—'}
                      </span>
                    </div>
                    <CardAchievementChips achievements={card.achievements} max={4} className="pf-card-ach-chips--tight" />
                    <span className="pf-fantasy-card__hint">Нажмите для деталей</span>
                  </button>
                )
              })}
            </div>
          </div>
        </div>
      </section>

      {detailCard && (
        <div
          className="pf-modal-backdrop"
          role="dialog"
          aria-modal
          aria-label="Карточка"
          onClick={() => setDetailCardId(null)}
        >
          <div className="pf-modal" onClick={(e) => e.stopPropagation()}>
            <button type="button" className="pf-modal__close" onClick={() => setDetailCardId(null)}>
              ×
            </button>
            {detailImgSrc && (
              <div className={`pf-modal__img-frame pf-modal__img-frame--${rarityClass(detailCard.rarity)}`}>
                <img src={detailImgSrc} alt="" className="pf-modal__img" />
              </div>
            )}
            <h3 className="pf-modal__title">{detailCard.playerNickname}</h3>
            <p className="pf-muted">{detailCard.rarity}</p>
            <ul className="pf-modal__ach">
              {detailCard.achievements.map((a) => (
                <li key={a.achievementId}>
                  {a.achievementName}: +{a.bonusPoints}
                </li>
              ))}
            </ul>

            {detailsQ.isLoading && <p className="pf-muted">Загрузка по играм…</p>}
            {detailsQ.isError && <p className="pf-err">{(detailsQ.error as Error).message}</p>}
            {modalColumn && detailsQ.data && detailsQ.data.games.length > 0 && (
              <div className="pf-modal__per-game">
                <h4>По играм серии</h4>
                <ul className="pf-modal__ach" style={{ listStyle: 'none', paddingLeft: 0 }}>
                  {detailsQ.data.games.map((g, gi) => {
                    const cell = modalColumn.cells[gi]
                    return (
                      <li
                        key={g.seriesGameId}
                        style={{ marginBottom: 10, borderBottom: '1px solid var(--pf-border)', paddingBottom: 8 }}
                      >
                        <strong>{g.gameName}</strong>
                        {!g.scored && <span className="pf-muted"> — не засчитана</span>}
                        {cell ? (
                          <>
                            <div style={{ marginTop: 6 }}>
                              <span className="pf-muted">Очки: </span>
                              <strong>{cell.totalScore != null ? cell.totalScore.toFixed(2) : '—'}</strong>
                            </div>
                            <BreakdownBlock b={cell} />
                          </>
                        ) : (
                          <p className="pf-muted" style={{ marginTop: 4 }}>
                            Нет данных
                          </p>
                        )}
                      </li>
                    )
                  })}
                </ul>
              </div>
            )}
          </div>
        </div>
      )}

      <p className="pf-footer-link">
        <Link to={backLb}>← К лидерборду</Link>
      </p>
    </div>
  )
}
