import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAchievements, useClaimAchievement, useSelectAchievementCardChoice } from '../api/achievements'
import type { AchievementCategory, AchievementItem, AchievementPendingCardChoice, AchievementReward } from '../api/types'
import { CardPerkChips } from '../components/CardPerkChips'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { PlayerImage } from '../components/PlayerImage'
import { useInitData } from '../context/useInitData'
import { skinClass } from '../lib/cardFrameClasses'
import { rarityClass } from '../lib/rarity'

type AchievementTab = 'claimable' | 'unfinished' | 'claimed'

type IndexedAchievement = {
  achievement: AchievementItem
  categoryCode: string
  categoryName: string
  originalIndex: number
}

type VisibleAchievementCategory = {
  code: string
  name: string
  achievements: AchievementItem[]
}

const ACHIEVEMENT_TABS: { id: AchievementTab; label: string }[] = [
  { id: 'claimable', label: 'Забрать' },
  { id: 'unfinished', label: 'Не завершено' },
  { id: 'claimed', label: 'Получено' },
]

const CATEGORY_ORDER = ['PARTICIPATION', 'BUDGET', 'RESULTS', 'COLLECTION', 'PACKS', 'MARKETPLACE', 'SOCIAL', 'SPECIAL']
const RARITY_ORDER: Record<AchievementItem['rarity'], number> = {
  LEGENDARY: 4,
  EPIC: 3,
  RARE: 2,
  COMMON: 1,
}

