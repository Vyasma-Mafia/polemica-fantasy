import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ApiError, apiGet } from '../api/client'
import {
  complainMarketplaceTransaction,
  fetchMarketplaceTransactionDetail,
} from '../api/marketplace'
import type { UserProfile } from '../api/types'
import { CardAchievementChips } from '../components/CardAchievementChips'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { rarityClass, rarityScoreModifierLabel } from '../lib/rarity'
import { formatDateShortWithTime } from '../lib/tournamentDates'

type BackState = {
  backTo?: string
  backLabel?: string
} | null

function complainErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return String(error)
  if (error.status === 429) return 'Лимит жалоб исчерпан на сегодня.'
  if (error.status === 409) return 'Вы уже пожаловались на эту сделку.'
  if (error.status === 400) return error.message || 'Жалобу нельзя подать для этой сделки.'
  return error.message || 'Не удалось отправить жалобу.'
}

export function TransactionDetailPage() {
  const initData = useInitData()
  const queryClient = useQueryClient()
  const location = useLocation()
  const [complaintConfirmOpen, setComplaintConfirmOpen] = useState(false)
  const { listingId } = useParams<{ listingId: string }>()
  const numericListingId = Number(listingId)
  const backState = (location.state as BackState) ?? null
  const backTo = backState?.backTo ?? '/marketplace'
  const backLabel = backState?.backLabel ?? 'Маркетплейс'

  const transactionQ = useQuery({
    queryKey: ['marketplace', 'transaction', numericListingId, initData],
    queryFn: () => fetchMarketplaceTransactionDetail(initData, numericListingId),
    enabled: !!initData && Number.isFinite(numericListingId),
  })

  const meQ = useQuery({
    queryKey: ['me', initData],
    queryFn: () => apiGet<UserProfile>('/api/v1/me', initData),
    enabled: !!initData,
  })

  const complainM = useMutation({
    mutationFn: () => complainMarketplaceTransaction(initData, numericListingId),
    onSuccess: async (result) => {
      setComplaintConfirmOpen(false)
      await queryClient.invalidateQueries({
        queryKey: ['marketplace', 'transaction', numericListingId],
      })
      window.alert(`Жалоба принята. Осталось ${result.remainingToday} жалоб сегодня.`)
    },
    onError: (error) => window.alert(complainErrorMessage(error)),
  })

  if (!initData) return <MissingInitDataNotice />
  if (!Number.isFinite(numericListingId)) return <p className="pf-err">Некорректный идентификатор сделки.</p>
  if (transactionQ.isLoading) return <p className="pf-loading">Загрузка сделки…</p>
  if (transactionQ.isError) return <p className="pf-err">{(transactionQ.error as Error).message}</p>

  const transaction = transactionQ.data
  if (!transaction) return <p className="pf-err">Сделка не найдена.</p>
  const cardImageUrl = cardDisplayImageUrl({
    playerPhotoUrl: transaction.card.playerPhotoUrl,
    imageUrl: null,
  })
  const achievements = transaction.card.achievements.map((item) => ({
    achievementId: item.achievementId,
    achievementName: item.name,
    bonusPoints: item.bonusPoints,
  }))

  const currentTelegramId = meQ.data?.telegramId ?? null
  const isParticipant =
    currentTelegramId != null &&
    (currentTelegramId === transaction.seller.telegramId ||
      currentTelegramId === transaction.buyer.telegramId)
  const complainDisabled =
    complainM.isPending ||
    meQ.isLoading ||
    transaction.complaint.userAlreadyComplained ||
    isParticipant ||
    transaction.sanction != null

  return (
    <div className="pf-page">
      <PageHeader title="Сделка" subtitle={transaction.card.playerName} backTo={backTo} backLabel={backLabel} />

      <div className="pf-transaction-detail">
        <section
          className={`pf-collection-card pf-transaction-detail__card pf-collection-card--${rarityClass(transaction.card.rarity)}`}
        >
          <div className="pf-collection-card__frame pf-transaction-detail__card-frame">
            <div className="pf-collection-card__open">
              {cardImageUrl ? (
                <img src={cardImageUrl} alt={transaction.card.playerName} className="pf-collection-card__img" />
              ) : (
                <div className="pf-collection-card__ph">{transaction.card.rarity}</div>
              )}
              <div className="pf-collection-card__cap pf-transaction-detail__card-cap">
                <span className="pf-collection-card__name pf-transaction-detail__card-name">{transaction.card.playerName}</span>
                <span className="pf-collection-card__rarity pf-transaction-detail__card-rarity">
                  {transaction.card.rarity}{' '}
                  <span className="pf-rarity-mod" title="Множитель очков в фэнтези">
                    {rarityScoreModifierLabel(transaction.card.rarity)}
                  </span>
                </span>
                <CardAchievementChips achievements={achievements} max={6} className="pf-card-ach-chips--compact" />
              </div>
            </div>
          </div>
        </section>

        <section className="pf-transaction-detail__info">
          <h2 className="pf-section-title">Детали сделки</h2>
          <p className="pf-transaction-detail__line">
            <span className="pf-muted">Цена:</span> <strong>{transaction.price}₣</strong>
          </p>
          <p className="pf-transaction-detail__line">
            <span className="pf-muted">Комиссия:</span> {transaction.commission}₣
          </p>
          <p className="pf-transaction-detail__line">
            <span className="pf-muted">Продавец получил:</span> {transaction.sellerReceived}₣
          </p>
          <p className="pf-transaction-detail__line">
            <span className="pf-muted">Дата:</span> {formatDateShortWithTime(new Date(transaction.soldAt))}
          </p>
        </section>

        <section className="pf-transaction-detail__participants">
          <h2 className="pf-section-title">Участники</h2>
          <div className="pf-transaction-detail__participant">
            <span className="pf-muted">Продавец:</span>{' '}
            <Link to={`/players/${transaction.seller.telegramId}`}>{transaction.seller.displayName}</Link>
          </div>
          <div className="pf-transaction-detail__participant">
            <span className="pf-muted">Покупатель:</span>{' '}
            <Link to={`/players/${transaction.buyer.telegramId}`}>{transaction.buyer.displayName}</Link>
          </div>
        </section>

        {transaction.sanction && (
          <section className="pf-transaction-detail__sanction">
            <p className="pf-transaction-detail__sanction-title">⚠️ Сделка признана нерыночной</p>
            <p className="pf-transaction-detail__line">
              <span className="pf-muted">Причина:</span> {transaction.sanction.reason}
            </p>
            <p className="pf-transaction-detail__line">
              <span className="pf-muted">Дата:</span>{' '}
              {formatDateShortWithTime(new Date(transaction.sanction.sanctionedAt))}
            </p>
          </section>
        )}

        <section className="pf-transaction-detail__complaint">
          <h2 className="pf-section-title">Жалобы</h2>
          <p className="pf-transaction-detail__line">
            <strong>{transaction.complaint.totalComplaints}</strong> жалоб
          </p>
          <button
            type="button"
            className="pf-btn pf-btn--small pf-btn--outline pf-complaint-btn"
            disabled={complainDisabled}
            onClick={() => setComplaintConfirmOpen(true)}
          >
            {complainM.isPending ? 'Отправляем…' : 'Пожаловаться'}
          </button>
          {transaction.complaint.userAlreadyComplained && (
            <p className="pf-muted">Вы уже пожаловались на эту сделку.</p>
          )}
          {isParticipant && <p className="pf-muted">Нельзя жаловаться на свою сделку.</p>}
          {transaction.sanction && <p className="pf-muted">Сделка уже санкционирована.</p>}
        </section>
      </div>

      {complaintConfirmOpen && (
        <div
          className="pf-modal-backdrop"
          role="dialog"
          aria-modal
          aria-label="Подтверждение жалобы"
          onClick={() => setComplaintConfirmOpen(false)}
        >
          <div className="pf-modal pf-modal--narrow" onClick={(e) => e.stopPropagation()}>
            <h3 className="pf-modal__title">Отправить жалобу?</h3>
            <p className="pf-muted">Администрация рассматривает жалобы только на транзакции маркетплейса.</p>
            <ul className="pf-transaction-detail__complaint-notes">
              <li>Действует дневной лимит жалоб. Если лимит исчерпан, отправка будет отклонена.</li>
              <li>Если сделку признают нерыночной, жалобщикам может быть начислена награда.</li>
            </ul>
            <div className="pf-modal__actions">
              <button
                type="button"
                className="pf-btn pf-btn--ghost"
                disabled={complainM.isPending}
                onClick={() => setComplaintConfirmOpen(false)}
              >
                Отмена
              </button>
              <button
                type="button"
                className="pf-btn"
                disabled={complainM.isPending}
                onClick={() => complainM.mutate()}
              >
                {complainM.isPending ? 'Отправляем…' : 'Отправить жалобу'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
