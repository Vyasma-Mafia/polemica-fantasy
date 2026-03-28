import { apiJson } from './client'

export interface PolemicaCompetitionSummary {
  id: number
  name: string
  startDate: string | null
  endDate: string | null
}

export interface PolemicaCompetitionDetail extends PolemicaCompetitionSummary {
  region: string | null
  city: string | null
  description: string | null
  link: string | null
  memberCount: number | null
  rating: number | null
  hasScores: boolean | null
}

export function listPolemicaCompetitions() {
  return apiJson<PolemicaCompetitionSummary[]>('/v1/admin/polemica/competitions')
}

export function getPolemicaCompetition(id: number) {
  return apiJson<PolemicaCompetitionDetail>(`/v1/admin/polemica/competitions/${id}`)
}
