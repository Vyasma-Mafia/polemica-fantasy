import { useState } from 'react'
import { ApiError } from '../api/client'
import { useAchievements, useClaimAchievement, useSelectAchievementCardChoice } from '../api/achievements'
import type { AchievementItem, AchievementPendingCardChoice, AchievementReward } from '../api/types'
import { CardPerkChips } from '../components/CardPerkChips'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { skinClass } from '../lib/cardFrameClasses'
import { rarityClass } from '../lib/rarity'

export function AchievementsPage() {
  const initData = useInitData()
  const achievementsQ = useAchievements(initData)
  const claimM = useClaimAchievement(initData)
  const selectChoiceM = useSelectAchievementCardChoice(initData)
  const [claimingCode, setClaimingCode] = useState<string | null>(null)
  const [pendingChoicesByCode, setPendingChoicesByCode] = useState<Record<string, AchievementPendingCardChoice[]>>({})
  const [selectedOptions, setSelectedOptions] = useState<Record<string, string[]>>({})

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
      {selectChoiceM.isError && (
        <p className="pf-err">{selectChoiceM.error instanceof ApiError ? selectChoiceM.error.message : String(selectChoiceM.error)}</p>
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
                  pendingChoices={pendingChoicesByCode[achievement.code] ?? []}
                  selectedOptions={selectedOptions}
                  isSelecting={selectChoiceM.isPending}
                  onToggleOption={(rewardId, optionId) => {
                    const key = `${achievement.code}:${rewardId}`
                    const choice = pendingChoicesByCode[achievement.code]?.find((item) => item.rewardId === rewardId)
                    const selected = selectedOptions[key] ?? []
                    const next = selected.includes(optionId)
                      ? selected.filter((id) => id !== optionId)
                      : selected.length < (choice?.requiredCount ?? 0)
                        ? [...selected, optionId]
                        : selected
                    setSelectedOptions((prev) => ({ ...prev, [key]: next }))
                  }}
                  onSelectChoice={(rewardId) => {
                    const key = `${achievement.code}:${rewardId}`
                    selectChoiceM.mutate(
                      { code: achievement.code, rewardId, optionIds: selectedOptions[key] ?? [] },
                      {
                        onSuccess: (result) => {
                          setPendingChoicesByCode((prev) => ({
                            ...prev,
                            [achievement.code]: result.pendingChoices ?? [],
                          }))
                          setSelectedOptions((prev) => ({ ...prev, [key]: [] }))
                        },
                      },
                    )
                  }}
                  onClaim={() => {
                    setClaimingCode(achievement.code)
                    claimM.mutate(achievement.code, {
                      onSuccess: (result) => {
                        setPendingChoicesByCode((prev) => ({
                          ...prev,
                          [achievement.code]: result.pendingChoices ?? [],
                        }))
                      },
                      onSettled: () => setClaimingCode(null),
                    })
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
  pendingChoices,
  selectedOptions,
  isSelecting,
  onToggleOption,
  onSelectChoice,
  onClaim,
}: {
  achievement: AchievementItem
  isClaiming: boolean
  pendingChoices: AchievementPendingCardChoice[]
  selectedOptions: Record<string, string[]>
  isSelecting: boolean
  onToggleOption: (rewardId: number, optionId: string) => void
  onSelectChoice: (rewardId: number) => void
  onClaim: () => void
}) {
  const progress = Math.min(achievement.progressValue, achievement.targetValue)
  const pct = achievement.targetValue > 0 ? Math.min(100, Math.round((progress / achievement.targetValue) * 100)) : 0
  const claimable = achievement.state === 'COMPLETED_UNCLAIMED'
  const hasPendingChoices = pendingChoices.length > 0

  return (
    <article className={`pf-achievement pf-achievement--${rarityClass(achievement.rarity)} pf-achievement--${achievement.state.toLowerCase()}${hasPendingChoices ? ' pf-achievement--choice-open' : ''}`}>
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
        {pendingChoices.map((choice) => {
          const key = `${achievement.code}:${choice.rewardId}`
          const selected = selectedOptions[key] ?? []
          const ready = selected.length === choice.requiredCount
          return (
            <div key={choice.rewardId} className="pf-achievement-choice">
              <div className="pf-achievement-choice__head">
                <span>Выберите {choice.requiredCount} из {choice.options.length}</span>
                <button type="button" className="pf-btn pf-achievement-choice__submit" disabled={!ready || isSelecting} onClick={() => onSelectChoice(choice.rewardId)}>
                  Получить
                </button>
              </div>
              <div className="pf-achievement-choice__options">
                {choice.options.map((option) => {
                  const active = selected.includes(option.optionId)
                  const rc = rarityClass(option.rarity)
                  const skinMod = skinClass(option.skinCode)
                  return (
                    <button
                      type="button"
                      key={option.optionId}
                      className={`pf-achievement-choice-card pf-pack-open__summary-card pf-pack-open__summary-card--${rc}${skinMod ? ` pf-pack-open__summary-card${skinMod}` : ''}${active ? ' is-selected' : ''}`}
                      aria-pressed={active}
                      onClick={() => onToggleOption(choice.rewardId, option.optionId)}
                    >
                      <span className="pf-achievement-choice-card__check" aria-hidden="true">✓</span>
                      <span className="pf-pack-open__summary-card-frame">
                        {option.playerPhotoUrl ? (
                          <img src={option.playerPhotoUrl} alt="" className="pf-pack-open__summary-card-img" />
                        ) : (
                          <span className="pf-pack-open__summary-card-ph">{option.rarity}</span>
                        )}
                        <span className="pf-pack-open__summary-card-cap">
                          <span className="pf-pack-open__summary-card-name">{option.playerName}</span>
                          <span className="pf-pack-open__summary-card-rarity">{option.rarity}</span>
                          <CardPerkChips perks={option.perks} max={2} className="pf-card-perk-chips--compact pf-card-perk-chips--tight" />
                        </span>
                      </span>
                    </button>
                  )
                })}
              </div>
            </div>
          )
        })}
      </div>
      {!hasPendingChoices && (
        <button type="button" className="pf-btn pf-achievement__claim" disabled={!claimable || isClaiming} onClick={onClaim}>
          {achievement.state === 'CLAIMED' ? 'Получено' : isClaiming ? 'Получаем…' : 'Забрать'}
        </button>
      )}
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
      if (reward.type === 'COSMETIC_UNLOCK') return profileCosmeticLabel(reward)
      if (reward.type === 'BADGE_STYLE') return 'бейдж'
      if (reward.type === 'RANDOM_CARD') return cardRewardLabel(reward, 'карта')
      if (reward.type === 'CARD_CHOICE_ROLL') return cardRewardLabel(reward, 'выбор')
      return reward.code ?? reward.type
    })
    .join(' + ')
}

function profileCosmeticLabel(reward: AchievementReward): string {
  const code = reward.code?.toLowerCase() ?? ''
  if (code.includes('title')) return 'титул профиля'
  if (code.includes('accent') || code.includes('background')) return 'оформление профиля'
  return 'косметика профиля'
}

function cardRewardLabel(reward: AchievementReward, fallback: string): string {
  if (!reward.metadata) return fallback
  try {
    const metadata = JSON.parse(reward.metadata) as { rarity?: string; count?: number; options?: number; skinCode?: string }
    const styleSuffix = metadata.skinCode ? ' со стилем карты' : ''
    if (reward.type === 'CARD_CHOICE_ROLL' && metadata.count && metadata.options && metadata.rarity) {
      return `выбор ${metadata.count} из ${metadata.options} ${metadata.rarity}${styleSuffix}`
    }
    if (metadata.count && metadata.rarity) return `${metadata.count} ${metadata.rarity}${styleSuffix}`
  } catch {
    return fallback
  }
  return fallback
}
