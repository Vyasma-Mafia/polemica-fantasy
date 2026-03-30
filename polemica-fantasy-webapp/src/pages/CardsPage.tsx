import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import { fetchEconomyInfo, recycleUserCard, renewUserCard } from '../api/userEconomy'
import type { Rarity, UserCardItem } from '../api/types'
import { CardAchievementChips } from '../components/CardAchievementChips'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/InitDataContext'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { RARITY_UI, rarityClass, rarityScoreModifierLabel } from '../lib/rarity'

type LifecycleFilter = 'all' | 'active' | 'expired'

function maxUsesForCard(c: UserCardItem, usesPerRarity: Record<Rarity, number> | undefined): number {
  if (!usesPerRarity) return Math.max(c.usesRemaining, 1)
  return usesPerRarity[c.rarity] ?? c.usesRemaining
}

export function CardsPage() {
  const initData = useInitData()
  const qc = useQueryClient()
  const [searchParams] = useSearchParams()
  const tournamentFromQuery = searchParams.get('tournamentId') ?? ''
  const backTo = tournamentFromQuery ? `/tournaments/${tournamentFromQuery}` : '/'

  const [tournamentId, setTournamentId] = useState(tournamentFromQuery)
  useEffect(() => {
    setTournamentId(tournamentFromQuery)
  }, [tournamentFromQuery])
  const [rarity, setRarity] = useState<Rarity | ''>('')
  const [playerFilter, setPlayerFilter] = useState('')
  const [lifeFilter, setLifeFilter] = useState<LifecycleFilter>('all')
  const [sortUses, setSortUses] = useState<'none' | 'asc' | 'desc'>('none')

  const params = useMemo(() => {
    const sp = new URLSearchParams()
    if (tournamentId) sp.set('tournamentId', tournamentId)
    if (rarity) sp.set('rarity', rarity)
    const q = sp.toString()
    return q ? `?${q}` : ''
  }, [tournamentId, rarity])

  const q = useQuery({
    queryKey: ['cards', params, initData],
    queryFn: () => apiGet<UserCardItem[]>(`/api/v1/me/cards${params}`, initData),
    enabled: !!initData,
  })

  const economyQ = useQuery({
    queryKey: ['economy-info', initData],
    queryFn: () => fetchEconomyInfo(initData!),
    enabled: !!initData,
  })

  const usesPerRarity = economyQ.data?.usesPerRarity

  const players = useMemo(() => {
    const names = new Set<string>()
    for (const c of q.data ?? []) names.add(c.playerNickname)
    return [...names].sort()
  }, [q.data])

  const filtered = useMemo(() => {
    let list = q.data ?? []
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
  }, [q.data, playerFilter, lifeFilter, sortUses])

  const recycleMut = useMutation({
    mutationFn: (cardId: number) => recycleUserCard(initData!, cardId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['cards'] })
      void qc.invalidateQueries({ queryKey: ['me'] })
    },
    onError: (e: Error) => window.alert(e.message),
  })

  const renewMut = useMutation({
    mutationFn: (cardId: number) => renewUserCard(initData!, cardId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['cards'] })
      void qc.invalidateQueries({ queryKey: ['me'] })
    },
    onError: (e: Error) => window.alert(e.message),
  })

  if (!initData) return <p className="pf-muted">Нужен initData.</p>

  return (
    <div className="pf-page">
      <PageHeader title="Моя коллекция" backTo={backTo} backLabel={tournamentFromQuery ? 'К турниру' : 'Турниры'} />

      <p className="pf-footer-link" style={{ marginBottom: 12 }}>
        <Link to="/economy">Правила экономики и награды</Link>
      </p>

      <div className="pf-filters">
        <label className="pf-field">
          <span className="pf-field__label">Турнир ID</span>
          <input
            className="pf-input"
            value={tournamentId}
            onChange={(e) => setTournamentId(e.target.value)}
            placeholder="все карты"
            inputMode="numeric"
          />
        </label>
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

      <ul className="pf-collection-grid">
        {filtered.map((c) => {
          const imgSrc = cardDisplayImageUrl(c)
          const maxU = maxUsesForCard(c, usesPerRarity)
          const expired = c.usesRemaining <= 0
          return (
            <li
              key={c.id}
              className={`pf-collection-card pf-collection-card--${rarityClass(c.rarity)}${
                expired ? ' pf-collection-card--expired' : ''
              }`}
            >
              <div className="pf-collection-card__frame">
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
                  <div className="pf-card-actions">
                    {!expired && (
                      <button
                        type="button"
                        className="pf-btn pf-btn--small"
                        disabled={recycleMut.isPending}
                        onClick={() => {
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
                        }}
                      >
                        Переработать
                      </button>
                    )}
                    {expired && economyQ.data && (
                      <button
                        type="button"
                        className="pf-btn pf-btn--small pf-btn--primary"
                        disabled={renewMut.isPending || c.timesRenewed >= economyQ.data.maxRenewals}
                        title={
                          c.timesRenewed >= economyQ.data.maxRenewals
                            ? 'Лимит продлений'
                            : undefined
                        }
                        onClick={() => {
                          const cost = economyQ.data!.renewalCosts[c.rarity]
                          if (!window.confirm(`Продлить контракт за ${cost}₣?`)) return
                          renewMut.mutate(c.id)
                        }}
                      >
                        Продлить ({economyQ.data.renewalCosts[c.rarity]}₣)
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
