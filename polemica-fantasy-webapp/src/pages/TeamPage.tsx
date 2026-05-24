import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, apiGet } from '../api/client'
import { fetchSeriesLeagues, submitLeagueTeam, updateLeagueTeam } from '../api/leagues'
import { cancelMarketplaceListing } from '../api/marketplace'
import { fetchEconomyInfo } from '../api/userEconomy'
import type { FantasyTeamDto, Rarity, UserCardItem, UserProfile, UserSeriesDetail } from '../api/types'
import { BudgetProgressBar } from '../components/BudgetProgressBar'
import { LeagueTabs } from '../components/LeagueTabs'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { teamCardRootClass, miniCardClass } from '../lib/cardFrameClasses'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { CardPerkChips } from '../components/CardPerkChips'
import { CardValueBadge } from '../components/CardValueBadge'
import { MarketplaceListedBadge } from '../components/MarketplaceListedBadge'
import { defaultLeagueCode, leagueShortName, resolveActiveLeagueCode } from '../lib/leagues'
import { compareRarityDesc, RARITY_UI, rarityScoreModifierLabel } from '../lib/rarity'
import { shareToTelegram } from '../lib/shareLinks'
import { useNow } from '../lib/useNow'

function cardsQueryString(tournamentId: number, seriesId: number) {
  const sp = new URLSearchParams()
  sp.set('tournamentId', String(tournamentId))
  sp.set('seriesId', String(seriesId))
  return sp.toString()
}

function leagueCodeEquals(a: string, b: string): boolean {
  return a.trim().toUpperCase() === b.trim().toUpperCase()
}

