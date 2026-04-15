import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { cancelMarketplaceListing, fetchMyMarketplaceListings } from '../api/marketplace'
import { CardAchievementChips } from '../components/CardAchievementChips'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { rarityClass } from '../lib/rarity'
import { rarityScoreModifierLabel } from '../lib/rarity'

export function MyListingsPage() {
  const initData = useInitData()
  const qc = useQueryClient()

  const q = useQuery({
    queryKey: ['my-marketplace-listings', initData],
    queryFn: () => fetchMyMarketplaceListings(initData),
    enabled: !!initData,
  })

  const cancelM = useMutation({
    mutationFn: (listingId: number) => cancelMarketplaceListing(initData, listingId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['my-marketplace-listings'] })
      void qc.invalidateQueries({ queryKey: ['marketplace-listings'] })
      void qc.invalidateQueries({ queryKey: ['cards'] })
    },
    onError: (e: Error) => window.alert(e instanceof ApiError ? e.message : String(e)),
  })

  if (!initData) return <MissingInitDataNotice />

  return (
    <div className="pf-page">
      <PageHeader title="Мои листинги" backTo="/marketplace" backLabel="Маркетплейс" />

      <p className="pf-footer-link" style={{ marginBottom: 12 }}>
        <Link to="/marketplace">← К маркетплейсу</Link>
      </p>

      {q.isLoading && <p className="pf-muted">Загрузка…</p>}
      {q.isError && <p className="pf-err">{(q.error as Error).message}</p>}

      {q.data && q.data.length === 0 && <p className="pf-muted">Нет активных объявлений.</p>}

      <ul className="pf-collection-grid">
        {(q.data ?? []).map((row) => {
          const c = row.card
          const img = cardDisplayImageUrl({ playerPhotoUrl: c.playerPhotoUrl, imageUrl: null })
          const achForChips = c.achievements.map((a) => ({
            achievementId: a.achievementId,
            achievementName: a.name,
            bonusPoints: a.bonusPoints,
          }))
          return (
            <li key={row.listingId} className={`pf-collection-card pf-collection-card--${rarityClass(c.rarity)}`}>
              <div className="pf-collection-card__frame">
                <div className="pf-collection-card__open pf-marketplace-card__open">
                  {img ? (
                    <img src={img} alt="" className="pf-collection-card__img" />
                  ) : (
                    <div className="pf-collection-card__ph">{c.rarity}</div>
                  )}
                  <div className="pf-collection-card__cap">
                    <span className="pf-collection-card__name">{c.playerName}</span>
                    <span className="pf-collection-card__rarity">
                      {c.rarity}{' '}
                      <span className="pf-rarity-mod" title="Множитель очков в фэнтези">
                        {rarityScoreModifierLabel(c.rarity)}
                      </span>
                    </span>
                    <CardAchievementChips achievements={achForChips} max={4} />
                  </div>
                </div>
                <div className="pf-marketplace-card__meta">
                  <div className="pf-marketplace-card__price">{row.price}₣</div>
                  <p className="pf-muted" style={{ fontSize: '0.75rem', margin: '4px 0' }}>
                    Выставлено: {new Date(row.createdAt).toLocaleString()}
                  </p>
                  <button
                    type="button"
                    className="pf-btn pf-btn--small"
                    disabled={cancelM.isPending}
                    onClick={() => {
                      if (!window.confirm('Снять карту с продажи?')) return
                      cancelM.mutate(row.listingId)
                    }}
                  >
                    Снять с продажи
                  </button>
                </div>
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
