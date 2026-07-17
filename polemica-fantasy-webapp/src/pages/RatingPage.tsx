import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMarkOnboardingStep } from '../api/antiChurn'
import { fetchGlobalRating } from '../api/rating'
import { LeaderboardPinnedBlock } from '../components/LeaderboardPinnedBlock'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { UserFrameName } from '../components/UserFrameName'
import { useInitData } from '../context/useInitData'
import { formatUserDisplayName } from '../lib/userDisplayName'
import type { RatingEntry } from '../api/types'
import {
  fetchMyPeriodicRating,
  fetchPeriodicRatingPeriods,
  fetchPeriodicRatingLeaderboard,
  fetchPeriodicRatingRewards,
  type PeriodicRatingEntry,
} from '../api/periodicRatings'

const FAR_RANK_THRESHOLD = 20

function formatValue(n: number): string {
  return n.toLocaleString('ru-RU')
}

function ratingRow(e: RatingEntry): {
  rank: number
  telegramId: number
  name: string
  profileFrameCode: string | null
  f: string
  c: string
  p: string
  t: string
} {
  return {
    rank: e.rank,
    telegramId: e.user.telegramId,
    name: formatUserDisplayName(e.user),
    profileFrameCode: e.user.profileFrameCode,
    f: formatValue(e.fantikiBalance),
    c: formatValue(e.cardsValue),
    p: formatValue(e.prizeWinnings),
    t: formatValue(e.totalValue),
  }
}

function RatingTableRow({
  row,
  current,
}: {
  row: ReturnType<typeof ratingRow>
  current: boolean
}) {
  return (
    <tr className={current ? 'pf-rating-row pf-rating-row--current' : 'pf-rating-row'}>
      <td className="pf-rating__cell pf-rating__cell--rank">#{row.rank}</td>
      <td className="pf-rating__cell pf-rating__cell--name">
        <Link to={`/players/${row.telegramId}`} className="pf-rating__name-link">
          <UserFrameName profileFrameCode={row.profileFrameCode}>{row.name}</UserFrameName>
        </Link>
      </td>
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
      <td className="pf-rating__cell pf-rating__num" title="Призовые за серии (фантики)">
        <span className="pf-rating__sym" aria-hidden>
          ₣
        </span>
        {row.p}
      </td>
      <td className="pf-rating__cell pf-rating__num pf-rating__cell--total" title="Всего">
        {row.t}
      </td>
    </tr>
  )
}