export function AchievementsPage() {
  const initData = useInitData()
  const achievementsQ = useAchievements(initData)
  const claimM = useClaimAchievement(initData)
  const selectChoiceM = useSelectAchievementCardChoice(initData)
  const [selectedTab, setSelectedTab] = useState<AchievementTab | null>(null)
  const [claimingCode, setClaimingCode] = useState<string | null>(null)
  const [profileCustomizationReady, setProfileCustomizationReady] = useState(false)
  const [pendingChoicesByCode, setPendingChoicesByCode] = useState<Record<string, AchievementPendingCardChoice[]>>({})
  const [selectedOptions, setSelectedOptions] = useState<Record<string, string[]>>({})

  if (!initData) return <MissingInitDataNotice />
  if (achievementsQ.isLoading) return <p className="pf-loading">Загружаем достижения…</p>
  if (achievementsQ.isError) {
    return (
      <div className="pf-page pf-achievements">
        <PageHeader title="Достижения" backTo="/" backLabel="Турниры" />
        <p className="pf-err">{(achievementsQ.error as Error).message}</p>
        <button type="button" className="pf-btn" onClick={() => void achievementsQ.refetch()}>
          Повторить
        </button>
      </div>
    )
  }

  const catalog = achievementsQ.data
  const indexedAchievements = catalog ? flattenAchievements(catalog.categories) : []
  const counts = countTabs(indexedAchievements, pendingChoicesByCode)
  const defaultTab: AchievementTab = counts.claimable > 0 ? 'claimable' : 'unfinished'
  const activeTab = selectedTab ?? defaultTab
  const visibleCategories = buildVisibleCategories(indexedAchievements, activeTab, pendingChoicesByCode)
  const showCategoryHeaders = visibleCategories.length > 1

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
      {profileCustomizationReady && (
        <div className="pf-achievements-customization-cta">
          <span>Новое оформление доступно в витрине профиля.</span>
          <Link to="/profile-customization" className="pf-btn pf-btn--small">
            Настроить витрину
          </Link>
        </div>
      )}

      <div className="pf-tabs pf-tabs--scroll pf-achievements-tabs" role="group" aria-label="Фильтр достижений">
        {ACHIEVEMENT_TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            aria-pressed={activeTab === tab.id}
            className={`pf-tab ${activeTab === tab.id ? 'pf-tab--active' : ''}`}
            onClick={() => setSelectedTab(tab.id)}
          >
            {tab.label}
            <span className="pf-achievements-tab-count">{counts[tab.id]}</span>
          </button>
        ))}
      </div>

      <div className="pf-achievements-categories">
        {visibleCategories.length === 0 && <AchievementEmptyState tab={activeTab} />}
        {visibleCategories.map((category) => (
          <section key={category.code} className="pf-achievements-category">
            {showCategoryHeaders && <h2>{category.name}</h2>}
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
                          if (hasProfileCosmeticUnlock(result.cosmeticUnlocks)) setProfileCustomizationReady(true)
                          if (result.claimedAt) setSelectedTab(null)
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
                        if (hasProfileCosmeticUnlock(result.cosmeticUnlocks)) setProfileCustomizationReady(true)
                        if (result.claimedAt) setSelectedTab(null)
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

function AchievementEmptyState({ tab }: { tab: AchievementTab }) {
  const copy =
    tab === 'claimable'
      ? {
          title: 'Нет наград к получению',
          body: 'Новые награды появятся здесь, когда достижение будет выполнено.',
        }
      : tab === 'claimed'
        ? {
            title: 'Пока ничего не получено',
            body: 'Заберите первую награду, когда выполните достижение.',
          }
        : {
            title: 'Все видимые достижения закрыты',
            body: 'Полученные награды лежат во вкладке «Получено».',
          }

  return (
    <div className="pf-achievements-empty">
      <strong>{copy.title}</strong>
      <span>{copy.body}</span>
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
  const choiceReward = hasCardChoiceReward(achievement.rewards)

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
                        <PlayerImage
                          src={option.playerPhotoUrl}
                          seedId={option.fantasyPlayerId}
                          variant="card"
                          className="pf-pack-open__summary-card-img"
                        />
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
          {achievement.state === 'CLAIMED' ? 'Получено' : isClaiming ? 'Получаем…' : choiceReward ? 'Выбрать' : 'Забрать'}
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
      return 'Не начато'
  }
}

function flattenAchievements(categories: AchievementCategory[]): IndexedAchievement[] {
  const items: IndexedAchievement[] = []
  categories.forEach((category, categoryIndex) => {
    category.achievements.forEach((achievement, achievementIndex) => {
      items.push({
        achievement,
        categoryCode: category.code,
        categoryName: categoryDisplayName(category),
        originalIndex: categoryIndex * 1000 + achievementIndex,
      })
    })
  })
  return items
}

function countTabs(items: IndexedAchievement[], pendingChoicesByCode: Record<string, AchievementPendingCardChoice[]>) {
  return items.reduce<Record<AchievementTab, number>>(
    (counts, item) => {
      const tab = tabForAchievement(item.achievement, pendingChoicesByCode[item.achievement.code] ?? [])
      counts[tab] += 1
      return counts
    },
    { claimable: 0, unfinished: 0, claimed: 0 },
  )
}

function buildVisibleCategories(
  items: IndexedAchievement[],
  activeTab: AchievementTab,
  pendingChoicesByCode: Record<string, AchievementPendingCardChoice[]>,
): VisibleAchievementCategory[] {
  const categories = new Map<string, { name: string; order: number; items: IndexedAchievement[] }>()
  items.forEach((item) => {
    const pendingChoices = pendingChoicesByCode[item.achievement.code] ?? []
    if (tabForAchievement(item.achievement, pendingChoices) !== activeTab) return
    const current = categories.get(item.categoryCode)
    if (current) {
      current.items.push(item)
    } else {
      categories.set(item.categoryCode, {
        name: item.categoryName,
        order: categoryOrder(item.categoryCode, item.originalIndex),
        items: [item],
      })
    }
  })

  return Array.from(categories.entries())
    .sort(([, left], [, right]) => left.order - right.order)
    .map(([code, category]) => ({
      code,
      name: category.name,
      achievements: category.items
        .slice()
        .sort((left, right) => compareAchievementsForTab(left, right, activeTab))
        .map((item) => item.achievement),
    }))
}

function tabForAchievement(achievement: AchievementItem, pendingChoices: AchievementPendingCardChoice[]): AchievementTab {
  if (pendingChoices.length > 0 || achievement.state === 'COMPLETED_UNCLAIMED') return 'claimable'
  if (achievement.state === 'CLAIMED') return 'claimed'
  return 'unfinished'
}

function compareAchievementsForTab(left: IndexedAchievement, right: IndexedAchievement, tab: AchievementTab): number {
  if (tab === 'claimable') {
    return (
      compareNullableDateDesc(left.achievement.completedAt, right.achievement.completedAt) ||
      RARITY_ORDER[right.achievement.rarity] - RARITY_ORDER[left.achievement.rarity] ||
      rewardPriority(right.achievement) - rewardPriority(left.achievement) ||
      left.originalIndex - right.originalIndex
    )
  }
  if (tab === 'claimed') {
    return (
      compareNullableDateDesc(left.achievement.claimedAt, right.achievement.claimedAt) ||
      RARITY_ORDER[right.achievement.rarity] - RARITY_ORDER[left.achievement.rarity] ||
      left.originalIndex - right.originalIndex
    )
  }
  return (
    unfinishedPriority(left.achievement) - unfinishedPriority(right.achievement) ||
    progressRatio(right.achievement) - progressRatio(left.achievement) ||
    remainingProgress(left.achievement) - remainingProgress(right.achievement) ||
    left.originalIndex - right.originalIndex
  )
}

function unfinishedPriority(achievement: AchievementItem): number {
  return achievement.state === 'IN_PROGRESS' ? 0 : 1
}

function progressRatio(achievement: AchievementItem): number {
  if (achievement.targetValue <= 0) return 0
  return Math.min(1, achievement.progressValue / achievement.targetValue)
}

function remainingProgress(achievement: AchievementItem): number {
  return Math.max(0, achievement.targetValue - achievement.progressValue)
}

function compareNullableDateDesc(left: string | null, right: string | null): number {
  if (left === right) return 0
  if (!left) return 1
  if (!right) return -1
  return Date.parse(right) - Date.parse(left)
}

function rewardPriority(achievement: AchievementItem): number {
  if (hasCardChoiceReward(achievement.rewards)) return 6
  if (achievement.rewards.some((reward) => reward.type === 'RANDOM_CARD')) return 5
  if (achievement.rewards.some((reward) => reward.type === 'PROFILE_FRAME')) return 4
  if (achievement.rewards.some((reward) => reward.type === 'COSMETIC_UNLOCK')) return 3
  if (achievement.rewards.some((reward) => reward.type === 'BADGE_STYLE')) return 2
  if (achievement.rewards.some((reward) => reward.type === 'FANTIKI')) return 1
  return 0
}

function hasCardChoiceReward(rewards: AchievementReward[]): boolean {
  return rewards.some((reward) => reward.type === 'CARD_CHOICE_ROLL')
}

function hasProfileCosmeticUnlock(unlocks: { type: string; code: string }[] | undefined): boolean {
  return unlocks?.some((unlock) => unlock.type === 'COSMETIC_UNLOCK') ?? false
}

function categoryOrder(code: string, fallback: number): number {
  const index = CATEGORY_ORDER.indexOf(code)
  return index >= 0 ? index : CATEGORY_ORDER.length + fallback
}

function categoryDisplayName(category: AchievementCategory): string {
  switch (category.code) {
    case 'PARTICIPATION':
      return 'Участие'
    case 'BUDGET':
      return 'Бюджетная лига'
    case 'RESULTS':
      return 'Результаты'
    case 'COLLECTION':
      return 'Коллекция'
    case 'PACKS':
      return 'Паки'
    case 'MARKETPLACE':
      return 'Маркетплейс'
    case 'SOCIAL':
      return 'Социальность'
    case 'SPECIAL':
      return 'Особые'
    default:
      return category.name
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
