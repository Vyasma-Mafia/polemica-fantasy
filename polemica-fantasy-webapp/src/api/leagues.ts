import { apiGet, apiSend } from './client'
import type { FantasyTeamDto, LeaderboardEntry, SeriesLeagueInfo } from './types'

function teamBody(userCardIds: number[]) {
  return { userCardIds }
}

export function fetchSeriesLeagues(seriesId: number, initData: string | undefined) {
  return apiGet<SeriesLeagueInfo[]>(`/api/v1/series/${seriesId}/leagues`, initData)
}

export function fetchLeagueLeaderboard(
  seriesId: number,
  leagueCode: string,
  initData: string | undefined,
) {
  return apiGet<LeaderboardEntry[]>(
    `/api/v1/series/${seriesId}/leagues/${encodeURIComponent(leagueCode)}/leaderboard`,
    initData,
  )
}

export function submitLeagueTeam(
  seriesId: number,
  leagueCode: string,
  userCardIds: number[],
  initData: string | undefined,
) {
  return apiSend<FantasyTeamDto>(
    'POST',
    `/api/v1/series/${seriesId}/leagues/${encodeURIComponent(leagueCode)}/fantasy-team`,
    initData,
    teamBody(userCardIds),
  )
}

export function updateLeagueTeam(
  seriesId: number,
  leagueCode: string,
  userCardIds: number[],
  initData: string | undefined,
) {
  return apiSend<FantasyTeamDto>(
    'PUT',
    `/api/v1/series/${seriesId}/leagues/${encodeURIComponent(leagueCode)}/fantasy-team`,
    initData,
    teamBody(userCardIds),
  )
}
