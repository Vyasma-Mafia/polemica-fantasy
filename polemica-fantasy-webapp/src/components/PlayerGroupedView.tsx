import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiGet } from '../api/client'
import type { FantasyPlayerBrief, Rarity, UserCardItem } from '../api/types'
import { groupCardsByPlayerId, pickBestUserCard } from '../lib/collectionByPlayer'
import { compareRarityDesc } from '../lib/rarity'
import { PlayerCard } from './PlayerCard'

type Props = {
  initData: string
  /** С клиентскими фильтрами: игрок, статус, сортировка. */
  filteredCards: UserCardItem[]
  /** Как в ответе API (турнир, редкость) — для «нет в коллекции». */
  serverScopedCards: UserCardItem[]
  playerFilter: string
  onOpenCard: (userCardId: number) => void
  usesPerRarity: Record<Rarity, number> | undefined
}

type RowWith = {
  type: 'with'
  id: number
  nickname: string
  photoUrl: string | null
  cards: UserCardItem[]
}

type RowEmpty = {
  type: 'empty'
  id: number
  nickname: string
  photoUrl: string | null
}

function buildRows(
  allPlayers: FantasyPlayerBrief[] | undefined,
  filteredCards: UserCardItem[],
  serverScopedCards: UserCardItem[],
  playerFilter: string,
): { withRows: RowWith[]; emptyRows: RowEmpty[] } {
  const byFiltered = groupCardsByPlayerId(filteredCards)
  const serverPlayerIds = new Set(serverScopedCards.map((c) => c.fantasyPlayerId))

  const withRows: RowWith[] = []
  for (const [id, cards] of byFiltered) {
    if (cards.length === 0) continue
    const fp = allPlayers?.find((p) => p.id === id)
    const nickname = fp?.nickname ?? cards[0]!.playerNickname
    const photoUrl = fp?.photoUrl ?? cards[0]!.playerPhotoUrl ?? null
    withRows.push({ type: 'with', id, nickname, photoUrl, cards })
  }
  withRows.sort((a, b) => {
    if (b.cards.length !== a.cards.length) return b.cards.length - a.cards.length
    return compareRarityDesc(
      pickBestUserCard(a.cards).rarity,
      pickBestUserCard(b.cards).rarity,
    )
  })

  const emptyRows: RowEmpty[] = []
  for (const fp of allPlayers ?? []) {
    if (serverPlayerIds.has(fp.id)) continue
    emptyRows.push({ type: 'empty', id: fp.id, nickname: fp.nickname, photoUrl: fp.photoUrl })
  }
  emptyRows.sort((a, b) => a.nickname.localeCompare(b.nickname, 'ru'))

  const q = playerFilter.trim().toLowerCase()
  const nameOk = (nick: string) => !q || nick.toLowerCase().includes(q)

  return {
    withRows: withRows.filter((r) => nameOk(r.nickname)),
    emptyRows: emptyRows.filter((r) => nameOk(r.nickname)),
  }
}

/**
 * Сетка коллекции, сгруппированная по игрокам; данные с `/api/v1/fantasy-players`.
 */
export function PlayerGroupedView({
  initData,
  filteredCards,
  serverScopedCards,
  playerFilter,
  onOpenCard,
  usesPerRarity,
}: Props) {
  const [expandedId, setExpandedId] = useState<number | null>(null)

  const playersQ = useQuery({
    queryKey: ['fantasy-players', initData],
    queryFn: () => apiGet<FantasyPlayerBrief[]>('/api/v1/fantasy-players', initData),
    enabled: !!initData,
  })

  const { withRows, emptyRows } = useMemo(
    () => buildRows(playersQ.data, filteredCards, serverScopedCards, playerFilter),
    [playersQ.data, filteredCards, serverScopedCards, playerFilter],
  )

  if (playersQ.isLoading) {
    return <p className="pf-muted">Загрузка игроков…</p>
  }
  if (playersQ.isError) {
    return <p className="pf-err">{(playersQ.error as Error).message}</p>
  }

  const toggle = (id: number) => {
    setExpandedId((prev) => (prev === id ? null : id))
  }

  if (withRows.length === 0 && emptyRows.length === 0) {
    return <p className="pf-muted">Нет игроков в этой выборке.</p>
  }

  return (
    <>
      {withRows.length > 0 && (
        <ul className="pf-collection-grid">
          {withRows.map((row) => (
            <PlayerCard
              key={row.id}
              mode="with"
              fantasyPlayerId={row.id}
              nickname={row.nickname}
              photoUrl={row.photoUrl}
              cards={row.cards}
              expanded={expandedId === row.id}
              onToggle={() => toggle(row.id)}
              onOpenCard={onOpenCard}
              usesPerRarity={usesPerRarity}
            />
          ))}
        </ul>
      )}

      {emptyRows.length > 0 && (
        <>
          <h3 className="pf-player-group-heading">Пока без карт</h3>
          <ul className="pf-collection-grid">
            {emptyRows.map((row) => (
              <PlayerCard
                key={row.id}
                mode="empty"
                fantasyPlayerId={row.id}
                nickname={row.nickname}
                photoUrl={row.photoUrl}
              />
            ))}
          </ul>
        </>
      )}
    </>
  )
}
