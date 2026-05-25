import { useState } from 'react'
import { ApiError } from '../api/client'
import { useAchievements, useClaimAchievement } from '../api/achievements'
import type { AchievementItem, AchievementReward } from '../api/types'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { rarityClass } from '../lib/rarity'

export function AchievementsPage() {
  const initData = useInitData()
  const achievementsQ = useAchievements(initData)
  const claimM = useClaimAchievement(initData)
  const [claimingCode, setClaimingCode] = useState<string | null>(null)

  if (!initData) return <MissingInitDataNotice />
  if (achievementsQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (achievementsQ.isError) return <p className="pf-err">{(achievementsQ.error as Error).message}</p>

  const catalog = achievementsQ.data

  return (
    <div className="pf-page pf-achievements">
      <PageHeader title="Достижения" backTo="/" backLabel="Турниры" />

      {catalog && (
        <section className="pf-achievements-summary" aria-label="Сводка достижений">
          <div>
            <strong>{catalog.summary.completed}</strong>
            <span>выполнено</span>
          </div>
          <div>
            <strong>{catalog.summary.claimed}</strong>
            <span>получено</span>
          </div>
          <div>
            <strong>{catalog.summary.unclaimedRewards}</strong>
            <span>к получению</span>
          </div>
        </section>
      )}

      {claimM.isError && (
        <p className="pf-err">{claimM.error instanceof ApiError ? claimM.error.message : String(claimM.error)}</p>
      )}

      <div className="pf-achievements-categories">
        {(catalog?.categories ?? []).map((category) => (
          <section key={category.code} className="pf-achievements-category">
            <h2>{category.name}</h2>
            <div className="pf-achievements-list">
              {category.achievements.map((achievement) => (
                <AchievementRow
                  key={achievement.code}
                  achievement={achievement}
                  isClaiming={claimM.isPending && claimingCode === achievement.code}
                  onClaim={() => {
                    setClaimingCode(achievement.code)
                    claimM.mutate(achievement.code, { onSettled: () => setClaimingCode(null) })
                  }}
                />
              ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  )
}

function AchievementRow({
  achievement,
  isClaiming,
  onClaim,
}: {
  achievement: AchievementItem
  isClaiming: boolean
  onClaim: () => void
}) {
  const progress = Math.min(achievement.progressValue, achievement.targetValue)
  const pct = achievement.targetValue > 0 ? Math.min(100, Math.round((progress / achievement.targetValue) * 100)) : 0
  const claimable = achievement.state === 'COMPLETED_UNCLAIMED'

  return (
    <article className={`pf-achievement pf-achievement--${rarityClass(achievement.rarity)} pf-achievement--${achievement.state.toLowerCase()}`}>
      <div className="pf-achievement__main">
        <div className="pf-achievement__title-row">
          <h3>{achievement.title}</h3>
          <span className="pf-achievement__state">{stateLabel(achievement.state)}</span>
        </div>
        {achievement.description && <p>{achievement.description}</p>}
        <div className="pf-achievement__progress" aria-label={`${progress} из ${achievement.targetValue}`}>
          <span style={{ width: `${pct}%` }} />
        </div>
        <div className="pf-achievement__meta">
          <span>
            {achievement.progressValue} / {achievement.targetValue}
          </span>
          <span>{formatRewards(achievement.rewards)}</span>
        </div>
      </div>
      <button type="button" className="pf-btn pf-achievement__claim" disabled={!claimable || isClaiming} onClick={onClaim}>
        {achievement.state === 'CLAIMED' ? 'Получено' : isClaiming ? 'Получаем…' : 'Забрать'}
      </button>
    </article>
  )
}

function stateLabel(state: AchievementItem['state']): string {
  switch (state) {
    case 'CLAIMED':
      return 'Получено'
    case 'COMPLETED_UNCLAIMED':
      return 'Готово'
    case 'IN_PROGRESS':
      return 'В процессе'
    default:
      return 'Закрыто'
  }
}

function formatRewards(rewards: AchievementReward[]): string {
  if (rewards.length === 0) return 'Без награды'
  return rewards
    .map((reward) => {
      if (reward.type === 'FANTIKI') return `${(reward.amount ?? 0).toLocaleString('ru-RU')} фантиков`
      if (reward.type === 'PROFILE_FRAME') return 'рамка профиля'
      if (reward.type === 'COSMETIC_UNLOCK' || reward.type === 'CARD_SKIN_UNLOCK') return 'косметика'
      if (reward.type === 'BADGE_STYLE') return 'бейдж'
      return reward.code ?? reward.type
    })
    .join(' + ')
}
