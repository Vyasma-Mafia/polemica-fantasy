import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError, apiGet } from '../api/client'
import { createMarketplaceListing, fetchMyMarketplaceListings } from '../api/marketplace'
import { fetchEconomyInfo, recycleUserCard, renewUserCard } from '../api/userEconomy'
import type {
  FantasyTeamDto,
  FantasyTeamSeriesDetails,
  Rarity,
  SeriesStatus,
  UserCardItem,
  UserSeriesDetail,
  UserTournament,
} from '../api/types'
import { CardAchievementChips } from '../components/CardAchievementChips'
import { CardOwnershipHistoryBlock } from '../components/CardOwnershipHistoryBlock'
import { PlayerGroupedView } from '../components/PlayerGroupedView'
import { isEligibleEpicForLegendary, LegendaryUpgradeWizard } from '../components/LegendaryUpgradeWizard'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { ScoreBreakdownBlock } from '../components/ScoreBreakdownBlock'
import { useInitData } from '../context/useInitData'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { collectionCardRootClass, modalImgFrameClass } from '../lib/cardFrameClasses'
import { RARITY_UI, rarityScoreModifierLabel } from '../lib/rarity'

type LifecycleFilter = 'all' | 'active' | 'expired'

function maxUsesForCard(c: UserCardItem, usesPerRarity: Record<Rarity, number> | undefined): number {
  if (!usesPerRarity) return Math.max(c.usesRemaining, 1)
  return usesPerRarity[c.rarity] ?? c.usesRemaining
}

function teamsContainingCard(teams: FantasyTeamDto[] | undefined, userCardId: number): FantasyTeamDto[] {
  if (!teams) return []
  return teams.filter((t) => t.slots.some((s) => s.userCardId === userCardId))
}

function slotScoreForCard(team: FantasyTeamDto, userCardId: number): number | null {
  const slot = team.slots.find((s) => s.userCardId === userCardId)
  return slot?.score ?? null
}

