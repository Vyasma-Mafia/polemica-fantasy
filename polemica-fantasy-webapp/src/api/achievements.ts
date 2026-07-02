import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiSend } from './client'
import type {
  AchievementCatalog,
  AchievementClaimResult,
  ProfileCustomization,
  UpdateProfileCustomizationRequest,
} from './types'

export function useAchievements(initData: string | undefined) {
  return useQuery({
    queryKey: ['achievements', initData],
    queryFn: () => apiGet<AchievementCatalog>('/api/v1/achievements', initData),
    enabled: !!initData,
  })
}

export function useClaimAchievement(initData: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (code: string) =>
      apiSend<AchievementClaimResult>('POST', `/api/v1/achievements/${code}/claim`, initData),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['achievements'] })
      void queryClient.invalidateQueries({ queryKey: ['me'] })
      void queryClient.invalidateQueries({ queryKey: ['cards'] })
      void queryClient.invalidateQueries({ queryKey: ['profile-customization'] })
    },
  })
}

export function useSelectAchievementCardChoice(initData: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ code, rewardId, optionIds }: { code: string; rewardId: number; optionIds: string[] }) =>
      apiSend<AchievementClaimResult>('POST', `/api/v1/achievements/${code}/choices/${rewardId}/select`, initData, { optionIds }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['achievements'] })
      void queryClient.invalidateQueries({ queryKey: ['me'] })
      void queryClient.invalidateQueries({ queryKey: ['cards'] })
      void queryClient.invalidateQueries({ queryKey: ['profile-customization'] })
    },
  })
}

export function useProfileCustomization(initData: string | undefined) {
  return useQuery({
    queryKey: ['profile-customization', initData],
    queryFn: () => apiGet<ProfileCustomization>('/api/v1/me/profile-customization', initData),
    enabled: !!initData,
  })
}

export function useUpdateProfileCustomization(initData: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: UpdateProfileCustomizationRequest) =>
      apiSend<ProfileCustomization>('PUT', '/api/v1/me/profile-customization', initData, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['profile-customization'] })
      void queryClient.invalidateQueries({ queryKey: ['player-profile'] })
      void queryClient.invalidateQueries({ queryKey: ['achievements'] })
    },
  })
}
