import type { UserSeriesSummary } from '../api/types'

/** Min start and max team deadline across series (for hub display). */
export function tournamentSeriesDateRange(series: UserSeriesSummary[]): { from: Date; to: Date } | null {
  if (series.length === 0) return null
  let min = new Date(series[0].startsAt).getTime()
  let max = new Date(series[0].teamDeadline).getTime()
  for (const s of series) {
    const a = new Date(s.startsAt).getTime()
    const b = new Date(s.teamDeadline).getTime()
    if (a < min) min = a
    if (b > max) max = b
  }
  return { from: new Date(min), to: new Date(max) }
}

export function formatDateShort(d: Date): string {
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}
