import type { LeaderboardEntry } from '../api/types'
import { formatUserDisplayName } from './userDisplayName'

export type AggregatedRow = {
  rank: number
  telegramId: number
  displayName: string
  totalScore: number
}

/** Sum leaderboard scores per user across series (client-side). */
export function aggregateTournamentLeaderboards(boards: LeaderboardEntry[][]): AggregatedRow[] {
  const byUser = new Map<number, { displayName: string; total: number }>()
  for (const board of boards) {
    for (const row of board) {
      const tid = row.user.telegramId
      const score = row.totalScore ?? 0
      const name = formatUserDisplayName(row.user)
      const prev = byUser.get(tid)
      if (prev) {
        prev.total += score
      } else {
        byUser.set(tid, { displayName: name, total: score })
      }
    }
  }
  const sorted = [...byUser.entries()].sort((a, b) => b[1].total - a[1].total)
  return sorted.map(([telegramId, v], i) => ({
    rank: i + 1,
    telegramId,
    displayName: v.displayName,
    totalScore: Math.round(v.total * 100) / 100,
  }))
}
