import { useQueries, useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { LeaderboardEntry, UserTournamentDetail } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/InitDataContext'
import { aggregateTournamentLeaderboards } from '../lib/aggregateLeaderboard'

export function TournamentLeaderboardPage() {
  const { tournamentId } = useParams<{ tournamentId: string }>()
  const id = Number(tournamentId)
  const initData = useInitData()
  const [tab, setTab] = useState<'general' | number>('general')

  const tq = useQuery({
    queryKey: ['tournament', id, initData],
    queryFn: () => apiGet<UserTournamentDetail>(`/api/v1/tournaments/${id}`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  const seriesIds = tq.data?.series.map((s) => s.id) ?? []
  const leaderboardQueries = useQueries({
    queries: seriesIds.map((sid) => ({
      queryKey: ['leaderboard', sid, initData],
      queryFn: () => apiGet<LeaderboardEntry[]>(`/api/v1/series/${sid}/leaderboard`, initData),
      enabled: !!initData && seriesIds.length > 0,
    })),
  })

  const lbPending = seriesIds.length > 0 && leaderboardQueries.some((q) => q.isPending || q.isLoading)
  const errLb = leaderboardQueries.find((q) => q.isError)?.error

  const generalRows = useMemo(() => {
    const boards = leaderboardQueries.map((q) => q.data ?? []).filter((b) => b.length > 0)
    if (boards.length === 0) return []
    return aggregateTournamentLeaderboards(boards)
  }, [leaderboardQueries])

  if (!initData) return <p className="pf-muted">Нужен initData.</p>
  if (tq.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (tq.isError) return <p className="pf-err">{(tq.error as Error).message}</p>

  const t = tq.data!
  const back = `/tournaments/${t.id}`

  const activeSeriesIndex = tab === 'general' ? -1 : t.series.findIndex((s) => s.id === tab)
  const activeBoard: LeaderboardEntry[] =
    tab === 'general' || activeSeriesIndex < 0 ? [] : (leaderboardQueries[activeSeriesIndex]?.data ?? [])

  return (
    <div className="pf-page">
      <PageHeader title="Лидерборд" subtitle={t.name} backTo={back} />

      <div className="pf-tabs pf-tabs--scroll">
        <button
          type="button"
          className={`pf-tab ${tab === 'general' ? 'pf-tab--active' : ''}`}
          onClick={() => setTab('general')}
        >
          Общий
        </button>
        {t.series.map((s) => (
          <button
            key={s.id}
            type="button"
            className={`pf-tab ${tab === s.id ? 'pf-tab--active' : ''}`}
            onClick={() => setTab(s.id)}
          >
            {s.name}
          </button>
        ))}
      </div>

      {lbPending && <p className="pf-muted">Загрузка таблицы…</p>}
      {errLb && <p className="pf-err">{(errLb as Error).message}</p>}

      {tab === 'general' && (
        <ul className="pf-lb-list">
          {generalRows.map((r) => (
            <li key={r.telegramId} className="pf-lb-row">
              <span className="pf-lb-rank">#{r.rank}</span>
              <span className="pf-lb-name">{r.displayName}</span>
              <span className="pf-lb-score">
                {r.totalScore.toFixed(2)}
                <span className="pf-lb-score-label">очков</span>
              </span>
            </li>
          ))}
        </ul>
      )}

      {tab !== 'general' && (
        <ul className="pf-lb-list">
          {activeBoard.map((r) => (
            <li key={r.rank + '-' + r.user.telegramId} className="pf-lb-row">
              <span className="pf-lb-rank">#{r.rank}</span>
              <span className="pf-lb-name">{r.user.firstName ?? r.user.username ?? r.user.telegramId}</span>
              <span className="pf-lb-score">
                {r.totalScore != null ? r.totalScore.toFixed(2) : '—'}
                <span className="pf-lb-score-label">очков</span>
              </span>
            </li>
          ))}
        </ul>
      )}

      {tab === 'general' && generalRows.length === 0 && !lbPending && (
        <p className="pf-muted">Пока нет данных по сериям.</p>
      )}
      {tab !== 'general' && activeBoard.length === 0 && !lbPending && (
        <p className="pf-muted">Пока нет команд.</p>
      )}

      <p className="pf-footer-link">
        <Link to={back}>← К турниру</Link>
      </p>
    </div>
  )
}
