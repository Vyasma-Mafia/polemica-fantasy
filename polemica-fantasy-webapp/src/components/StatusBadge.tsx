import type { SeriesStatus, TournamentStatus } from '../api/types'

const TOURNAMENT_LABELS: Record<TournamentStatus, string> = {
  DRAFT: 'Черновик',
  ACTIVE: 'Активен',
  FINISHED: 'Завершён',
}

const SERIES_LABELS: Record<SeriesStatus, string> = {
  UPCOMING: 'Скоро',
  ACTIVE: 'Идёт',
  SCORING: 'Подсчёт',
  FINISHED: 'Завершена',
}

export function TournamentStatusBadge({ status }: { status: TournamentStatus }) {
  return <span className={`pf-badge pf-badge--tournament pf-badge--${status.toLowerCase()}`}>{TOURNAMENT_LABELS[status]}</span>
}

export function SeriesStatusBadge({ status }: { status: SeriesStatus }) {
  return <span className={`pf-badge pf-badge--series pf-badge--${status.toLowerCase()}`}>{SERIES_LABELS[status]}</span>
}
