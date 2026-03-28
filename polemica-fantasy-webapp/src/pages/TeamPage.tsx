import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiGet, apiSend, ApiError } from '../api/client'
import type { FantasyTeamDto, UserCardItem, UserSeriesDetail } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/InitDataContext'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { rarityClass } from '../lib/rarity'

export function TeamPage() {
  const { seriesId } = useParams<{ seriesId: string }>()
  const sid = Number(seriesId)
  const initData = useInitData()
  const qc = useQueryClient()

  const seriesQ = useQuery({
    queryKey: ['series', sid, initData],
    queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${sid}`, initData),
    enabled: !!initData && Number.isFinite(sid),
  })

  const cardsQ = useQuery({
    queryKey: ['cards', 'team', seriesQ.data?.tournamentId, initData],
    queryFn: () =>
      apiGet<UserCardItem[]>(
        `/api/v1/me/cards?tournamentId=${seriesQ.data!.tournamentId}`,
        initData,
      ),
    enabled: !!initData && !!seriesQ.data?.tournamentId,
  })

  const teamQ = useQuery({
    queryKey: ['fantasy-team', sid, initData],
    queryFn: async () => {
      try {
        return await apiGet<FantasyTeamDto>(`/api/v1/me/fantasy-teams/${sid}`, initData)
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) return null
        throw e
      }
    },
    enabled: !!initData && Number.isFinite(sid),
    retry: false,
  })

  const [selected, setSelected] = useState<number[]>([])
  const toggle = (id: number) => {
    setSelected((prev) => {
      if (prev.includes(id)) return prev.filter((x) => x !== id)
      if (prev.length >= 3) return prev
      return [...prev, id]
    })
  }

  const submit = useMutation({
    mutationFn: async () => {
      const body = { userCardIds: selected }
      const existing = teamQ.data
      if (existing != null) {
        return apiSend<FantasyTeamDto>('PUT', `/api/v1/series/${sid}/fantasy-team`, initData, body)
      }
      return apiSend<FantasyTeamDto>('POST', `/api/v1/series/${sid}/fantasy-team`, initData, body)
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['fantasy-team', sid] })
      void qc.invalidateQueries({ queryKey: ['fantasy-teams'] })
    },
  })

  const deadlinePassed = useMemo(() => {
    if (!seriesQ.data) return false
    return Date.now() > new Date(seriesQ.data.teamDeadline).getTime()
  }, [seriesQ.data])

  const cardById = useMemo(() => {
    const m = new Map<number, UserCardItem>()
    for (const c of cardsQ.data ?? []) m.set(c.id, c)
    return m
  }, [cardsQ.data])

  if (!initData) return <p className="pf-muted">Нужен initData.</p>
  if (seriesQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (seriesQ.isError) return <p className="pf-err">{(seriesQ.error as Error).message}</p>

  const s = seriesQ.data!
  const cards = cardsQ.data ?? []
  const errMsg = submit.error instanceof ApiError ? submit.error.message : (submit.error as Error)?.message
  const back = `/tournaments/${s.tournamentId}/series`

  return (
    <div className="pf-page">
      <PageHeader title="Сборка команды" subtitle={s.name} backTo={back} />

      {deadlinePassed && <p className="pf-err">Дедлайн сбора команды прошёл.</p>}
      {teamQ.isSuccess && teamQ.data && (
        <p className="pf-muted">
          Команда отправлена ({teamQ.data.slots.length} карт). Можно обновить до дедлайна.
        </p>
      )}
      {teamQ.isSuccess && !teamQ.data && <p className="pf-muted">Выберите три карты для серии.</p>}

      <p className="pf-instruction">Выберите ровно 3 карты (порядок — слоты 1–3)</p>

      <ol className="pf-picked-slots">
        {[0, 1, 2].map((i) => {
          const id = selected[i]
          const c = id != null ? cardById.get(id) : undefined
          const pickedSrc = c ? cardDisplayImageUrl(c) : null
          return (
            <li key={i} className="pf-picked-slots__slot">
              <span className="pf-picked-slots__num">{i + 1}</span>
              {c ? (
                <div className={`pf-mini-card pf-mini-card--${rarityClass(c.rarity)}`}>
                  {pickedSrc ? <img src={pickedSrc} alt="" /> : <div className="pf-mini-card__ph" />}
                  <span>{c.playerNickname}</span>
                </div>
              ) : (
                <span className="pf-muted">—</span>
              )}
            </li>
          )
        })}
      </ol>

      <ul className="pf-team-grid">
        {cards.map((c) => {
          const imgSrc = cardDisplayImageUrl(c)
          return (
          <li key={c.id}>
            <button
              type="button"
              className={`pf-team-card pf-team-card--${rarityClass(c.rarity)} ${selected.includes(c.id) ? 'pf-team-card--picked' : ''}`}
              onClick={() => toggle(c.id)}
              disabled={deadlinePassed}
            >
              {imgSrc ? (
                <img src={imgSrc} alt="" className="pf-team-card__img" />
              ) : (
                <div className="pf-team-card__ph">{c.rarity}</div>
              )}
              <span className="pf-team-card__name">{c.playerNickname}</span>
              <span className="pf-team-card__meta">{c.rarity}</span>
            </button>
          </li>
          )
        })}
      </ul>

      <button
        type="button"
        className="pf-btn pf-btn--primary pf-btn--block"
        disabled={selected.length !== 3 || deadlinePassed || submit.isPending}
        onClick={() => submit.mutate()}
      >
        {teamQ.isSuccess && teamQ.data ? 'Обновить команду' : 'Отправить команду'}
      </button>
      {errMsg && <p className="pf-err">{errMsg}</p>}

      <p className="pf-footer-link">
        <Link to={`/series/${sid}`}>Обзор серии</Link>
      </p>
    </div>
  )
}
