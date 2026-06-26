import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useProfileCustomization, useUpdateProfileCustomization } from '../api/achievements'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import type { FavoriteBadgePlayerOption, ProfileCustomization } from '../api/types'
import { rarityClass } from '../lib/rarity'

const MAX_FEATURED = 5
const AUTO_FAVORITE_BADGE_VALUE = ''

type ProfileCustomizationDraft = {
  profileFrameCode: string | null
  featuredAchievementCodes: string[]
  favoriteBadgeValue: string
}

type ProfileFrame = ProfileCustomization['unlockedFrames'][number]
type FeaturedAchievement = ProfileCustomization['availableFeaturedAchievements'][number]
type AchievementByCode = Map<string, FeaturedAchievement>

const EMPTY_DRAFT: ProfileCustomizationDraft = {
  profileFrameCode: null,
  featuredAchievementCodes: [],
  favoriteBadgeValue: AUTO_FAVORITE_BADGE_VALUE,
}

function favoriteBadgeSelectValue(
  favoriteBadgeFantasyPlayerId: number | null,
  options: FavoriteBadgePlayerOption[],
): string {
  if (favoriteBadgeFantasyPlayerId === null) return AUTO_FAVORITE_BADGE_VALUE
  const value = String(favoriteBadgeFantasyPlayerId)
  return options.some((player) => String(player.fantasyPlayerId) === value) ? value : AUTO_FAVORITE_BADGE_VALUE
}

function favoriteBadgeFantasyPlayerIdFromValue(value: string, options: FavoriteBadgePlayerOption[]): number | null {
  if (value === AUTO_FAVORITE_BADGE_VALUE) return null
  return options.find((player) => String(player.fantasyPlayerId) === value)?.fantasyPlayerId ?? null
}

function draftFromCustomization(customization: ProfileCustomization): ProfileCustomizationDraft {
  return {
    profileFrameCode: customization.profileFrameCode,
    featuredAchievementCodes: customization.featuredAchievementCodes,
    favoriteBadgeValue: favoriteBadgeSelectValue(
      customization.favoriteBadgeFantasyPlayerId,
      customization.favoriteBadgePlayerOptions,
    ),
  }
}

function FramePicker({
  profileFrameCode,
  frames,
  onChange,
}: {
  profileFrameCode: string | null
  frames: ProfileFrame[]
  onChange: (code: string | null) => void
}) {
  return (
    <section className="pf-section">
      <h2 className="pf-section-title">Рамка</h2>
      <div className="pf-showcase-frame-options">
        <button
          type="button"
          className={`pf-showcase-frame-option${profileFrameCode === null ? ' active' : ''}`}
          onClick={() => onChange(null)}
        >
          Без рамки
        </button>
        {frames.map((frame) => (
          <button
            key={frame.code}
            type="button"
            className={`pf-showcase-frame-option${profileFrameCode === frame.code ? ' active' : ''}`}
            onClick={() => onChange(frame.code)}
          >
            {frame.name}
          </button>
        ))}
      </div>
    </section>
  )
}

