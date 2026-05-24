import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect } from 'react'
import { apiGet, apiSend } from './client'
import { useInitData } from '../context/useInitData'

const APP_VERSION = import.meta.env.VITE_APP_VERSION ?? '0.0.0'
const CAMPAIGN_STORAGE_KEY = 'polemica_active_campaign_id'

export type OnboardingStep =
  | 'OPEN_STORE'
  | 'OPEN_PACK'
  | 'VIEW_COLLECTION'
  | 'SUBMIT_FIRST_TEAM'
  | 'OPEN_NOTIFICATIONS'
  | 'VIEW_RESULTS'

export interface OnboardingChecklistItem {
  step: OnboardingStep | string
  title: string
  description: string
  completed: boolean
  ctaLabel: string
  ctaPath: string
}

export interface OnboardingChecklist {
  completedCount: number
  totalCount: number
  primaryCta: OnboardingChecklistItem | null
  items: OnboardingChecklistItem[]
}

export interface ReleaseNote {
  id: number
  title: string
  body: string
  audience: string
  minAppVersion: string | null
  publishedAt: string
  seen: boolean
}

export interface ReleaseNotesResponse {
  notes: ReleaseNote[]
  unseenCount: number
}

export interface ProductEventPayload {
  eventType: string
  campaignId?: number | null
  releaseNoteId?: number | null
  subjectType?: string | null
  subjectId?: number | null
  source?: string | null
  metadata?: Record<string, unknown> | null
}

export const onboardingKey = (initData: string | undefined) => ['onboarding', initData] as const
export const releaseNotesKey = (initData: string | undefined) => ['release-notes', initData] as const

export function useOnboardingChecklist() {
  const initData = useInitData()
  return useQuery({
    queryKey: onboardingKey(initData),
    queryFn: () => apiGet<OnboardingChecklist>('/api/v1/onboarding', initData),
    enabled: !!initData,
  })
}

export function useReleaseNotes() {
  const initData = useInitData()
  return useQuery({
    queryKey: releaseNotesKey(initData),
    queryFn: () =>
      apiGet<ReleaseNotesResponse>(`/api/v1/release-notes?appVersion=${encodeURIComponent(APP_VERSION)}`, initData),
    enabled: !!initData,
  })
}

export function useMarkReleaseNoteSeen() {
  const initData = useInitData()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      if (!initData) throw new Error('Нет initData')
      return apiSend<void>('POST', `/api/v1/release-notes/${id}/seen`, initData)
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: releaseNotesKey(initData) })
    },
  })
}

export function useMarkOnboardingStep(step: OnboardingStep) {
  const initData = useInitData()
  const qc = useQueryClient()
  useEffect(() => {
    if (!initData) return
    void apiSend<void>('POST', `/api/v1/onboarding/steps/${step}/complete`, initData)
      .then(() => {
        void qc.invalidateQueries({ queryKey: onboardingKey(initData) })
        const campaignId = currentCampaignId()
        if (campaignId != null) {
          void apiSend<void>('POST', '/api/v1/product-events', initData, {
            eventType: 'CAMPAIGN_ACTION',
            campaignId,
            subjectType: step,
            source: 'TMA',
          }).catch(() => undefined)
        }
      })
      .catch(() => undefined)
  }, [initData, qc, step])
}

export function useTrackProductEvent() {
  const initData = useInitData()
  return useCallback(
    (payload: ProductEventPayload) => {
      if (!initData) return
      const campaignId = payload.campaignId ?? currentCampaignId()
      void apiSend<void>('POST', '/api/v1/product-events', initData, {
        source: 'TMA',
        ...payload,
        campaignId,
      }).catch(() => undefined)
    },
    [initData],
  )
}

export function rememberCampaign(campaignId: number) {
  sessionStorage.setItem(CAMPAIGN_STORAGE_KEY, String(campaignId))
}

function currentCampaignId(): number | null {
  const raw = sessionStorage.getItem(CAMPAIGN_STORAGE_KEY)
  if (!raw) return null
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}
