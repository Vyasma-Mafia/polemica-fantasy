import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type {
  FantasyTeamDetailSlot,
  FantasyTeamDto,
  FantasyTeamSeriesDetails,
  UserCardItem,
  UserTournamentDetail,
} from '../api/types'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { ScoreBreakdownBlock } from '../components/ScoreBreakdownBlock'
import { useInitData } from '../context/useInitData'
import { modalImgFrameClass } from '../lib/cardFrameClasses'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { rarityClass } from '../lib/rarity'

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

export function FantasyHistoryPage() {
  const { tournamentId } = useParams<{ tournamentId: string }>()
  const id = Number(tournamentId)
  const initData = useInitData()
  const [openId, setOpenId] = useState<number | null>(null)
  const [detailCardId, setDetailCardId] = useState<number | null>(null)
  const [detailSeriesId, setDetailSeriesId] = useState<number | null>(null)
  const [expandedCell, setExpandedCell] = useState<{ gameIndex: number; colIndex: number } | null>(null)

  const tq = useQuery({
    queryKey: ['tournament', id, initData],
    queryFn: () => apiGet<UserTournamentDetail>(`/api/v1/tournaments/${id}`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  const teamsQ = useQuery({
    queryKey: ['fantasy-teams', initData],
    queryFn: () => apiGet<FantasyTeamDto[]>(`/api/v1/me/fantasy-teams`, initData),
    enabled: !!initData,
  })

  const cardsQ = useQuery({
    queryKey: ['cards', 'all', initData],
    queryFn: () => apiGet<UserCardItem[]>(`/api/v1/me/cards`, initData),
    enabled: !!initData,
  })

  const detailsQ = useQuery({
    queryKey: ['fantasy-team-details', openId, initData],
    queryFn: () => apiGet<FantasyTeamSeriesDetails>(`/api/v1/me/fantasy-teams/${openId}/details`, initData),
    enabled: !!initData && openId != null,
  })

  const detailsModalQ = useQuery({
    queryKey: ['fantasy-team-details', detailSeriesId, initData],
    queryFn: () => apiGet<FantasyTeamSeriesDetails>(`/api/v1/me/fantasy-teams/${detailSeriesId}/details`, initData),
    enabled: !!initData && detailSeriesId != null && detailCardId != null,
  })

  const teamsInTournament = useMemo(() => {
    const teams = teamsQ.data ?? []
    return teams.filter((t) => t.tournamentId === id)
  }, [teamsQ.data, id])

  const cardById = useMemo(() => {
    const m = new Map<number, UserCardItem>()
    for (const c of cardsQ.data ?? []) m.set(c.id, c)
    return m
  }, [cardsQ.data])

  const seriesName = (sid: number) => tq.data?.series.find((s) => s.id === sid)?.name ?? `Серия #${sid}`

  const matrixHighlights = useMemo(() => {
    const d = detailsQ.data
    if (!d || d.games.length === 0 || d.columns.length === 0) return null
    return highlightMaxes(d.columns, d.games.length)
  }, [detailsQ.data])

  if (!initData) return <MissingInitDataNotice />
  if (tq.isLoading || teamsQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (tq.isError) return <p className="pf-err">{(tq.error as Error).message}</p>
  if (teamsQ.isError) return <p className="pf-err">{(teamsQ.error as Error).message}</p>

  const t = tq.data!
  const back = `/tournaments/${t.id}`

  const detailCard = detailCardId != null ? cardById.get(detailCardId) : undefined
  const detailImgSrc = detailCard ? cardDisplayImageUrl(detailCard) : null

  const modalColumn =
    detailCardId != null && detailsModalQ.data
      ? detailsModalQ.data.columns.find((c) => c.userCardId === detailCardId)
      : undefined

  return (
    <div className="pf-page">
      <PageHeader title="История фэнтези" subtitle={t.name} backTo={back} />

      <section className="pf-history">
        {teamsInTournament.map((team) => {
          const open = openId === team.seriesId
          const total = team.totalScore
          const d = open ? detailsQ.data : undefined
          const showMatrix = open && d && d.games.length > 0
          return (
            <div key={team.seriesId} className="pf-acc">
              <button
                type="button"
                className="pf-acc__head"
                onClick={() => {
                  setOpenId(open ? null : team.seriesId)
                  setExpandedCell(null)
                }}
                aria-expanded={open}
              >
                <span>{seriesName(team.seriesId)}</span>
                <span className="pf-acc__chevron">{open ? '▲' : '▼'}</span>
              </button>
              {open && (
                <div className="pf-acc__body">
                  <div className="pf-history-points">
                    <span className="pf-history-points__label">Всего очков</span>
                    <span className="pf-history-points__value">{total != null ? total.toFixed(2) : '—'}</span>
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
                              const card = cardById.get(col.userCardId)
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
                                        <ScoreBreakdownBlock b={cell} />
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
                    {team.slots.map((slot) => {
                      const card = cardById.get(slot.userCardId)
                      const imgSrc = card ? cardDisplayImageUrl(card) : null
                      return (
                        <button
                          key={slot.slot}
                          type="button"
                          className={`pf-fantasy-card pf-fantasy-card--${rarityClass(card?.rarity)}`}
                          onClick={() => {
                            setDetailSeriesId(team.seriesId)
                            setDetailCardId(slot.userCardId)
                          }}
                          role="listitem"
                        >
                          {imgSrc ? (
                            <img src={imgSrc} alt="" className="pf-fantasy-card__img" />
                          ) : (
                            <div className="pf-fantasy-card__ph">{card?.rarity ?? '—'}</div>
                          )}
                          <div className="pf-fantasy-card__cap">
                            <span className="pf-fantasy-card__name">{card?.playerNickname ?? `#${slot.userCardId}`}</span>
                            <span className="pf-fantasy-card__pts">
                              {slot.score != null ? `${slot.score.toFixed(2)} pts` : '—'}
                            </span>
                          </div>
                          <span className="pf-fantasy-card__hint">Нажмите для деталей</span>
                        </button>
                      )
                    })}
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </section>

      {teamsInTournament.length === 0 && <p className="pf-muted">Вы ещё не выставляли команды в этом турнире.</p>}

      {detailCard && (
        <div
          className="pf-modal-backdrop"
          role="dialog"
          aria-modal
          aria-label="Карточка"
          onClick={() => {
            setDetailCardId(null)
            setDetailSeriesId(null)
          }}
        >
          <div className="pf-modal" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              className="pf-modal__close"
              onClick={() => {
                setDetailCardId(null)
                setDetailSeriesId(null)
              }}
            >
              ×
            </button>
            {detailImgSrc && detailCard && (
              <div className={modalImgFrameClass(detailCard)}>
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

            {detailsModalQ.isLoading && <p className="pf-muted">Загрузка по играм…</p>}
            {detailsModalQ.isError && <p className="pf-err">{(detailsModalQ.error as Error).message}</p>}
            {modalColumn && detailsModalQ.data && detailsModalQ.data.games.length > 0 && (
              <div className="pf-modal__per-game">
                <h4>По играм серии</h4>
                <ul className="pf-modal__ach" style={{ listStyle: 'none', paddingLeft: 0 }}>
                  {detailsModalQ.data.games.map((g, gi) => {
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
                            <ScoreBreakdownBlock b={cell} />
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
        <Link to={back}>← К турниру</Link>
      </p>
    </div>
  )
}
