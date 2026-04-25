import { ApiError } from '../api/client'
import {
  useTournamentSubscriptions,
  useUpdateTournamentSubscriptions,
} from '../api/notifications'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'

export function TournamentSubscriptionsPage() {
  const initData = useInitData()
  const subscriptionsQ = useTournamentSubscriptions()
  const updateM = useUpdateTournamentSubscriptions()

  if (!initData) return <MissingInitDataNotice />

  const available = subscriptionsQ.data?.availableTournaments ?? []
  const updateError =
    updateM.error instanceof ApiError ? updateM.error.message : updateM.error ? String(updateM.error) : null

  function setSubscribed(tournamentId: number, checked: boolean) {
    const nextTournamentIds = available
      .filter((entry) => (entry.tournamentId === tournamentId ? checked : entry.subscribed))
      .map((entry) => entry.tournamentId)
    updateM.mutate({ tournamentIds: nextTournamentIds })
  }

  return (
    <div className="pf-page">
      <PageHeader title="Подписки на турниры" backTo="/notifications" backLabel="Уведомления" />

      <p className="pf-muted">
        Если не выбран ни один - приходят уведомления обо всех турнирах.
      </p>

      {subscriptionsQ.isLoading && <p className="pf-muted">Загрузка турниров…</p>}
      {subscriptionsQ.isError && <p className="pf-err">{(subscriptionsQ.error as Error).message}</p>}
      {updateError && <p className="pf-err">{updateError}</p>}

      <ul className="pf-notify-list">
        {available.map((entry) => (
          <li key={entry.tournamentId} className="pf-notify-list__item">
            <label className="pf-checkbox-row">
              <input
                type="checkbox"
                checked={entry.subscribed}
                onChange={(e) => setSubscribed(entry.tournamentId, e.target.checked)}
                disabled={updateM.isPending}
              />
              <span>{entry.tournamentName}</span>
            </label>
          </li>
        ))}
      </ul>

      {!subscriptionsQ.isLoading && !subscriptionsQ.isError && available.length === 0 && (
        <p className="pf-muted">Нет активных турниров для подписки.</p>
      )}
    </div>
  )
}
