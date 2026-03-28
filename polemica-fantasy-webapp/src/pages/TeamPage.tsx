import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiGet, apiSend, ApiError } from '../api/client'
import type { FantasyTeamDto, UserCardItem, UserSeriesDetail } from '../api/types'
import { useInitData } from '../context/InitDataContext'

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
    },
  })

  const deadlinePassed = useMemo(() => {
    if (!seriesQ.data) return false
    return Date.now() > new Date(seriesQ.data.teamDeadline).getTime()
  }, [seriesQ.data])

  if (!initData) return <p className="muted">Нужен initData.</p>
  if (seriesQ.isLoading) return <p>Загрузка…</p>
  if (seriesQ.isError) return <p className="err">{(seriesQ.error as Error).message}</p>

  const s = seriesQ.data!
  const cards = cardsQ.data ?? []
  const errMsg = submit.error instanceof ApiError ? submit.error.message : (submit.error as Error)?.message

  return (
    <div>
      <p>
        <Link to={`/series/${sid}`}>← Серия</Link>
      </p>
      <h1>Команда: {s.name}</h1>
      {deadlinePassed && <p className="err">Дедлайн сбора команды прошёл.</p>}
      {teamQ.isSuccess && teamQ.data && (
        <p className="muted">
          Уже отправлено ({teamQ.data.slots.length} карт). Можно обновить до дедлайна.
        </p>
      )}
      {teamQ.isSuccess && !teamQ.data && <p className="muted">Команда ещё не собрана.</p>}
      <p>Выберите ровно 3 карты (порядок = слоты 1–3):</p>
      <ol className="picked">
        {selected.map((id) => (
          <li key={id}>{id}</li>
        ))}
      </ol>
      <ul className="cards-grid">
        {cards.map((c) => (
          <li key={c.id}>
            <button
              type="button"
              className={selected.includes(c.id) ? 'picked-btn' : ''}
              onClick={() => toggle(c.id)}
              disabled={deadlinePassed}
            >
              {c.playerNickname} · {c.rarity} · #{c.id}
            </button>
          </li>
        ))}
      </ul>
      <button
        type="button"
        disabled={selected.length !== 3 || deadlinePassed || submit.isPending}
        onClick={() => submit.mutate()}
      >
        {teamQ.isSuccess && teamQ.data ? 'Обновить команду' : 'Отправить команду'}
      </button>
      {errMsg && <p className="err">{errMsg}</p>}
    </div>
  )
}
