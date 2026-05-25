import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useProfileCustomization, useUpdateProfileCustomization } from '../api/achievements'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { rarityClass } from '../lib/rarity'

const MAX_FEATURED = 5

export function ProfileCustomizationPage() {
  const initData = useInitData()
  const customizationQ = useProfileCustomization(initData)
  const saveM = useUpdateProfileCustomization(initData)
  const customization = customizationQ.data
  const [profileFrameCode, setProfileFrameCode] = useState<string | null>(null)
  const [featuredCodes, setFeaturedCodes] = useState<string[]>([])

  useEffect(() => {
    if (!customization) return
    setProfileFrameCode(customization.profileFrameCode)
    setFeaturedCodes(customization.featuredAchievementCodes)
  }, [customization])

  const achievementByCode = useMemo(() => {
    const map = new Map<string, NonNullable<typeof customization>['availableFeaturedAchievements'][number]>()
    customization?.availableFeaturedAchievements.forEach((achievement) => {
      map.set(achievement.code, achievement)
    })
    return map
  }, [customization])

  if (!initData) return <MissingInitDataNotice />
  if (customizationQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (customizationQ.isError) return <p className="pf-err">{(customizationQ.error as Error).message}</p>
  if (!customization) return null

  function toggleFeatured(code: string) {
    setFeaturedCodes((current) => {
      if (current.includes(code)) return current.filter((item) => item !== code)
      if (current.length >= MAX_FEATURED) return current
      return [...current, code]
    })
  }

  function removeFeatured(code: string) {
    setFeaturedCodes((current) => current.filter((item) => item !== code))
  }

  function moveFeatured(index: number, direction: -1 | 1) {
    setFeaturedCodes((current) => {
      const nextIndex = index + direction
      if (nextIndex < 0 || nextIndex >= current.length) return current
      const next = [...current]
      const [item] = next.splice(index, 1)
      next.splice(nextIndex, 0, item)
      return next
    })
  }

  function save() {
    if (featuredCodes.length > MAX_FEATURED) return
    saveM.mutate(
      {
        profileFrameCode,
        featuredAchievementCodes: featuredCodes,
      },
      {
        onSuccess: (data) => {
          setProfileFrameCode(data.profileFrameCode)
          setFeaturedCodes(data.featuredAchievementCodes)
        },
      },
    )
  }

  return (
    <div className="pf-page">
      <PageHeader title="Витрина профиля" backTo="/rating" />

      <div className="pf-showcase-editor">
        <section className="pf-section">
          <h2 className="pf-section-title">Рамка</h2>
          <div className="pf-showcase-frame-options">
            <button
              type="button"
              className={`pf-showcase-frame-option${profileFrameCode === null ? ' active' : ''}`}
              onClick={() => setProfileFrameCode(null)}
            >
              Без рамки
            </button>
            {customization.unlockedFrames.map((frame) => (
              <button
                key={frame.code}
                type="button"
                className={`pf-showcase-frame-option${profileFrameCode === frame.code ? ' active' : ''}`}
                onClick={() => setProfileFrameCode(frame.code)}
              >
                {frame.name}
              </button>
            ))}
          </div>
        </section>

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
                    <button type="button" onClick={() => moveFeatured(index, -1)} disabled={index === 0}>
                      ↑
                    </button>
                    <button type="button" onClick={() => moveFeatured(index, 1)} disabled={index === featuredCodes.length - 1}>
                      ↓
                    </button>
                    <button type="button" onClick={() => removeFeatured(code)}>
                      ×
                    </button>
                  </div>
                </li>
              )
            })}
          </ul>
        </section>

        <section className="pf-section">
          <h2 className="pf-section-title">Доступные значки</h2>
          <div className="pf-showcase-badge-options">
            {customization.availableFeaturedAchievements.map((achievement) => {
              const selected = featuredCodes.includes(achievement.code)
              return (
                <button
                  key={achievement.code}
                  type="button"
                  className={`pf-showcase-badge-option pf-showcase-badge-option--${rarityClass(achievement.rarity)}${selected ? ' active' : ''}`}
                  disabled={!selected && featuredCodes.length >= MAX_FEATURED}
                  onClick={() => toggleFeatured(achievement.code)}
                >
                  {achievement.title}
                </button>
              )
            })}
          </div>
        </section>

        <div className="pf-showcase-actions">
          <button type="button" className="pf-btn" onClick={save} disabled={saveM.isPending || featuredCodes.length > MAX_FEATURED}>
            {saveM.isPending ? 'Сохранение…' : 'Сохранить'}
          </button>
          <Link to="/rating" className="pf-btn pf-btn--ghost">
            Закрыть
          </Link>
          {saveM.isError ? <p className="pf-err">{(saveM.error as Error).message}</p> : null}
        </div>
      </div>
    </div>
  )
}
