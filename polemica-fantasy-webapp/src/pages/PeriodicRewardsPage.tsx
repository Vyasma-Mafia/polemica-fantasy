import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchPeriodicRatingRewards, type PeriodicRatingReward } from '../api/periodicRatings'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'

const statusCopy: Record<PeriodicRatingReward['status'], string> = {
  AVAILABLE: 'Можно создать',
  DRAFT: 'Черновик',
  CHANGES_REQUESTED: 'Нужны изменения',
  REVIEW_REQUIRED: 'На проверке',
  FULFILLED: 'Карта получена',
  OVERDUE: 'Срок выбора прошёл, награда доступна',
  CANCELLED: 'Отменена',
}

function actionCopy(reward: PeriodicRatingReward) {
  if (reward.status === 'AVAILABLE') return 'Создать карту'
  if (reward.status === 'DRAFT') return 'Продолжить'
  if (reward.status === 'CHANGES_REQUESTED') return 'Исправить'
  if (reward.status === 'REVIEW_REQUIRED') return 'Посмотреть'
  if (reward.status === 'OVERDUE') return 'Завершить выбор'
  if (reward.status === 'CANCELLED') return 'Подробнее'
  return 'Открыть карту'
}

function RewardRow({ reward }: { reward: PeriodicRatingReward }) {
  const target = reward.status === 'FULFILLED' && reward.issuedUserCardId
    ? `/cards?cardId=${reward.issuedUserCardId}`
    : `/rating/rewards/${reward.id}/create`
  return <article className={`pf-reward-row pf-reward-row--${reward.status.toLowerCase()}`}>
    <div className="pf-reward-row__place">#{reward.rank}</div>
    <div className="pf-reward-row__body">
      <span className="pf-reward-row__status">{statusCopy[reward.status]}</span>
      <h3>{reward.periodTitle}</h3>
      <p>{reward.policy.rarity} · {reward.serial}</p>
      {reward.claimDeadline && reward.status !== 'FULFILLED' && <p>Ориентир выбора: {new Date(reward.claimDeadline).toLocaleDateString('ru-RU')}</p>}
      {reward.fantikiAmount > 0 && <p className="pf-reward-row__fantiki">+{reward.fantikiAmount}₣ {reward.fantikiGrantedAt ? 'уже начислены' : 'будут начислены'}</p>}
    </div>
    <Link className="pf-btn pf-btn--small" to={target}>{actionCopy(reward)}</Link>
  </article>
}

export function PeriodicRewardsPage() {
  const initData = useInitData()
  const rewards = useQuery({
    queryKey: ['periodic-rating-rewards', initData],
    queryFn: () => fetchPeriodicRatingRewards(initData!),
    enabled: !!initData,
  })
  if (!initData) return <MissingInitDataNotice />
  const current = rewards.data?.filter((reward) => reward.status !== 'FULFILLED') ?? []
  const history = rewards.data?.filter((reward) => reward.status === 'FULFILLED') ?? []
  return <div className="pf-page pf-rewards-page">
    <PageHeader title="Награды рейтинга" backTo="/rating" />
    <section className="pf-rewards-intro"><span>🏆</span><div><h2>Ваши трофейные карты</h2><p>Награда закреплена за вами. Срок в карточке — ориентир, она не пропадёт.</p></div></section>
    {rewards.isLoading && <p className="pf-loading">Загрузка наград…</p>}
    {rewards.isError && <div className="pf-period-rating-empty"><h2>Не удалось загрузить награды</h2><button className="pf-btn" onClick={() => rewards.refetch()}>Повторить</button></div>}
    {rewards.data && rewards.data.length === 0 && <div className="pf-period-rating-empty"><span>✨</span><h2>Пока нет наград</h2><p>Попадите в топ-10 рейтинга периода, чтобы получить уникальную карту.</p></div>}
    {current.length > 0 && <section className="pf-rewards-section"><h2>Требуют внимания</h2>{current.map((reward) => <RewardRow key={reward.id} reward={reward} />)}</section>}
    {history.length > 0 && <section className="pf-rewards-section"><h2>История</h2>{history.map((reward) => <RewardRow key={reward.id} reward={reward} />)}</section>}
  </div>
}
