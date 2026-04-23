import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { LeaderboardEntry, UserProfile, UserSeriesDetail } from '../api/types'
import { LeaderboardPinnedBlock } from '../components/LeaderboardPinnedBlock'
import { PageHeader } from '../components/PageHeader'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { useInitData } from '../context/useInitData'
import { splitLeaderboardByTelegramId } from '../lib/leaderboardSelf'
import { formatUserDisplayName } from '../lib/userDisplayName'

export function LeaderboardPage() {
  const { seriesId } = useParams<{ seriesId: string }>()
  const id = Number(seriesId)
  const initData = useInitData()

  const seriesMeta = useQuery({
    queryKey: ['series', id, initData],
    queryFn: () => apiGet<UserSeriesDetail>(`/api/v1/series/${id}`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  const q = useQuery({
    queryKey: ['leaderboard', id, initData],
    queryFn: () => apiGet<LeaderboardEntry[]>(`/api/v1/series/${id}/leaderboard`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  const meQ = useQuery({
    queryKey: ['me', initData],
    queryFn: () => apiGet<UserProfile>('/api/v1/me', initData),
    enabled: !!initData,
  })

  if (!initData) return <MissingInitDataNotice />
  if (q.isLoading || seriesMeta.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>

  const rows = q.data ?? []
  const myTg = meQ.data?.telegramId
  const { pinned, rest } = splitLeaderboardByTelegramId(rows, myTg)
  const s = seriesMeta.data
  const back = s ? `/tournaments/${s.tournamentId}` : '/'

  return (
    <div className="pf-page">
      <PageHeader title="Лидерборд" subtitle={s?.name} backTo={back} />

      {pinned && (
        <LeaderboardPinnedBlock>
          <Link
            to={`/series/${id}/leaderboard/player/${pinned.user.telegramId}`}
            className="pf-lb-row pf-lb-row--link"
          >
            <span className="pf-lb-rank">#{pinned.rank}</span>
            <span className="pf-lb-name">{formatUserDisplayName(pinned.user)}</span>
            <span className="pf-lb-score">
              {pinned.totalScore != null ? pinned.totalScore.toFixed(2) : '—'}
              <span className="pf-lb-score-label">очков</span>
            </span>
          </Link>
        </LeaderboardPinnedBlock>
      )}

      <ul className="pf-lb-list">
        {rest.map((r) => (
          <li key={r.rank + '-' + r.user.telegramId}>
            <Link
              to={`/series/${id}/leaderboard/player/${r.user.telegramId}`}
              className="pf-lb-row pf-lb-row--link"
            >
              <span className="pf-lb-rank">#{r.rank}</span>
              <span className="pf-lb-name">{formatUserDisplayName(r.user)}</span>
              <span className="pf-lb-score">
                {r.totalScore != null ? r.totalScore.toFixed(2) : '—'}
                <span className="pf-lb-score-label">очков</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
      {rows.length === 0 && <p className="pf-muted">Пока нет команд.</p>}

      <p className="pf-footer-link">
        <Link to={`/series/${id}`}>← К серии</Link>
      </p>
    </div>
  )
}
