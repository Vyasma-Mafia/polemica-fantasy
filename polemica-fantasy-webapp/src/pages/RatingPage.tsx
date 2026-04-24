import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchGlobalRating } from '../api/rating'
import { LeaderboardPinnedBlock } from '../components/LeaderboardPinnedBlock'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { formatUserDisplayName } from '../lib/userDisplayName'
import type { RatingEntry } from '../api/types'

const FAR_RANK_THRESHOLD = 20

function formatValue(n: number): string {
  return n.toLocaleString('ru-RU')
}

function ratingRow(e: RatingEntry): { rank: number; name: string; f: string; c: string; t: string } {
  return {
    rank: e.rank,
    name: formatUserDisplayName(e.user),
    f: formatValue(e.fantikiBalance),
    c: formatValue(e.cardsValue),
    t: formatValue(e.totalValue),
  }
}

function RatingTableRow({ row, current }: { row: ReturnType<typeof ratingRow>; current: boolean }) {
  return (
    <tr className={current ? 'pf-rating-row pf-rating-row--current' : 'pf-rating-row'}>
      <td className="pf-rating__cell pf-rating__cell--rank">#{row.rank}</td>
      <td className="pf-rating__cell pf-rating__cell--name">{row.name}</td>
      <td className="pf-rating__cell pf-rating__num" title="Фантики">
        <span className="pf-rating__sym" aria-hidden>
          ₣
        </span>
        {row.f}
      </td>
      <td className="pf-rating__cell pf-rating__num" title="Ценность карт (₱)">
        <span className="pf-rating__sym" aria-hidden>
          ₱
        </span>
        {row.c}
      </td>
      <td className="pf-rating__cell pf-rating__num pf-rating__cell--total" title="Всего">
        {row.t}
      </td>
    </tr>
  )
}

export function RatingPage() {
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['global-rating', initData],
    queryFn: () => fetchGlobalRating(initData!),
    enabled: !!initData,
  })

  if (!initData) return <MissingInitDataNotice />
  if (q.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>

  const data = q.data!
  const entries = data.entries
  const myTg = data.currentUser?.user.telegramId
  const showBottomPin = Boolean(
    data.currentUser && data.currentUser.rank > FAR_RANK_THRESHOLD
  )
  const bottomRow = data.currentUser ? ratingRow(data.currentUser) : null

  return (
    <div className={showBottomPin ? 'pf-page pf-page--rating-pinned' : 'pf-page'}>
      <PageHeader title="Рейтинг" backTo="/" />

      <div className="pf-rating-table-wrap">
        <table className="pf-rating-table">
          <thead>
            <tr>
              <th className="pf-rating__th--rank" scope="col">
                #
              </th>
              <th scope="col">Игрок</th>
              <th className="pf-rating__th--num" scope="col" title="Баланс фантиков">
                ₣
              </th>
              <th className="pf-rating__th--num" scope="col" title="Суммарная ценность карт (₱)">
                ₱
              </th>
              <th className="pf-rating__th--num" scope="col" title="Баланс + ценность карт">
                Всего
              </th>
            </tr>
          </thead>
          <tbody>
            {entries.map((e) => {
              const isCurrent = myTg != null && e.user.telegramId === myTg
              return (
                <RatingTableRow
                  key={e.user.telegramId + '-' + e.rank}
                  row={ratingRow(e)}
                  current={isCurrent}
                />
              )
            })}
          </tbody>
        </table>
        {entries.length === 0 && <p className="pf-muted">Пока нет записей.</p>}
      </div>

      {showBottomPin && bottomRow && (
        <div className="pf-rating-pinned--bottom" role="status" aria-label="Ваше место в рейтинге">
          <LeaderboardPinnedBlock>
            <div className="pf-rating-pinned__row">
              <span className="pf-rating__cell--rank">#{bottomRow.rank}</span>
              <span className="pf-rating__name-truncate">{bottomRow.name}</span>
              <span className="pf-rating__num">
                <span className="pf-rating__sym" aria-hidden>
                  ₣
                </span>
                {bottomRow.f}
              </span>
              <span className="pf-rating__num">
                <span className="pf-rating__sym" aria-hidden>
                  ₱
                </span>
                {bottomRow.c}
              </span>
              <span className="pf-rating__num pf-rating__cell--total">{bottomRow.t}</span>
            </div>
          </LeaderboardPinnedBlock>
        </div>
      )}

      <p className="pf-footer-link">
        <Link to="/help#global-rating">Как считается рейтинг</Link>
      </p>
    </div>
  )
}
