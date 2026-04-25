import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiSend } from './client'
import { useInitData } from '../context/useInitData'
import type {
  CreateMarketplaceWatchRequest,
  MarketplaceWatchesResponse,
  NotificationSettingsResponse,
  UpdateNotificationSettingsRequest,
  UpdateTournamentSubscriptionsRequest,
  TournamentSubscriptionsResponse,
} from '../types/notifications'

const notificationSettingsKey = (initData: string | undefined) => ['settings', 'notifications', initData] as const
const tournamentSubscriptionsKey = (initData: string | undefined) =>
  ['settings', 'tournament-subscriptions', initData] as const
const marketplaceWatchesKey = (initData: string | undefined) => ['settings', 'marketplace-watches', initData] as const

export function useNotificationSettings() {
  const initData = useInitData()
  return useQuery({
    queryKey: notificationSettingsKey(initData),
    queryFn: () => apiGet<NotificationSettingsResponse>('/api/v1/settings/notifications', initData),
    enabled: !!initData,
  })
}

export function useUpdateNotificationSettings() {
  const initData = useInitData()
  const queryClient = useQueryClient()
  const queryKey = notificationSettingsKey(initData)

  return useMutation({
    mutationFn: async (request: UpdateNotificationSettingsRequest) => {
      if (!initData) throw new Error('Нет initData')
      return apiSend<NotificationSettingsResponse>('PUT', '/api/v1/settings/notifications', initData, request)
    },
    onMutate: async (request) => {
      await queryClient.cancelQueries({ queryKey })
      const previous = queryClient.getQueryData<NotificationSettingsResponse>(queryKey)
      if (previous) {
        const optimistic: NotificationSettingsResponse = {
          categories: previous.categories.map((item) => ({
            ...item,
            enabled: request.categories[item.category] ?? item.enabled,
          })),
        }
        queryClient.setQueryData(queryKey, optimistic)
      }
      return { previous }
    },
    onError: (_error, _request, context) => {
      if (context?.previous) {
        queryClient.setQueryData(queryKey, context.previous)
      }
    },
    onSuccess: (next) => {
      queryClient.setQueryData(queryKey, next)
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey })
    },
  })
}

export function useTournamentSubscriptions() {
  const initData = useInitData()
  return useQuery({
    queryKey: tournamentSubscriptionsKey(initData),
    queryFn: () =>
      apiGet<TournamentSubscriptionsResponse>('/api/v1/settings/tournament-subscriptions', initData),
    enabled: !!initData,
  })
}

export function useUpdateTournamentSubscriptions() {
  const initData = useInitData()
  const queryClient = useQueryClient()
  const queryKey = tournamentSubscriptionsKey(initData)

  return useMutation({
    mutationFn: async (request: UpdateTournamentSubscriptionsRequest) => {
      if (!initData) throw new Error('Нет initData')
      return apiSend<TournamentSubscriptionsResponse>(
        'PUT',
        '/api/v1/settings/tournament-subscriptions',
        initData,
        request,
      )
    },
    onMutate: async (request) => {
      await queryClient.cancelQueries({ queryKey })
      const previous = queryClient.getQueryData<TournamentSubscriptionsResponse>(queryKey)
      if (previous) {
        const selected = new Set(request.tournamentIds)
        const optimisticAvailable = previous.availableTournaments.map((entry) => ({
          ...entry,
          subscribed: selected.has(entry.tournamentId),
        }))
        queryClient.setQueryData<TournamentSubscriptionsResponse>(queryKey, {
          availableTournaments: optimisticAvailable,
          subscriptions: optimisticAvailable.filter((entry) => entry.subscribed),
        })
      }
      return { previous }
    },
    onError: (_error, _request, context) => {
      if (context?.previous) {
        queryClient.setQueryData(queryKey, context.previous)
      }
    },
    onSuccess: (next) => {
      queryClient.setQueryData(queryKey, next)
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey })
    },
  })
}

export function useMarketplaceWatches() {
  const initData = useInitData()
  return useQuery({
    queryKey: marketplaceWatchesKey(initData),
    queryFn: () => apiGet<MarketplaceWatchesResponse>('/api/v1/settings/marketplace-watches', initData),
    enabled: !!initData,
  })
}

export function useCreateMarketplaceWatch() {
  const initData = useInitData()
  const queryClient = useQueryClient()
  const queryKey = marketplaceWatchesKey(initData)
  return useMutation({
    mutationFn: async (request: CreateMarketplaceWatchRequest) => {
      if (!initData) throw new Error('Нет initData')
      return apiSend('POST', '/api/v1/settings/marketplace-watches', initData, request)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey })
    },
  })
}

export function useDeleteMarketplaceWatch() {
  const initData = useInitData()
  const queryClient = useQueryClient()
  const queryKey = marketplaceWatchesKey(initData)
  return useMutation({
    mutationFn: async (id: number) => {
      if (!initData) throw new Error('Нет initData')
      return apiSend<void>('DELETE', `/api/v1/settings/marketplace-watches/${id}`, initData)
    },
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey })
      const previous = queryClient.getQueryData<MarketplaceWatchesResponse>(queryKey)
      if (previous) {
        queryClient.setQueryData<MarketplaceWatchesResponse>(queryKey, {
          ...previous,
          watches: previous.watches.filter((watch) => watch.id !== id),
        })
      }
      return { previous }
    },
    onError: (_error, _id, context) => {
      if (context?.previous) {
        queryClient.setQueryData(queryKey, context.previous)
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey })
    },
  })
}