function FeaturedBadgesEditor({
  featuredCodes,
  achievementByCode,
  onMove,
  onRemove,
}: {
  featuredCodes: string[]
  achievementByCode: AchievementByCode
  onMove: (index: number, direction: -1 | 1) => void
  onRemove: (code: string) => void
}) {
  return (
    <section className="pf-section">
      <h2 className="pf-section-title">Выбранные значки</h2>
      <ul className="pf-showcase-selected">
        {featuredCodes.map((code, index) => {
          const achievement = achievementByCode.get(code)
          if (!achievement) return null
          return (
            <li key={code} className="pf-showcase-selected__item">
              <span>{achievement.title}</span>
              <div>
                <button
                  type="button"
                  aria-label={`Переместить значок ${achievement.title} выше`}
                  onClick={() => onMove(index, -1)}
                  disabled={index === 0}
                >
                  ↑
                </button>
                <button
                  type="button"
                  aria-label={`Переместить значок ${achievement.title} ниже`}
                  onClick={() => onMove(index, 1)}
                  disabled={index === featuredCodes.length - 1}
                >
                  ↓
                </button>
                <button
                  type="button"
                  aria-label={`Убрать значок ${achievement.title}`}
                  onClick={() => onRemove(code)}
                >
                  ×
                </button>
              </div>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

function FavoriteBadgePlayerSelect({
  options,
  value,
  onChange,
}: {
  options: FavoriteBadgePlayerOption[]
  value: string
  onChange: (value: string) => void
}) {
  if (options.length === 0) return null

  return (
    <section className="pf-section">
      <h2 className="pf-section-title">Любимый игрок</h2>
      <select
        className="pf-input pf-showcase-favorite-select"
        aria-label="Любимый игрок для бейджа"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">Автовыбор</option>
        {options.map((player) => (
          <option key={player.fantasyPlayerId} value={String(player.fantasyPlayerId)}>
            {player.nickname}
          </option>
        ))}
      </select>
    </section>
  )
}

function AvailableBadgesPicker({
  achievements,
  featuredCodes,
  onToggle,
}: {
  achievements: FeaturedAchievement[]
  featuredCodes: string[]
  onToggle: (code: string) => void
}) {
  return (
    <section className="pf-section">
      <h2 className="pf-section-title">Доступные значки</h2>
      <div className="pf-showcase-badge-options">
        {achievements.map((achievement) => {
          const selected = featuredCodes.includes(achievement.code)
          return (
            <button
              key={achievement.code}
              type="button"
              className={`pf-showcase-badge-option pf-showcase-badge-option--${rarityClass(achievement.rarity)}${selected ? ' active' : ''}`}
              disabled={!selected && featuredCodes.length >= MAX_FEATURED}
              onClick={() => onToggle(achievement.code)}
            >
              {achievement.title}
            </button>
          )
        })}
      </div>
    </section>
  )
}

function ActionBar({
  isSaving,
  isSaveDisabled,
  saveError,
  onSave,
}: {
  isSaving: boolean
  isSaveDisabled: boolean
  saveError: Error | null
  onSave: () => void
}) {
  return (
    <div className="pf-showcase-actions">
      <button type="button" className="pf-btn" onClick={onSave} disabled={isSaveDisabled}>
        {isSaving ? 'Сохранение…' : 'Сохранить'}
      </button>
      <Link to="/rating" className="pf-btn pf-btn--ghost">
        Закрыть
      </Link>
      {saveError ? <p className="pf-err">{saveError.message}</p> : null}
    </div>
  )
}

export function ProfileCustomizationPage() {
  const initData = useInitData()
  const customizationQ = useProfileCustomization(initData)
  const saveM = useUpdateProfileCustomization(initData)
  const customization = customizationQ.data
  const [draft, setDraft] = useState<ProfileCustomizationDraft>(EMPTY_DRAFT)

  useEffect(() => {
    if (!customization) return
    setDraft(draftFromCustomization(customization))
  }, [customization])

  const achievementByCode = useMemo(() => {
    const map: AchievementByCode = new Map()
    customization?.availableFeaturedAchievements.forEach((achievement) => {
      map.set(achievement.code, achievement)
    })
    return map
  }, [customization])

  if (!initData) return <MissingInitDataNotice />
  if (customizationQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (customizationQ.isError) return <p className="pf-err">{(customizationQ.error as Error).message}</p>
  if (!customization) return null
  const favoriteBadgePlayerOptions = customization.favoriteBadgePlayerOptions

  function updateDraft(next: Partial<ProfileCustomizationDraft>) {
    setDraft((current) => ({ ...current, ...next }))
  }

  function toggleFeatured(code: string) {
    setDraft((current) => {
      const featuredAchievementCodes = (() => {
        const featuredCodes = current.featuredAchievementCodes
        if (featuredCodes.includes(code)) return featuredCodes.filter((item) => item !== code)
        if (featuredCodes.length >= MAX_FEATURED) return featuredCodes
        return [...featuredCodes, code]
      })()

      if (featuredAchievementCodes === current.featuredAchievementCodes) return current
      return { ...current, featuredAchievementCodes }
    })
  }

  function removeFeatured(code: string) {
    setDraft((current) => ({
      ...current,
      featuredAchievementCodes: current.featuredAchievementCodes.filter((item) => item !== code),
    }))
  }

  function moveFeatured(index: number, direction: -1 | 1) {
    setDraft((current) => {
      const featuredCodes = current.featuredAchievementCodes
      const nextIndex = index + direction
      if (nextIndex < 0 || nextIndex >= featuredCodes.length) return current
      const featuredAchievementCodes = [...featuredCodes]
      const [item] = featuredAchievementCodes.splice(index, 1)
      featuredAchievementCodes.splice(nextIndex, 0, item)
      return { ...current, featuredAchievementCodes }
    })
  }

  function save() {
    if (draft.featuredAchievementCodes.length > MAX_FEATURED) return
    const favoriteBadgeFantasyPlayerId = favoriteBadgeFantasyPlayerIdFromValue(
      draft.favoriteBadgeValue,
      favoriteBadgePlayerOptions,
    )
    saveM.mutate(
      {
        profileFrameCode: draft.profileFrameCode,
        featuredAchievementCodes: draft.featuredAchievementCodes,
        favoriteBadgeFantasyPlayerId,
      },
      {
        onSuccess: (data) => {
          setDraft(draftFromCustomization(data))
        },
      },
    )
  }

  return (
    <div className="pf-page">
      <PageHeader title="Витрина профиля" backTo="/rating" />

      <div className="pf-showcase-editor">
        <FramePicker
          profileFrameCode={draft.profileFrameCode}
          frames={customization.unlockedFrames}
          onChange={(profileFrameCode) => updateDraft({ profileFrameCode })}
        />

        <FeaturedBadgesEditor
          featuredCodes={draft.featuredAchievementCodes}
          achievementByCode={achievementByCode}
          onMove={moveFeatured}
          onRemove={removeFeatured}
        />

        <FavoriteBadgePlayerSelect
          options={favoriteBadgePlayerOptions}
          value={draft.favoriteBadgeValue}
          onChange={(favoriteBadgeValue) => updateDraft({ favoriteBadgeValue })}
        />

        <AvailableBadgesPicker
          achievements={customization.availableFeaturedAchievements}
          featuredCodes={draft.featuredAchievementCodes}
          onToggle={toggleFeatured}
        />

        <ActionBar
          isSaving={saveM.isPending}
          isSaveDisabled={saveM.isPending || draft.featuredAchievementCodes.length > MAX_FEATURED}
          saveError={saveM.isError ? (saveM.error as Error) : null}
          onSave={save}
        />
      </div>
    </div>
  )
}
