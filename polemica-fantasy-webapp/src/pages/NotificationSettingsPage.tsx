import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import {
  useMarketplaceWatches,
  useNotificationSettings,
  useTournamentSubscriptions,
  useUpdateNotificationSettings,
} from '../api/notifications'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import type { NotificationCategory, NotificationCategoryDto } from '../types/notifications'

const GROUPS: Array<{ title: string; categories: NotificationCategory[] }> = [
  {
    title: 'Турниры и серии',
    categories: ['SERIES_START', 'TEAM_DEADLINE_REMINDER', 'SERIES_FINALIZED', 'SERIES_ROSTER_CHANGE'],
  },
  {
    title: 'Маркетплейс',
    categories: ['MARKETPLACE_SALE', 'MARKETPLACE_WATCH'],
  },
  {
    title: 'Системные',
    categories: ['ADMIN_BROADCAST', 'PAIR_BAN'],
  },
]

const CATEGORY_LABELS: Record<NotificationCategory, string> = {
  ADMIN_BROADCAST: 'Сообщения от администрации',
  SERIES_START: 'Старт серии',
  TEAM_DEADLINE_REMINDER: 'Напоминание о дедлайне команды',
  SERIES_FINALIZED: 'Результаты серии',
  SERIES_ROSTER_CHANGE: 'Замена карт в составе',
  MARKETPLACE_SALE: 'Продажа вашей карты',
  MARKETPLACE_WATCH: 'Отслеживание карт',
  PAIR_BAN: 'Уведомления о санкциях',
}

function subscriptionsHint(
  data:
    | {
        subscriptions: { tournamentId: number }[]
        availableTournaments: { tournamentId: number }[]
      }
    | undefined,
  loading: boolean,
) {
  if (loading) return '...'
  if (!data) return 'не загружено'
  if (data.subscriptions.length === 0) return 'все турниры'
  const total = data.availableTournaments.length
  if (total > 0) return `${data.subscriptions.length} из ${total}`
  return String(data.subscriptions.length)
}

function watchesHint(
  data: { watches: { id: number }[]; maxWatches: number } | undefined,
  loading: boolean,
) {
  if (loading) return '...'
  if (!data) return '0'
  return `${data.watches.length}`
}

export function NotificationSettingsPage() {
  const initData = useInitData()
  const settingsQ = useNotificationSettings()
  const updateM = useUpdateNotificationSettings()
  const subscriptionsQ = useTournamentSubscriptions()
  const watchesQ = useMarketplaceWatches()

  if (!initData) return <MissingInitDataNotice />

  const categoriesById = new Map(
    (settingsQ.data?.categories ?? []).map((item) => [item.category, item] as const),
  )

  function toggleCategory(item: NotificationCategoryDto) {
    if (!item.toggleable) return
    updateM.mutate({
      categories: {
        [item.category]: !item.enabled,
      },
    })
  }

  const updateError =
    updateM.error instanceof ApiError ? updateM.error.message : updateM.error ? String(updateM.error) : null

  return (
    <div className="pf-page">
      <PageHeader title="Уведомления" backTo="/" backLabel="Турниры" />

      {settingsQ.isLoading && <p className="pf-muted">Загрузка настроек…</p>}
      {settingsQ.isError && <p className="pf-err">{(settingsQ.error as Error).message}</p>}
      {updateError && <p className="pf-err">{updateError}</p>}

      {GROUPS.map((group) => {
        const items = group.categories
          .map((category) => categoriesById.get(category))
          .filter((item): item is NotificationCategoryDto => Boolean(item))
        if (items.length === 0) return null

        return (
          <section key={group.title} className="pf-notify-group">
            <h2 className="pf-notify-group__title">{group.title}</h2>
            <ul className="pf-notify-list">
              {items.map((item) => (
                <li key={item.category} className="pf-notify-list__item">
                  <div className="pf-notify-row">
                    <div className="pf-notify-row__copy">
                      <div className="pf-notify-row__label">
                        {CATEGORY_LABELS[item.category as NotificationCategory] ?? item.description}
                      </div>
                    </div>
                    {item.toggleable ? (
                      <label className="pf-notify-switch">
                        <input
                          type="checkbox"
                          checked={item.enabled}
                          onChange={() => toggleCategory(item)}
                          disabled={updateM.isPending}
                          aria-label={item.description}
                        />
                        <span className="pf-notify-switch__track" />
                      </label>
                    ) : (
                      <span className="pf-notify-row__lock">🔒 Всегда вкл</span>
                    )}
                  </div>

                  {item.category === 'SERIES_START' && (
                    <Link to="/notifications/tournaments" className="pf-notify-link-row">
                      <span>Подписки на турниры: {subscriptionsHint(subscriptionsQ.data, subscriptionsQ.isLoading)}</span>
                      <span aria-hidden>›</span>
                    </Link>
                  )}

                  {item.category === 'MARKETPLACE_WATCH' && (
                    <Link to="/notifications/marketplace-watches" className="pf-notify-link-row">
                      <span>Фильтры отслеживания ({watchesHint(watchesQ.data, watchesQ.isLoading)})</span>
                      <span aria-hidden>›</span>
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}