export function TeamPage() {
  const { seriesId } = useParams<{ seriesId: string }>()
  const sid = Number(seriesId)
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const initData = useInitData()
  const qc = useQueryClient()
  const fromHome = (location.state as { fromHome?: boolean } | null)?.fromHome === true
  const requestedLeagueCode = defaultLeagueCode(searchParams.get('league'))

  const seriesQ = useQuery({
    queryKey: ['series', sid, initData],
    queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${sid}`, initData),
    enabled: !!initData && Number.isFinite(sid),
  })
  const leaguesQ = useQuery({
    queryKey: ['series', sid, 'leagues', initData],
    queryFn: () => fetchSeriesLeagues(sid, initData),
    enabled: !!initData && Number.isFinite(sid),
  })
  const leagues = leaguesQ.data ?? []
  const activeLeagueCode = resolveActiveLeagueCode(leagues, requestedLeagueCode)
  const activeLeague = leagues.find((league) => leagueCodeEquals(league.code, activeLeagueCode))

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
  const meQ = useQuery({
    queryKey: ['me', initData],
    queryFn: () => apiGet<UserProfile>('/api/v1/me', initData),
    enabled: !!initData,
  })

  const teamQ = useQuery({
    queryKey: ['fantasy-team', sid, activeLeagueCode, initData],
    queryFn: async () => {
      try {
        return await apiGet<FantasyTeamDto>(
          `/api/v1/me/fantasy-teams/${sid}?leagueCode=${encodeURIComponent(activeLeagueCode)}`,
          initData,
        )
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) return null
        throw e
      }
    },
    enabled: !!initData && Number.isFinite(sid) && leagues.length > 0,
    retry: false,
  })

  const [selected, setSelected] = useState<number[]>([])
  const [rarityFilter, setRarityFilter] = useState<Rarity | ''>('')
  const [playerFantasyId, setPlayerFantasyId] = useState<number | ''>('')
  const [teamSelectionHydrated, setTeamSelectionHydrated] = useState(false)
  const [unlistingListingId, setUnlistingListingId] = useState<number | null>(null)

  useEffect(() => {
    queueMicrotask(() => {
      setSelected([])
      setRarityFilter('')
      setPlayerFantasyId('')
      setTeamSelectionHydrated(false)
    })
  }, [sid, activeLeagueCode])

  useEffect(() => {
    if (!leagues.length) return
    if (leagueCodeEquals(activeLeagueCode, requestedLeagueCode)) return
    const next = new URLSearchParams(searchParams)
    next.set('league', activeLeagueCode)
    setSearchParams(next, { replace: true })
  }, [activeLeagueCode, leagues.length, requestedLeagueCode, searchParams, setSearchParams])

  useEffect(() => {
    if (!teamQ.isSuccess || teamSelectionHydrated) return
    queueMicrotask(() => {
      if (teamQ.data?.slots?.length) {
        setSelected(
          [...teamQ.data.slots].sort((a, b) => a.slot - b.slot).map((sl) => sl.userCardId),
        )
      } else if (selected.length === 0) {
        setSelected([])
      }
      setTeamSelectionHydrated(true)
    })
  }, [teamQ.isSuccess, teamQ.data, teamSelectionHydrated, selected.length])
  const cardById = useMemo(() => {
    const m = new Map<number, UserCardItem>()
    for (const c of cardsQ.data ?? []) m.set(c.id, c)
    return m
  }, [cardsQ.data])
  const currentTeamCardIds = useMemo(
    () => new Set((teamQ.data?.slots ?? []).map((slot) => slot.userCardId)),
    [teamQ.data?.slots],
  )

  const toggle = (id: number) => {
    setSelected((prev) => {
      if (prev.includes(id)) return prev.filter((x) => x !== id)
      if (prev.length >= (activeLeague?.maxTeamSize ?? 3)) return prev
      const adding = cardById.get(id)
      if (adding) {
        if (adding.canJoinMoreLeagues === false && !currentTeamCardIds.has(id)) return prev
        const fp = adding.fantasyPlayerId
        for (const x of prev) {
          if (cardById.get(x)?.fantasyPlayerId === fp) return prev
        }
        if (adding.rarity === 'LEGENDARY' && activeLeague?.maxLegendaryCount != null) {
          const hasLegendary = prev.some((x) => cardById.get(x)?.rarity === 'LEGENDARY')
          if (hasLegendary && activeLeague.maxLegendaryCount <= 1) return prev
          const nextLegendaryCount =
            prev.filter((x) => cardById.get(x)?.rarity === 'LEGENDARY').length + 1
          if (nextLegendaryCount > activeLeague.maxLegendaryCount) return prev
        }
        if (activeLeague?.valueCap != null) {
          const selectedValue = prev.reduce((sum, cid) => sum + (cardById.get(cid)?.value ?? 0), 0)
          if (selectedValue + adding.value > activeLeague.valueCap) return prev
        }
      }
      return [...prev, id]
    })
  }

  const submit = useMutation({
    mutationFn: async () => {
      const existing = teamQ.data
      if (existing != null) {
        return updateLeagueTeam(sid, activeLeagueCode, selected, initData)
      }
      return submitLeagueTeam(sid, activeLeagueCode, selected, initData)
    },
    onSuccess: (data) => {
      setSelected([...data.slots].sort((a, b) => a.slot - b.slot).map((sl) => sl.userCardId))
      setTeamSelectionHydrated(true)
      qc.setQueryData<FantasyTeamDto | null>(['fantasy-team', sid, activeLeagueCode, initData], data)
    },
    onError: () => {
      void qc.invalidateQueries({ queryKey: ['fantasy-team', sid] })
    },
  })
  const unlistCardListing = useMutation({
    mutationFn: async (listingId: number) => {
      await cancelMarketplaceListing(initData, listingId)
      return listingId
    },
    onMutate: (listingId) => {
      setUnlistingListingId(listingId)
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['cards'] })
      void qc.invalidateQueries({ queryKey: ['my-marketplace-listings'] })
      void qc.invalidateQueries({ queryKey: ['marketplace-listings'] })
    },
    onError: (e: Error) => {
      window.alert(e instanceof ApiError ? e.message : e.message || 'Не удалось снять карту с продажи')
    },
    onSettled: () => {
      setUnlistingListingId(null)
    },
  })

  const now = useNow()
  const deadlinePassed = useMemo(() => {
    if (!seriesQ.data) return false
    return now > new Date(seriesQ.data.teamDeadline).getTime()
  }, [seriesQ.data, now])

  const usesPerRarity = economyQ.data?.usesPerRarity
  const selectedValue = useMemo(
    () => selected.reduce((sum, cid) => sum + (cardById.get(cid)?.value ?? 0), 0),
    [selected, cardById],
  )
  const valueCap = activeLeague?.valueCap ?? null
  const remainingBudget = valueCap != null ? valueCap - selectedValue : null
  const minTeamSize = activeLeague?.minTeamSize ?? 1
  const maxTeamSize = activeLeague?.maxTeamSize ?? 3

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
  const leagueTabs = leagues.map((league) => ({
    code: league.code,
    name: league.name,
    hasTeam: league.hasTeam,
    valueCap: league.valueCap,
  }))

  if (!initData) return <MissingInitDataNotice />
  if (seriesQ.isLoading || leaguesQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (seriesQ.isError) return <p className="pf-err">{(seriesQ.error as Error).message}</p>
  if (leaguesQ.isError) return <p className="pf-err">{(leaguesQ.error as Error).message}</p>
  if (!activeLeague) return <p className="pf-err">Лига недоступна для этой серии.</p>

  const s = seriesQ.data!
  const errMsg = submit.error instanceof ApiError ? submit.error.message : (submit.error as Error)?.message
  const back = fromHome ? '/' : `/tournaments/${s.tournamentId}/series`
  const activeLeagueName = leagueShortName(activeLeague.code, activeLeague.name)
  const seriesOverviewPath = `/series/${sid}?league=${encodeURIComponent(activeLeagueCode)}`
  const submittedTeam = teamQ.data
  const setLeague = (code: string) => {
    const next = new URLSearchParams(searchParams)
    next.set('league', code.toUpperCase())
    setSearchParams(next, { replace: true })
  }

  return (
    <div className="pf-page">
      <PageHeader
        title="Сборка команды"
        subtitle={`${s.name} · ${activeLeagueName}`}
        backTo={back}
        backLabel={fromHome ? 'Турниры' : 'Назад'}
      />
      <LeagueTabs leagues={leagueTabs} activeCode={activeLeagueCode} onChange={setLeague} />

      {deadlinePassed && <p className="pf-err">Дедлайн сбора команды прошёл.</p>}
      {teamQ.isSuccess && teamQ.data && (
        <p className="pf-muted">
          Команда отправлена ({teamQ.data.slots.length} карт). Можно обновить до дедлайна.
        </p>
      )}
      {teamQ.isSuccess && !teamQ.data && (
        <p className="pf-muted">
          Выберите от {minTeamSize} до {maxTeamSize} карт для серии (неполный состав — меньше награда за место).
        </p>
      )}

      <p className="pf-instruction">
        Выберите от {minTeamSize} до {maxTeamSize} карт (порядок — слоты 1–3). Один игрок — не больше одной карты.
        {activeLeague.maxLegendaryCount != null &&
          ` Легендарных карт в команде — не больше ${activeLeague.maxLegendaryCount}.`}
      </p>
      {valueCap != null && <BudgetProgressBar currentValue={selectedValue} maxValue={valueCap} />}

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
      <div className="pf-team-picked-actions">
        <button
          type="button"
          className="pf-btn pf-btn--primary pf-btn--block"
          disabled={selected.length < minTeamSize || deadlinePassed || submit.isPending}
          onClick={() => submit.mutate()}
        >
          {teamQ.isSuccess && teamQ.data ? `Обновить (${activeLeagueName})` : `Отправить (${activeLeagueName})`}
        </button>
        {errMsg && <p className="pf-err">{errMsg}</p>}
        <p className="pf-footer-link">
          <Link to={seriesOverviewPath}>Обзор серии</Link>
        </p>
        {submittedTeam && meQ.data && (
          <div className="pf-share-row pf-share-row--center">
            <button
              type="button"
              className="pf-btn pf-btn--small pf-btn--outline"
              onClick={() =>
                shareToTelegram(
                  {
                    kind: 'team',
                    seriesId: sid,
                    telegramId: meQ.data.telegramId,
                    leagueCode: activeLeagueCode,
                  },
                  `Моя команда в ${s.name}, ${activeLeagueName}: ${submittedTeam.totalScore != null ? `${submittedTeam.totalScore.toFixed(2)} очков` : 'состав отправлен'}`,
                )
              }
            >
              Поделиться командой
            </button>
          </div>
        )}
      </div>

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
          const budgetBlocked =
            valueCap != null &&
            !selected.includes(c.id) &&
            remainingBudget != null &&
            c.value > remainingBudget
          const leagueUsesBlocked =
            c.canJoinMoreLeagues === false && !selected.includes(c.id) && !currentTeamCardIds.has(c.id)
          const secondLegendaryBlocked =
            c.rarity === 'LEGENDARY' &&
            !selected.includes(c.id) &&
            activeLeague.maxLegendaryCount != null &&
            legendarySlotsUsed >= activeLeague.maxLegendaryCount
          const listed = Boolean(c.activeMarketplaceListing)
          const gridDisabled =
            deadlinePassed ||
            dead ||
            playerAlreadyPicked ||
            secondLegendaryBlocked ||
            listed ||
            budgetBlocked ||
            leagueUsesBlocked
          const otherLeaguesInSeries =
            c.leaguesInSeries?.filter((code) => !leagueCodeEquals(code, activeLeagueCode)) ?? []
          const gridTitle = dead
            ? 'Контракт истёк — продлите в коллекции'
            : playerAlreadyPicked
              ? 'Этот игрок уже в команде'
              : budgetBlocked
                ? `Эта карта стоит ${c.value}₱, осталось ${remainingBudget ?? 0}₱ бюджета`
                : leagueUsesBlocked
                  ? 'Не хватает uses, чтобы поставить карту ещё в одну лигу серии'
              : secondLegendaryBlocked
                ? `В лиге максимум ${activeLeague.maxLegendaryCount} легендарн. карт`
                : listed
                  ? 'Снимите карту с продажи, чтобы поставить в команду'
                  : undefined
          const listingId = c.activeMarketplaceListing?.listingId ?? null
          const unlistingThisCard = listingId != null && unlistingListingId === listingId
          return (
            <li key={c.id} className="pf-team-grid__item">
              <button
                type="button"
                className={teamCardRootClass(
                  c,
                  [
                    selected.includes(c.id) ? 'pf-team-card--picked' : '',
                    dead ? 'pf-team-card--dead' : '',
                    playerAlreadyPicked && !dead ? 'pf-team-card--blocked' : '',
                    budgetBlocked && !dead ? 'pf-card--budget-disabled' : '',
                    listed && !dead ? 'pf-team-card--listed' : '',
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
                  {otherLeaguesInSeries.length > 0 && (
                    <span className={`pf-card-league-badge${dead ? ' pf-card-league-badge--team-dead' : ''}`}>
                      {otherLeaguesInSeries.map((code) => leagueShortName(code)).join(', ')}
                    </span>
                  )}
                  {c.activeMarketplaceListing && (
                    <MarketplaceListedBadge listing={c.activeMarketplaceListing} layout="team" />
                  )}
                  <CardValueBadge value={c.value} layout="team" dead={dead} />
                  <div className="pf-team-card__cap">
                    <span className="pf-team-card__name">{c.playerNickname}</span>
                    <span className="pf-team-card__meta">
                      {c.rarity}{' '}
                      <span className="pf-rarity-mod">{rarityScoreModifierLabel(c.rarity)}</span>
                    </span>
                    {lastUse && (
                      <span className="pf-last-use-warn">Последнее использование!</span>
                    )}
                    <CardPerkChips perks={c.perks} max={3} className="pf-card-perk-chips--tight" />
                  </div>
                </div>
              </button>
              {listingId != null && (
                <button
                  type="button"
                  className="pf-team-card__unlist-btn"
                  disabled={unlistCardListing.isPending}
                  onClick={() => {
                    if (!window.confirm(`Снять с продажи карту игрока ${c.playerNickname}?`)) return
                    unlistCardListing.mutate(listingId)
                  }}
                >
                  {unlistingThisCard ? 'Снимаем…' : 'Снять с продажи'}
                </button>
              )}
            </li>
          )
        })}
      </ul>
    </div>
  )
}
