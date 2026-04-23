import type { LeaderboardEntry } from '../api/types'
import type { AggregatedRow } from './aggregateLeaderboard'

export function splitLeaderboardByTelegramId(
  rows: LeaderboardEntry[],
  myTelegramId: number | undefined
): { pinned: LeaderboardEntry | null; rest: LeaderboardEntry[] } {
  if (myTelegramId == null) return { pinned: null, rest: rows }
  const idx = rows.findIndex((r) => r.user.telegramId === myTelegramId)
  if (idx < 0) return { pinned: null, rest: rows }
  const pinned = rows[idx]!
  const rest = rows.filter((_, i) => i !== idx)
  return { pinned, rest }
}

export function splitAggregatedByTelegramId(
  rows: AggregatedRow[],
  myTelegramId: number | undefined
): { pinned: AggregatedRow | null; rest: AggregatedRow[] } {
  if (myTelegramId == null) return { pinned: null, rest: rows }
  const idx = rows.findIndex((r) => r.telegramId === myTelegramId)
  if (idx < 0) return { pinned: null, rest: rows }
  const pinned = rows[idx]!
  const rest = rows.filter((_, i) => i !== idx)
  return { pinned, rest }
}
