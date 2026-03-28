import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { Rarity, UserCardItem } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/InitDataContext'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { RARITY_UI, rarityClass } from '../lib/rarity'

export function CardsPage() {
  const initData = useInitData()
  const [searchParams] = useSearchParams()
  const tournamentFromQuery = searchParams.get('tournamentId') ?? ''
  const backTo = tournamentFromQuery ? `/tournaments/${tournamentFromQuery}` : '/'

  const [tournamentId, setTournamentId] = useState(tournamentFromQuery)
  useEffect(() => {
    setTournamentId(tournamentFromQuery)
  }, [tournamentFromQuery])
  const [rarity, setRarity] = useState<Rarity | ''>('')
  const [playerFilter, setPlayerFilter] = useState('')

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

  const players = useMemo(() => {
    const names = new Set<string>()
    for (const c of q.data ?? []) names.add(c.playerNickname)
    return [...names].sort()
  }, [q.data])

  const filtered = useMemo(() => {
    const list = q.data ?? []
    if (!playerFilter.trim()) return list
    return list.filter((c) => c.playerNickname.toLowerCase().includes(playerFilter.trim().toLowerCase()))
  }, [q.data, playerFilter])

  if (!initData) return <p className="pf-muted">Нужен initData.</p>

  return (
    <div className="pf-page">
      <PageHeader title="Моя коллекция" backTo={backTo} backLabel={tournamentFromQuery ? 'К турниру' : 'Турниры'} />

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
          return (
          <li key={c.id} className={`pf-collection-card pf-collection-card--${rarityClass(c.rarity)}`}>
            <div className="pf-collection-card__frame">
              {imgSrc ? (
                <img src={imgSrc} alt="" className="pf-collection-card__img" />
              ) : (
                <div className="pf-collection-card__ph">{c.rarity}</div>
              )}
              <div className="pf-collection-card__cap">
                <span className="pf-collection-card__name">{c.playerNickname}</span>
                <span className="pf-collection-card__rarity">{c.rarity}</span>
              </div>
            </div>
            {c.achievements.length > 0 && (
              <ul className="pf-collection-card__ach">
                {c.achievements.slice(0, 3).map((a) => (
                  <li key={a.achievementType}>
                    {a.achievementType}: +{a.bonusPoints}
                  </li>
                ))}
              </ul>
            )}
          </li>
          )
        })}
      </ul>
    </div>
  )
}
