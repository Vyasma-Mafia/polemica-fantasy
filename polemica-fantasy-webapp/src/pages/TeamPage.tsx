import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { apiGet, apiSend, ApiError } from '../api/client'
import { fetchEconomyInfo } from '../api/userEconomy'
import type { FantasyTeamDto, Rarity, UserCardItem, UserSeriesDetail } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { teamCardRootClass, miniCardClass } from '../lib/cardFrameClasses'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { CardAchievementChips } from '../components/CardAchievementChips'
import { compareRarityDesc, RARITY_UI, rarityScoreModifierLabel } from '../lib/rarity'
import { useNow } from '../lib/useNow'

function cardsQueryString(tournamentId: number, seriesId: number) {
  const sp = new URLSearchParams()
  sp.set('tournamentId', String(tournamentId))
  sp.set('seriesId', String(seriesId))
  return sp.toString()
}

export function TeamPage() {
  const { seriesId } = useParams<{ seriesId: string }>()
  const sid = Number(seriesId)
  const location = useLocation()
  const initData = useInitData()
  const qc = useQueryClient()
  const fromHome = (location.state as { fromHome?: boolean } | null)?.fromHome === true

  const seriesQ = useQuery({
    queryKey: ['series', sid, initData],
    queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${sid}`, initData),
    enabled: !!initData && Number.isFinite(sid),
  })

  const cardsQ = useQuery({
    queryKey: ['cards', 'team', sid, seriesQ.data?.tournamentId, initData],
    queryFn: () =>
      apiGet<UserCardItem[]>(
        `/api/v1/me/cards?${cardsQueryString(seriesQ.data!.tournamentId, sid)}`,
        initData,
      ),
    enabled: !!initData && Number.isFinite(sid) && !!seriesQ.data?.tournamentId,
  })

  const economyQ = useQuery({
    queryKey: ['economy-info', initData],
    queryFn: () => fetchEconomyInfo(initData!),
    enabled: !!initData,
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
  const [rarityFilter, setRarityFilter] = useState<Rarity | ''>('')
  const [playerFantasyId, setPlayerFantasyId] = useState<number | ''>('')

  useEffect(() => {
    queueMicrotask(() => {
      setSelected([])
      setRarityFilter('')
      setPlayerFantasyId('')
    })
  }, [sid])

  useEffect(() => {
    if (!teamQ.isSuccess) return
    queueMicrotask(() => {
      if (teamQ.data?.slots?.length) {
        setSelected(
          [...teamQ.data.slots].sort((a, b) => a.slot - b.slot).map((sl) => sl.userCardId),
        )
      } else {
        setSelected([])
      }
    })
  }, [teamQ.isSuccess, teamQ.data])

  const cardById = useMemo(() => {
    const m = new Map<number, UserCardItem>()
    for (const c of cardsQ.data ?? []) m.set(c.id, c)
    return m
  }, [cardsQ.data])

  const toggle = (id: number) => {
    setSelected((prev) => {
      if (prev.includes(id)) return prev.filter((x) => x !== id)
      if (prev.length >= 3) return prev
      const adding = cardById.get(id)
      if (adding) {
        const fp = adding.fantasyPlayerId
        for (const x of prev) {
          if (cardById.get(x)?.fantasyPlayerId === fp) return prev
        }
        if (adding.rarity === 'LEGENDARY') {
          const hasLegendary = prev.some((x) => cardById.get(x)?.rarity === 'LEGENDARY')
          if (hasLegendary) return prev
        }
      }
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
    onSuccess: (data) => {
      qc.setQueryData<FantasyTeamDto | null>(['fantasy-team', sid, initData], data)
      void qc.invalidateQueries({ queryKey: ['fantasy-team', sid] })
      void qc.invalidateQueries({ queryKey: ['fantasy-teams'] })
    },
  })

  const now = useNow()
  const deadlinePassed = useMemo(() => {
    if (!seriesQ.data) return false
    return now > new Date(seriesQ.data.teamDeadline).getTime()
  }, [seriesQ.data, now])

  const usesPerRarity = economyQ.data?.usesPerRarity

  const displayCards = useMemo(() => {
    let list = [...(cardsQ.data ?? [])]
    if (rarityFilter) list = list.filter((c) => c.rarity === rarityFilter)
    if (playerFantasyId !== '') list = list.filter((c) => c.fantasyPlayerId === playerFantasyId)
    list.sort((a, b) => compareRarityDesc(a.rarity, b.rarity) || b.id - a.id)
    return list
  }, [cardsQ.data, rarityFilter, playerFantasyId])

  const legendarySlotsUsed = useMemo(
    () => selected.filter((cid) => cardById.get(cid)?.rarity === 'LEGENDARY').length,
    [selected, cardById],
  )

  if (!initData) return <p className="pf-muted">Нужен initData.</p>
  if (seriesQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (seriesQ.isError) return <p className="pf-err">{(seriesQ.error as Error).message}</p>

  const s = seriesQ.data!
  const errMsg = submit.error instanceof ApiError ? submit.error.message : (submit.error as Error)?.message
  const back = fromHome ? '/' : `/tournaments/${s.tournamentId}/series`

  return (
    <div className="pf-page">
      <PageHeader title="Сборка команды" subtitle={s.name} backTo={back} backLabel={fromHome ? 'Турниры' : 'Назад'} />

      {deadlinePassed && <p className="pf-err">Дедлайн сбора команды прошёл.</p>}
      {teamQ.isSuccess && teamQ.data && (
        <p className="pf-muted">
          Команда отправлена ({teamQ.data.slots.length} карт). Можно обновить до дедлайна.
        </p>
      )}
      {teamQ.isSuccess && !teamQ.data && (
        <p className="pf-muted">Выберите от 1 до 3 карт для серии (неполный состав — меньше награда за место).</p>
      )}

      <p className="pf-instruction">
        Выберите от 1 до 3 карт (порядок — слоты 1–3). Один игрок — не больше одной карты. Легендарных карт в команде —
        не больше одной.
      </p>

      <ol className="pf-picked-slots">
        {[0, 1, 2].map((i) => {
          const id = selected[i]
          const c = id != null ? cardById.get(id) : undefined
          const pickedSrc = c ? cardDisplayImageUrl(c) : null
          return (
            <li key={i} className="pf-picked-slots__slot">
              <span className="pf-picked-slots__num">{i + 1}</span>
              {c ? (
                <button
                  type="button"
                  className={miniCardClass(c)}
                  disabled={deadlinePassed}
                  title={deadlinePassed ? undefined : 'Снять из состава'}
                  onClick={() => toggle(c.id)}
                >
                  {pickedSrc ? <img src={pickedSrc} alt="" /> : <div className="pf-mini-card__ph" />}
                  <span>{c.playerNickname}</span>
                </button>
              ) : (
                <span className="pf-muted">—</span>
              )}
            </li>
          )
        })}
      </ol>

      <div className="pf-filters">
        <label className="pf-field">
          <span className="pf-field__label">Игрок серии</span>
          <select
            className="pf-input"
            value={playerFantasyId === '' ? '' : String(playerFantasyId)}
            onChange={(e) => {
              const v = e.target.value
              setPlayerFantasyId(v === '' ? '' : Number(v))
            }}
          >
            <option value="">Все</option>
            {s.players
              .slice()
              .sort((a, b) => a.nickname.localeCompare(b.nickname, 'ru'))
              .map((p) => (
                <option key={p.fantasyPlayerId} value={String(p.fantasyPlayerId)}>
                  {p.nickname}
                </option>
              ))}
          </select>
        </label>
      </div>

      <div className="pf-rarity-tabs">
        {RARITY_UI.map((tab) => (
          <button
            key={tab.label}
            type="button"
            className={`pf-rarity-tab ${rarityFilter === tab.value ? 'pf-rarity-tab--active' : ''}`}
            onClick={() => setRarityFilter(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {cardsQ.isLoading && <p className="pf-muted">Загрузка карт…</p>}
      {cardsQ.isError && <p className="pf-err">{(cardsQ.error as Error).message}</p>}

      <ul className="pf-team-grid">
        {displayCards.map((c) => {
          const imgSrc = cardDisplayImageUrl(c)
          const maxU = usesPerRarity?.[c.rarity] ?? Math.max(c.usesRemaining, 1)
          const dead = c.usesRemaining <= 0
          const lastUse = c.usesRemaining === 1
          const playerAlreadyPicked =
            !selected.includes(c.id) &&
            selected.some((sid) => cardById.get(sid)?.fantasyPlayerId === c.fantasyPlayerId)
          const secondLegendaryBlocked =
            c.rarity === 'LEGENDARY' && !selected.includes(c.id) && legendarySlotsUsed >= 1
          const gridDisabled = deadlinePassed || dead || playerAlreadyPicked || secondLegendaryBlocked
          const gridTitle = dead
            ? 'Контракт истёк — продлите в коллекции'
            : playerAlreadyPicked
              ? 'Этот игрок уже в команде'
              : secondLegendaryBlocked
                ? 'В команде не больше одной легендарной карты за серию'
                : undefined
          return (
            <li key={c.id}>
              <button
                type="button"
                className={teamCardRootClass(
                  c,
                  [
                    selected.includes(c.id) ? 'pf-team-card--picked' : '',
                    dead ? 'pf-team-card--dead' : '',
                    playerAlreadyPicked && !dead ? 'pf-team-card--blocked' : '',
                  ]
                    .filter(Boolean)
                    .join(' '),
                )}
                onClick={() => !gridDisabled && toggle(c.id)}
                disabled={gridDisabled}
                title={gridTitle}
              >
                <div className="pf-team-card__media">
                  {imgSrc ? (
                    <img src={imgSrc} alt="" className="pf-team-card__img" />
                  ) : (
                    <div className="pf-team-card__ph">{c.rarity}</div>
                  )}
                  <span className="pf-team-uses">⚡{c.usesRemaining}/{maxU}</span>
                  {dead && <span className="pf-team-dead-label">Истекла</span>}
                  <div className="pf-team-card__cap">
                    <span className="pf-team-card__name">{c.playerNickname}</span>
                    <span className="pf-team-card__meta">
                      {c.rarity}{' '}
                      <span className="pf-rarity-mod">{rarityScoreModifierLabel(c.rarity)}</span>
                    </span>
                    {lastUse && (
                      <span className="pf-last-use-warn">Последнее использование!</span>
                    )}
                    <CardAchievementChips achievements={c.achievements} max={3} className="pf-card-ach-chips--tight" />
                  </div>
                </div>
              </button>
            </li>
          )
        })}
      </ul>

      <button
        type="button"
        className="pf-btn pf-btn--primary pf-btn--block"
        disabled={selected.length < 1 || deadlinePassed || submit.isPending}
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
