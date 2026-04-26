import type { SeriesLeagueBrief, SeriesLeagueInfo } from '../api/types'

export const MAIN_LEAGUE_CODE = 'MAIN'

export function defaultLeagueCode(input: string | null | undefined): string {
  const normalized = (input ?? '').trim().toUpperCase()
  return normalized || MAIN_LEAGUE_CODE
}

export function leagueShortName(code: string, fallbackName?: string): string {
  const normalized = code.trim().toUpperCase()
  if (normalized === 'MAIN') return 'Основная'
  if (normalized === 'BUDGET') return 'Бюджетная'
  return fallbackName ?? code
}

export function resolveActiveLeagueCode<T extends SeriesLeagueBrief | SeriesLeagueInfo>(
  leagues: T[],
  requestedCode: string,
): string {
  const requested = requestedCode.trim().toUpperCase()
  if (leagues.some((l) => l.code.toUpperCase() === requested)) return requested
  if (leagues.some((l) => l.code.toUpperCase() === MAIN_LEAGUE_CODE)) return MAIN_LEAGUE_CODE
  return leagues[0]?.code ?? MAIN_LEAGUE_CODE
}