export function CardsPage() {
  const initData = useInitData()
  const qc = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const tournamentFromQuery = searchParams.get('tournamentId') ?? ''
  const backTo = tournamentFromQuery ? `/tournaments/${tournamentFromQuery}` : '/'
  const collectionView: 'cards' | 'players' = searchParams.get('view') === 'players' ? 'players' : 'cards'

  function setCollectionView(next: 'cards' | 'players') {
    setSearchParams(
      (prev) => {
        const n = new URLSearchParams(prev)
        if (next === 'players') n.set('view', 'players')
        else n.delete('view')
        return n
      },
      { replace: true },
    )
  }

  const [tournamentId, setTournamentId] = useState(tournamentFromQuery)
  useEffect(() => {
    setTournamentId(tournamentFromQuery)
  }, [tournamentFromQuery])
  const [rarity, setRarity] = useState<Rarity | ''>('')
  const [playerFilter, setPlayerFilter] = useState('')
  const [lifeFilter, setLifeFilter] = useState<LifecycleFilter>('all')
  const [sortUses, setSortUses] = useState<'none' | 'asc' | 'desc'>('none')

  const [detailCardId, setDetailCardId] = useState<number | null>(null)
  const [selectedSeriesId, setSelectedSeriesId] = useState<number | null>(null)
  const [legendaryWizardOpen, setLegendaryWizardOpen] = useState(false)
  const [legendaryWizardInitialCardId, setLegendaryWizardInitialCardId] = useState<number | null>(null)
  const [sellModalCard, setSellModalCard] = useState<UserCardItem | null>(null)
  const [sellPrice, setSellPrice] = useState('')

  const params = useMemo(() => {
    const sp = new URLSearchParams()
    if (tournamentId) sp.set('tournamentId', tournamentId)
    if (rarity) sp.set('rarity', rarity)
    const q = sp.toString()
    return q ? `?${q}` : ''
  }, [tournamentId, rarity])

  const legendaryUpgradeParam = searchParams.get('legendaryUpgrade')
  useEffect(() => {
    if (legendaryUpgradeParam == null || !initData) return
    if (legendaryUpgradeParam === '1' || legendaryUpgradeParam === 'true') {
      setLegendaryWizardInitialCardId(null)
      setLegendaryWizardOpen(true)
    } else {
      const n = Number(legendaryUpgradeParam)
      if (Number.isFinite(n)) {
        setLegendaryWizardInitialCardId(n)
        setLegendaryWizardOpen(true)
      }
    }
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.delete('legendaryUpgrade')
        return next
      },
      { replace: true },
    )
  }, [legendaryUpgradeParam, initData, setSearchParams])

  const q = useQuery({
    queryKey: ['cards', params, initData],
    queryFn: () => apiGet<UserCardItem[]>(`/api/v1/me/cards${params}`, initData),
    enabled: !!initData,
  })

  const teamsQ = useQuery({
    queryKey: ['fantasy-teams', initData],
    queryFn: () => apiGet<FantasyTeamDto[]>('/api/v1/me/fantasy-teams', initData),
    enabled: !!initData,
  })

  const tournamentsQ = useQuery({
    queryKey: ['tournaments', initData],
    queryFn: () => apiGet<UserTournament[]>('/api/v1/tournaments', initData),
    enabled: !!initData,
  })

  const economyQ = useQuery({
    queryKey: ['economy-info', initData],
    queryFn: () => fetchEconomyInfo(initData!),
    enabled: !!initData,
  })

  const myListingsQ = useQuery({
    queryKey: ['my-marketplace-listings', initData],
    queryFn: () => fetchMyMarketplaceListings(initData),
    enabled: !!initData,
  })

  const teamSeriesIds = useMemo(
    () => [...new Set((teamsQ.data ?? []).map((t) => t.seriesId))],
    [teamsQ.data],
  )

  const seriesMetaForTeams = useQueries({
    queries: teamSeriesIds.map((seriesId) => ({
      queryKey: ['series', seriesId, initData],
      queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${seriesId}`, initData!),
      enabled: !!initData && teamSeriesIds.length > 0,
    })),
  })

  const seriesStatusById = useMemo(() => {
    const m = new Map<number, SeriesStatus>()
    teamSeriesIds.forEach((id, i) => {
      const st = seriesMetaForTeams[i]?.data?.status
      if (st) m.set(id, st)
    })
    return m
  }, [teamSeriesIds, seriesMetaForTeams])

  const listedUserCardIds = useMemo(
    () => new Set((myListingsQ.data ?? []).map((l) => l.card.userCardId)),
    [myListingsQ.data],
  )

  const usesPerRarity = economyQ.data?.usesPerRarity

  function cardBlockedForMarketplaceByTeam(cardId: number): boolean {
    const teams = teamsContainingCard(teamsQ.data, cardId)
    for (const t of teams) {
      const st = seriesStatusById.get(t.seriesId)
      if (st == null) return true
      if (st !== 'FINISHED') return true
    }
    return false
  }

  function canOfferCardOnMarketplace(c: UserCardItem): boolean {
    if (c.usesRemaining <= 0) return false
    if (listedUserCardIds.has(c.id)) return false
    if (cardBlockedForMarketplaceByTeam(c.id)) return false
    return true
  }

  const tournaments = tournamentsQ.data ?? []
  const tournamentSelectUnknown =
    Boolean(tournamentId) && !tournaments.some((t) => String(t.id) === tournamentId)

  const players = useMemo(() => {
    const names = new Set<string>()
    for (const c of q.data ?? []) names.add(c.playerNickname)
    return [...names].sort()
  }, [q.data])

  const filtered = useMemo(() => {
    let list = q.data ?? []
    if (collectionView === 'players') {
      return list
    }
    if (playerFilter.trim()) {
      list = list.filter((c) =>
        c.playerNickname.toLowerCase().includes(playerFilter.trim().toLowerCase()),
      )
    }
    if (lifeFilter === 'active') list = list.filter((c) => c.usesRemaining > 0)
    if (lifeFilter === 'expired') list = list.filter((c) => c.usesRemaining <= 0)
    if (sortUses === 'asc') {
      list = [...list].sort((a, b) => a.usesRemaining - b.usesRemaining)
    } else if (sortUses === 'desc') {
      list = [...list].sort((a, b) => b.usesRemaining - a.usesRemaining)
    }
    return list
  }, [q.data, collectionView, playerFilter, lifeFilter, sortUses])

  const detailCard = detailCardId != null ? q.data?.find((c) => c.id === detailCardId) : undefined

  const teamsWithCard = useMemo(
    () => teamsContainingCard(teamsQ.data, detailCardId ?? -1),
    [teamsQ.data, detailCardId],
  )

  useEffect(() => {
    if (detailCardId == null) {
      setSelectedSeriesId(null)
      return
    }
    if (teamsWithCard.length === 0) {
      setSelectedSeriesId(null)
      return
    }
    setSelectedSeriesId((prev) => {
      const stillValid = prev != null && teamsWithCard.some((t) => t.seriesId === prev)
      if (stillValid) return prev
      const sorted = [...teamsWithCard].sort(
        (a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime(),
      )
      return sorted[0].seriesId
    })
  }, [detailCardId, teamsWithCard])

  const seriesIdsForHistory = useMemo(
    () => [...new Set(teamsWithCard.map((t) => t.seriesId))],
    [teamsWithCard],
  )

  const seriesMetaQueries = useQueries({
    queries: seriesIdsForHistory.map((sid) => ({
      queryKey: ['series', sid, initData],
      queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${sid}`, initData!),
      enabled: !!initData && detailCardId != null,
    })),
  })

  const seriesNameById = useMemo(() => {
    const m = new Map<number, string>()
    seriesIdsForHistory.forEach((sid, i) => {
      const name = seriesMetaQueries[i]?.data?.name
      if (name) m.set(sid, name)
    })
    return m
  }, [seriesIdsForHistory, seriesMetaQueries])

  const detailsModalQ = useQuery({
    queryKey: ['fantasy-team-details', selectedSeriesId, initData],
    queryFn: () =>
      apiGet<FantasyTeamSeriesDetails>(
        `/api/v1/me/fantasy-teams/${selectedSeriesId}/details`,
        initData!,
      ),
    enabled: !!initData && selectedSeriesId != null && detailCardId != null && teamsWithCard.length > 0,
  })

  const modalColumn =
    detailCardId != null && detailsModalQ.data
      ? detailsModalQ.data.columns.find((col) => col.userCardId === detailCardId)
      : undefined

  const detailImgSrc = detailCard ? cardDisplayImageUrl(detailCard) : null

  const recycleMut = useMutation({
    mutationFn: (cardId: number) => recycleUserCard(initData!, cardId),
    onSuccess: (_, cardId) => {
      void qc.invalidateQueries({ queryKey: ['cards'] })
      void qc.invalidateQueries({ queryKey: ['me'] })
      void qc.invalidateQueries({ queryKey: ['fantasy-teams'] })
      setDetailCardId((d) => (d === cardId ? null : d))
    },
    onError: (e: Error) => window.alert(e.message),
  })

  const renewMut = useMutation({
    mutationFn: (cardId: number) => renewUserCard(initData!, cardId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['cards'] })
      void qc.invalidateQueries({ queryKey: ['me'] })
      void qc.invalidateQueries({ queryKey: ['fantasy-teams'] })
    },
    onError: (e: Error) => window.alert(e.message),
  })

  const sellMut = useMutation({
    mutationFn: () => {
      const card = sellModalCard
      if (!card || !initData) throw new Error('Нет карты')
      const price = Number(sellPrice)
      return createMarketplaceListing(initData, { userCardId: card.id, price })
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['my-marketplace-listings'] })
      void qc.invalidateQueries({ queryKey: ['marketplace-listings'] })
      void qc.invalidateQueries({ queryKey: ['cards'] })
      setSellModalCard(null)
    },
    onError: (e: Error) => window.alert(e instanceof ApiError ? e.message : String(e)),
  })

  const runRecycle = (c: UserCardItem) => {
    const recycleVal = economyQ.data?.recycleValues[c.rarity]
    if (
      !window.confirm(
        recycleVal != null
          ? `Переработать карту? Вы получите ${recycleVal}₣. Карта будет уничтожена.`
          : 'Переработать карту? Карта будет уничтожена.',
      )
    ) {
      return
    }
    recycleMut.mutate(c.id)
  }

  const runRenew = (c: UserCardItem) => {
    if (!economyQ.data) return
    const cost = economyQ.data.renewalCosts[c.rarity]
    if (!window.confirm(`Продлить контракт за ${cost}₣?`)) return
    renewMut.mutate(c.id)
  }

  const closeModal = () => {
    setDetailCardId(null)
    setSelectedSeriesId(null)
  }

  const legendaryParam = searchParams.get('legendaryUpgrade')
  useEffect(() => {
    if (legendaryParam == null || !initData) return
    if (legendaryParam === '1' || legendaryParam === 'true') {
      setLegendaryWizardInitialCardId(null)
      setLegendaryWizardOpen(true)
    } else {
      const n = Number(legendaryParam)
      if (Number.isFinite(n) && n > 0) {
        setLegendaryWizardInitialCardId(n)
        setLegendaryWizardOpen(true)
      }
    }
    const next = new URLSearchParams(searchParams)
    next.delete('legendaryUpgrade')
    setSearchParams(next, { replace: true })
  }, [legendaryParam, initData, searchParams, setSearchParams])

  if (!initData) return <MissingInitDataNotice />

  const teamsSortedForHistory = [...teamsWithCard].sort(
    (a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime(),
  )

  return (
    <div className="pf-page">
      <PageHeader title="Моя коллекция" backTo={backTo} backLabel={tournamentFromQuery ? 'К турниру' : 'Турниры'} />

      <div className="pf-view-toggle" role="group" aria-label="Вид коллекции">
        <button
          type="button"
          className={`pf-view-toggle__btn ${collectionView === 'cards' ? 'pf-view-toggle__btn--active' : ''}`}
          onClick={() => setCollectionView('cards')}
        >
          Карты
        </button>
        <button
          type="button"
          className={`pf-view-toggle__btn ${collectionView === 'players' ? 'pf-view-toggle__btn--active' : ''}`}
          onClick={() => setCollectionView('players')}
        >
          По игрокам
        </button>
      </div>

      <div className="pf-filters">
        <label className="pf-field">
          <span className="pf-field__label">Турнир</span>
          <select
            className="pf-input"
            value={tournamentId}
            onChange={(e) => setTournamentId(e.target.value)}
          >
            <option value="">Все карты</option>
            {tournamentSelectUnknown && (
              <option value={tournamentId}>Турнир №{tournamentId}</option>
            )}
            {tournaments.map((t) => (
              <option key={t.id} value={String(t.id)}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
        {collectionView === 'cards' && (
          <>
            <label className="pf-field">
              <span className="pf-field__label">Игрок</span>
              <select
                className="pf-input"
                value={playerFilter}
                onChange={(e) => setPlayerFilter(e.target.value)}
              >
                <option value="">Все</option>
                {players.map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
            <label className="pf-field">
              <span className="pf-field__label">Статус</span>
              <select
                className="pf-input"
                value={lifeFilter}
                onChange={(e) => setLifeFilter(e.target.value as LifecycleFilter)}
              >
                <option value="all">Все</option>
                <option value="active">Активные</option>
                <option value="expired">Истёкшие</option>
              </select>
            </label>
            <label className="pf-field">
              <span className="pf-field__label">Сортировка по использованиям</span>
              <select
                className="pf-input"
                value={sortUses}
                onChange={(e) => setSortUses(e.target.value as 'none' | 'asc' | 'desc')}
              >
                <option value="none">Нет</option>
                <option value="asc">Меньше сначала</option>
                <option value="desc">Больше сначала</option>
              </select>
            </label>
          </>
        )}
      </div>

      <div className="pf-rarity-tabs">
        {RARITY_UI.map((tab) => (
          <button
            key={tab.label}
            type="button"
            className={`pf-rarity-tab ${rarity === tab.value ? 'pf-rarity-tab--active' : ''}`}
            onClick={() => setRarity(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {q.isLoading && <p className="pf-muted">Загрузка…</p>}
      {q.isError && <p className="pf-err">{(q.error as Error).message}</p>}

      {!q.isLoading && !q.isError && collectionView === 'cards' && (
        <ul className="pf-collection-grid">
          {filtered.map((c) => {
            const imgSrc = cardDisplayImageUrl(c)
            const maxU = maxUsesForCard(c, usesPerRarity)
            const expired = c.usesRemaining <= 0
            return (
              <li
                key={c.id}
                className={collectionCardRootClass(c, { expired })}
              >
                <div className="pf-collection-card__frame">
                  <div
                    className="pf-collection-card__open"
                    role="button"
                    tabIndex={0}
                    onClick={() => setDetailCardId(c.id)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        setDetailCardId(c.id)
                      }
                    }}
                  >
                    {imgSrc ? (
                      <img src={imgSrc} alt="" className="pf-collection-card__img" />
                    ) : (
                      <div className="pf-collection-card__ph">{c.rarity}</div>
                    )}
                    <span className="pf-uses-badge" title="Осталось использований">
                      ⚡{c.usesRemaining}/{maxU}
                    </span>
                    {expired && <span className="pf-expired-badge">Истекла</span>}
                    <div className="pf-collection-card__cap">
                      <span className="pf-collection-card__name">{c.playerNickname}</span>
                      <span className="pf-collection-card__rarity">
                        {c.rarity}{' '}
                        <span className="pf-rarity-mod" title="Множитель очков в фэнтези">
                          {rarityScoreModifierLabel(c.rarity)}
                        </span>
                      </span>
                      <CardAchievementChips achievements={c.achievements} max={4} />
                    </div>
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {!q.isLoading && !q.isError && collectionView === 'players' && initData && (
        <PlayerGroupedView
          initData={initData}
          filteredCards={filtered}
          serverScopedCards={q.data ?? []}
          playerFilter=""
          onOpenCard={setDetailCardId}
          usesPerRarity={usesPerRarity}
        />
      )}

      {detailCard && (
        <div
          className="pf-modal-backdrop"
          role="dialog"
          aria-modal
          aria-label="Карточка"
          onClick={closeModal}
        >
          <div className="pf-modal" onClick={(e) => e.stopPropagation()}>
            <button type="button" className="pf-modal__close" onClick={closeModal}>
              ×
            </button>
            {detailImgSrc && (
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

            <CardOwnershipHistoryBlock userCardId={detailCard.id} />

            {teamsWithCard.length > 0 && (
              <div className="pf-modal__per-game" style={{ marginTop: 12 }}>
                <h4>Очки в сериях</h4>
                <ul className="pf-modal__ach" style={{ listStyle: 'disc', paddingLeft: 20 }}>
                  {teamsSortedForHistory.map((team) => {
                    const pts = slotScoreForCard(team, detailCard.id)
                    const label =
                      seriesNameById.get(team.seriesId) ??
                      (seriesMetaQueries[seriesIdsForHistory.indexOf(team.seriesId)]?.isLoading
                        ? '…'
                        : `Серия #${team.seriesId}`)
                    return (
                      <li key={team.seriesId}>
                        <strong>{label}</strong>
                        <span className="pf-muted"> — </span>
                        {pts != null ? `${pts.toFixed(2)}` : '—'}
                      </li>
                    )
                  })}
                </ul>
              </div>
            )}

            {teamsWithCard.length > 1 && selectedSeriesId != null && (
              <label className="pf-field pf-modal__series-pick">
                <span className="pf-field__label">Детализация по играм</span>
                <select
                  className="pf-input"
                  value={selectedSeriesId}
                  onChange={(e) => setSelectedSeriesId(Number(e.target.value))}
                >
                  {teamsWithCard.map((t) => (
                    <option key={t.seriesId} value={t.seriesId}>
                      {seriesNameById.get(t.seriesId) ?? `Серия #${t.seriesId}`}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {teamsWithCard.length === 0 && (
              <p className="pf-muted" style={{ marginTop: 10 }}>
                Карта ещё не участвовала в составе команд в сериях.
              </p>
            )}

            {detailsModalQ.isLoading && teamsWithCard.length > 0 && (
              <p className="pf-muted">Загрузка по играм…</p>
            )}
            {detailsModalQ.isError && (
              <p className="pf-err">{(detailsModalQ.error as Error).message}</p>
            )}
            {modalColumn && detailsModalQ.data && detailsModalQ.data.games.length > 0 && (
              <div className="pf-modal__per-game">
                <h4>По играм серии</h4>
                <ul className="pf-modal__ach" style={{ listStyle: 'none', paddingLeft: 0 }}>
                  {detailsModalQ.data.games.map((g, gi) => {
                    const cell = modalColumn.cells[gi]
                    return (
                      <li
                        key={g.seriesGameId}
                        style={{
                          marginBottom: 10,
                          borderBottom: '1px solid var(--pf-border)',
                          paddingBottom: 8,
                        }}
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

            <div className="pf-modal__economy-actions">
              {isEligibleEpicForLegendary(detailCard) && (
                <button
                  type="button"
                  className="pf-btn pf-btn--small pf-btn--primary"
                  onClick={() => {
                    setLegendaryWizardInitialCardId(detailCard.id)
                    setLegendaryWizardOpen(true)
                    closeModal()
                  }}
                >
                  Улучшить до легендарной
                </button>
              )}
              {canOfferCardOnMarketplace(detailCard) && (
                <button
                  type="button"
                  className="pf-btn pf-btn--small pf-btn--primary"
                  onClick={() => {
                    setSellModalCard(detailCard)
                    const min = economyQ.data?.renewalCosts[detailCard.rarity] ?? 0
                    setSellPrice(String(min))
                  }}
                >
                  Продать
                </button>
              )}
              <button
                type="button"
                className="pf-btn pf-btn--small"
                disabled={recycleMut.isPending}
                onClick={() => runRecycle(detailCard)}
              >
                Переработать
              </button>
              {detailCard.usesRemaining <= 0 && economyQ.data && (
                <button
                  type="button"
                  className="pf-btn pf-btn--small pf-btn--primary"
                  disabled={
                    renewMut.isPending || detailCard.timesRenewed >= economyQ.data.maxRenewals
                  }
                  title={
                    detailCard.timesRenewed >= economyQ.data.maxRenewals ? 'Лимит продлений' : undefined
                  }
                  onClick={() => runRenew(detailCard)}
                >
                  Продлить контракт ({economyQ.data.renewalCosts[detailCard.rarity]}₣)
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {sellModalCard && economyQ.data && (
        <div
          className="pf-modal-backdrop"
          role="dialog"
          aria-modal
          aria-label="Продажа карты"
          onClick={() => !sellMut.isPending && setSellModalCard(null)}
        >
          <div className="pf-modal" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              className="pf-modal__close"
              disabled={sellMut.isPending}
              onClick={() => setSellModalCard(null)}
            >
              ×
            </button>
            <h3 className="pf-modal__title">Выставить на маркетплейс</h3>
            <p className="pf-muted">{sellModalCard.playerNickname}</p>
            <label className="pf-field">
              <span className="pf-field__label">
                Цена (мин. {economyQ.data.marketplaceMinPrices[sellModalCard.rarity]}₣, макс.{' '}
                {economyQ.data.marketplaceMaxPrices[sellModalCard.rarity]}₣)
              </span>
              <input
                className="pf-input"
                inputMode="numeric"
                value={sellPrice}
                onChange={(e) => setSellPrice(e.target.value)}
              />
            </label>
            {(() => {
              const price = Number(sellPrice)
              const pct = economyQ.data.marketplaceCommissionPercent ?? 10
              const commission = Number.isFinite(price) ? Math.floor((price * pct) / 100) : 0
              const youGet = Number.isFinite(price) ? price - commission : 0
              return (
                <p className="pf-muted" style={{ marginTop: 8 }}>
                  Комиссия {pct}%: {Number.isFinite(price) ? `${commission}₣` : '—'}. Вы получите:{' '}
                  <strong>{Number.isFinite(price) && price > 0 ? `${youGet}₣` : '—'}</strong>
                </p>
              )
            })()}
            <div className="pf-modal__economy-actions" style={{ marginTop: 12 }}>
              <button
                type="button"
                className="pf-btn pf-btn--small"
                disabled={sellMut.isPending}
                onClick={() => setSellModalCard(null)}
              >
                Отмена
              </button>
              <button
                type="button"
                className="pf-btn pf-btn--small pf-btn--primary"
                disabled={sellMut.isPending}
                onClick={() => {
                  const min = economyQ.data!.renewalCosts[sellModalCard.rarity]
                  const max = economyQ.data!.marketplaceMaxPrices[sellModalCard.rarity]
                  const price = Number(sellPrice)
                  if (!Number.isFinite(price) || price < min) {
                    window.alert(`Минимальная цена для этой редкости: ${min}₣`)
                    return
                  }
                  if (!Number.isFinite(price) || price > max) {
                    window.alert(`Максимальная цена для этой редкости: ${max}₣`)
                    return
                  }
                  sellMut.mutate()
                }}
              >
                {sellMut.isPending ? 'Отправка…' : 'Выставить на продажу'}
              </button>
            </div>
          </div>
        </div>
      )}

      {initData && (
        <LegendaryUpgradeWizard
          isOpen={legendaryWizardOpen}
          onClose={() => {
            setLegendaryWizardOpen(false)
            setLegendaryWizardInitialCardId(null)
          }}
          initData={initData}
          initialUserCardId={legendaryWizardInitialCardId}
        />
      )}
    </div>
  )
}
