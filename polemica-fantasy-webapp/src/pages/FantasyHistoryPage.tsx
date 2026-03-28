import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { FantasyTeamDto, UserCardItem, UserTournamentDetail } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/InitDataContext'
import { rarityClass } from '../lib/rarity'

export function FantasyHistoryPage() {
  const { tournamentId } = useParams<{ tournamentId: string }>()
  const id = Number(tournamentId)
  const initData = useInitData()
  const [openId, setOpenId] = useState<number | null>(null)
  const [detailCardId, setDetailCardId] = useState<number | null>(null)

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

  if (!initData) return <p className="pf-muted">Нужен initData.</p>
  if (tq.isLoading || teamsQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (tq.isError) return <p className="pf-err">{(tq.error as Error).message}</p>
  if (teamsQ.isError) return <p className="pf-err">{(teamsQ.error as Error).message}</p>

  const t = tq.data!
  const back = `/tournaments/${t.id}`

  const detailCard = detailCardId != null ? cardById.get(detailCardId) : undefined

  return (
    <div className="pf-page">
      <PageHeader title="История фэнтези" subtitle={t.name} backTo={back} />

      <section className="pf-history">
        {teamsInTournament.map((team) => {
          const open = openId === team.seriesId
          const total = team.totalScore
          return (
            <div key={team.seriesId} className="pf-acc">
              <button
                type="button"
                className="pf-acc__head"
                onClick={() => setOpenId(open ? null : team.seriesId)}
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
                  <div className="pf-carousel" role="list">
                    {team.slots.map((slot) => {
                      const card = cardById.get(slot.userCardId)
                      return (
                        <button
                          key={slot.slot}
                          type="button"
                          className={`pf-fantasy-card pf-fantasy-card--${rarityClass(card?.rarity)}`}
                          onClick={() => setDetailCardId(slot.userCardId)}
                          role="listitem"
                        >
                          {card?.imageUrl ? (
                            <img src={card.imageUrl} alt="" className="pf-fantasy-card__img" />
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
          onClick={() => setDetailCardId(null)}
        >
          <div className="pf-modal" onClick={(e) => e.stopPropagation()}>
            <button type="button" className="pf-modal__close" onClick={() => setDetailCardId(null)}>
              ×
            </button>
            {detailCard.imageUrl && <img src={detailCard.imageUrl} alt="" className="pf-modal__img" />}
            <h3 className="pf-modal__title">{detailCard.playerNickname}</h3>
            <p className="pf-muted">{detailCard.rarity}</p>
            <ul className="pf-modal__ach">
              {detailCard.achievements.map((a) => (
                <li key={a.achievementType}>
                  {a.achievementType}: +{a.bonusPoints}
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      <p className="pf-footer-link">
        <Link to={back}>← К турниру</Link>
      </p>
    </div>
  )
}