function GlobalRating() {
  const initData = useInitData()
  useMarkOnboardingStep('VIEW_RESULTS')
  const q = useQuery({
    queryKey: ['global-rating', initData],
    queryFn: () => fetchGlobalRating(initData!),
    enabled: !!initData,
  })

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
              <th className="pf-rating__th--num" scope="col" title="Сумма призовых за серии">
                Призовые
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
              <Link to={`/players/${bottomRow.telegramId}`} className="pf-rating__name-link pf-rating__name-truncate">
                <UserFrameName profileFrameCode={bottomRow.profileFrameCode}>{bottomRow.name}</UserFrameName>
              </Link>
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
              <span className="pf-rating__num">
                <span className="pf-rating__sym" aria-hidden>
                  ₣
                </span>
                {bottomRow.p}
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

function periodName(entry: PeriodicRatingEntry) {
  return entry.user.displayName || entry.user.firstName || entry.user.username || `Игрок ${entry.user.telegramId}`
}

function PeriodRewardBanner({ count }: { count: number }) {
  if (count === 0) return null
  return <Link className="pf-period-reward-banner" to="/rating/rewards">
    <span className="pf-period-reward-banner__icon">🏆</span>
    <span><b>{count === 1 ? 'Ваша награда ждёт' : `У вас ${count} награды`}</b><small>Выберите игрока, перки и уникальный скин</small></span>
    <i>{count}</i><strong>›</strong>
  </Link>
}

function PeriodRating() {
  const initData = useInitData()!
  const [breakdownOpen, setBreakdownOpen] = useState(false)
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null)
  const rewards = useQuery({
    queryKey: ['periodic-rating-rewards', initData],
    queryFn: () => fetchPeriodicRatingRewards(initData),
  })
  const periods = useQuery({
    queryKey: ['periodic-rating-periods', initData],
    queryFn: () => fetchPeriodicRatingPeriods(initData),
  })
  const periodId = selectedPeriodId ?? periods.data?.[0]?.id
  const leaderboard = useQuery({
    queryKey: ['periodic-rating-leaderboard', periodId, initData],
    queryFn: () => fetchPeriodicRatingLeaderboard(periodId!, initData),
    enabled: periodId != null,
  })
  const me = useQuery({
    queryKey: ['periodic-rating-me', periodId, initData],
    queryFn: () => fetchMyPeriodicRating(periodId!, initData),
    enabled: periodId != null && breakdownOpen,
  })

  if (periods.isLoading || leaderboard.isLoading) return <p className="pf-loading">Загрузка рейтинга периода…</p>
  if (periods.data?.length === 0) {
    const pendingCount = rewards.data?.filter((reward) => reward.status !== 'FULFILLED').length ?? 0
    return <><PeriodRewardBanner count={pendingCount} /><div className="pf-period-rating-empty"><span>🏆</span><h2>Активного периода пока нет</h2><p>Новый рейтинг появится после открытия следующего периода.</p></div></>
  }
  if (periods.isError || leaderboard.isError) {
    return <div className="pf-period-rating-empty"><h2>Не удалось загрузить рейтинг</h2><button className="pf-btn" onClick={() => { periods.refetch(); leaderboard.refetch() }}>Повторить</button></div>
  }

  const data = leaderboard.data!
  const period = data.period
  const date = (value: string) => new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short', timeZone: period.timezone }).format(new Date(value))
  const visibleIds = new Set(data.entries.map((entry) => entry.user.telegramId))
  const pinned = data.currentUser && !visibleIds.has(data.currentUser.user.telegramId) ? data.currentUser : null
  const pendingRewards = rewards.data?.filter((reward) => reward.status !== 'FULFILLED') ?? []

  return <div className="pf-period-rating">
    <PeriodRewardBanner count={pendingRewards.length} />
    <label className="pf-period-rating-picker">
      <span>Период</span>
      <select value={periodId} onChange={(event) => { setSelectedPeriodId(Number(event.target.value)); setBreakdownOpen(false) }}>
        {periods.data!.map((item) => <option value={item.id} key={item.id}>{item.title}{item.status === 'FINALIZED' ? ' · итоги' : item.status === 'SETTLING' ? ' · подсчёт' : ' · сейчас'}</option>)}
      </select>
    </label>
    <section className="pf-period-rating-hero">
      <div className="pf-period-rating-hero__top"><span className={`pf-period-rating-status pf-period-rating-status--${period.status.toLowerCase()}`}>{period.status === 'OPEN' ? 'Идёт сейчас' : period.status === 'SETTLING' ? 'Подводим итоги' : 'Итоги'}</span><span>{date(period.startsAt)} — {date(period.endsAt)}</span></div>
      <h2>{period.title}</h2>
      <p>Суммируем ваш итоговый результат в каждой финализированной серии основной лиги.</p>
      <div className="pf-period-rating-stats"><span><b>{period.seriesCount}</b> серий</span><span><b>{period.participantCount}</b> участников</span></div>
    </section>

    {data.blockersCount > 0 && <div className="pf-period-rating-notice">⏳ {data.blockersCount} {data.blockersCount === 1 ? 'серия ожидает' : 'серии ожидают'} финализации. Рейтинг предварительный.</div>}

    <div className="pf-period-rating-list">
      {data.entries.map((entry) => <Link to={`/players/${entry.user.telegramId}`} className={`pf-period-rating-row${entry.user.telegramId === data.currentUser?.user.telegramId ? ' pf-period-rating-row--me' : ''}`} key={entry.user.telegramId}>
        <span className="pf-period-rating-row__rank">#{entry.rank}</span>
        <span className="pf-period-rating-row__name"><UserFrameName profileFrameCode={entry.user.profileFrameCode}>{periodName(entry)}</UserFrameName><small>{entry.seriesCount} сер.</small></span>
        <b>{entry.totalScore.toLocaleString('ru-RU', { minimumFractionDigits: 2 })}</b>
      </Link>)}
      {data.entries.length === 0 && <p className="pf-muted">В зачёте пока нет финализированных серий.</p>}
    </div>

    {pinned && <div className="pf-period-rating-pinned"><span>Ваше место</span><b>#{pinned.rank}</b><strong>{pinned.totalScore.toLocaleString('ru-RU', { minimumFractionDigits: 2 })}</strong></div>}

    {data.currentUser && <section className="pf-period-rating-me">
      <button className="pf-period-rating-me__button" onClick={() => setBreakdownOpen((open) => !open)}>{breakdownOpen ? 'Скрыть мои серии' : 'Показать мои серии'}</button>
      {breakdownOpen && me.isLoading && <p className="pf-loading">Загрузка…</p>}
      {breakdownOpen && me.isError && <p className="pf-err">Не удалось загрузить серии.</p>}
      {breakdownOpen && me.data && <div className="pf-period-contributions">{me.data.contributions.map((c) => <Link to={`/series/${c.seriesId}`} key={c.seriesId}><span>{c.tournamentName}<b>{c.seriesName}</b></span><span>#{c.seriesRank} из {c.participantsCount}<strong>{c.score.toLocaleString('ru-RU', { minimumFractionDigits: 2 })}</strong></span></Link>)}</div>}
    </section>}
  </div>
}

export function RatingPage() {
  const initData = useInitData()
  const [tab, setTab] = useState<'period' | 'global'>('period')
  useMarkOnboardingStep('VIEW_RESULTS')
  if (!initData) return <MissingInitDataNotice />
  return <div className="pf-page">
    <PageHeader title="Рейтинг" backTo="/" />
    <div className="pf-rating-tabs" role="tablist">
      <button className={tab === 'period' ? 'is-active' : ''} onClick={() => setTab('period')}>Рейтинг периода</button>
      <button className={tab === 'global' ? 'is-active' : ''} onClick={() => setTab('global')}>Общий рейтинг</button>
    </div>
    {tab === 'period' ? <PeriodRating /> : <GlobalRating />}
  </div>
}
