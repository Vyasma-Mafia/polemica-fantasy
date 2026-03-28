import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { apiGet } from '../api/client'
import type { Rarity, UserCardItem } from '../api/types'
import { useInitData } from '../context/InitDataContext'

const RARITIES: Rarity[] = ['COMMON', 'RARE', 'EPIC', 'LEGENDARY']

export function CardsPage() {
  const initData = useInitData()
  const [tournamentId, setTournamentId] = useState<string>('')
  const [rarity, setRarity] = useState<Rarity | ''>('')

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

  if (!initData) return <p className="muted">Нужен initData.</p>

  return (
    <div>
      <h1>Коллекция</h1>
      <div className="filters">
        <label>
          Турнир ID{' '}
          <input
            value={tournamentId}
            onChange={(e) => setTournamentId(e.target.value)}
            placeholder="все"
          />
        </label>
        <label>
          Редкость{' '}
          <select value={rarity} onChange={(e) => setRarity((e.target.value || '') as Rarity | '')}>
            <option value="">все</option>
            {RARITIES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </label>
      </div>
      {q.isLoading && <p>Загрузка…</p>}
      {q.isError && <p className="err">{(q.error as Error).message}</p>}
      <ul className="cards-grid">
        {(q.data ?? []).map((c) => (
          <li key={c.id} className="card-mini">
            {c.imageUrl ? <img src={c.imageUrl} alt="" /> : <div className="ph">{c.rarity}</div>}
            <div>
              <strong>{c.playerNickname}</strong>
              <div className="muted">
                #{c.id} · {c.rarity}
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}
