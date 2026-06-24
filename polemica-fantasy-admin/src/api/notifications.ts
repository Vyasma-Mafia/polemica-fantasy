import type {
  BroadcastAcceptedDto,
  DirectMessageResultDto,
  ProductAnalyticsSummaryDto,
  ProductCampaignAnalyticsDto,
  ProductCampaignAudienceCountDto,
  ProductCampaignDto,
  ProductCampaignPreviewDto,
  ReleaseNoteAdminDto,
  ReleaseNoteAnalyticsDto,
} from './types'
import { apiJson } from './client'

export function broadcastMessage(text: string) {
  return apiJson<BroadcastAcceptedDto>('/v1/admin/notifications/broadcast', {
    method: 'POST',
    body: JSON.stringify({ text }),
  })
}

export function sendDirectMessage(telegramUserId: number, text: string) {
  return apiJson<DirectMessageResultDto>('/v1/admin/notifications/direct', {
    method: 'POST',
    body: JSON.stringify({ telegramUserId, text }),
  })
}

export function listCampaigns() {
  return apiJson<ProductCampaignDto[]>('/v1/admin/notifications/campaigns')
}

export function dryRunCampaign(audience: string) {
  return apiJson<ProductCampaignAudienceCountDto>('/v1/admin/notifications/campaigns/dry-run', {
    method: 'POST',
    body: JSON.stringify({ audience }),
  })
}

export function previewCampaign(input: {
  text: string
  audience: string
  buttonText?: string | null
  buttonUrl?: string | null
}) {
  return apiJson<ProductCampaignPreviewDto>('/v1/admin/notifications/campaigns/preview', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function sendCampaign(input: {
  title: string
  text: string
  audience: string
  buttonText?: string | null
  buttonUrl?: string | null
}) {
  return apiJson<ProductCampaignDto>('/v1/admin/notifications/campaigns/send', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function sendExistingCampaign(id: number) {
  return apiJson<ProductCampaignDto>(`/v1/admin/notifications/campaigns/${id}/send`, {
    method: 'POST',
  })
}

export function listReleaseNotes() {
  return apiJson<ReleaseNoteAdminDto[]>('/v1/admin/notifications/release-notes')
}

export function createReleaseNote(input: {
  title: string
  body: string
  buttonText?: string | null
  buttonUrl?: string | null
  audience: string
  minAppVersion?: string | null
  active: boolean
}) {
  return apiJson<ReleaseNoteAdminDto>('/v1/admin/notifications/release-notes', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateReleaseNote(id: number, input: Partial<{
  title: string
  body: string
  buttonText: string | null
  buttonUrl: string | null
  audience: string
  minAppVersion: string | null
  active: boolean
}>) {
  return apiJson<ReleaseNoteAdminDto>(`/v1/admin/notifications/release-notes/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function publishReleaseNote(id: number, active: boolean) {
  return apiJson<ReleaseNoteAdminDto>(
    `/v1/admin/notifications/release-notes/${id}/${active ? 'publish' : 'unpublish'}`,
    { method: 'POST' },
  )
}

export function fetchProductAnalyticsSummary() {
  return apiJson<ProductAnalyticsSummaryDto>('/v1/admin/notifications/analytics/summary')
}

export function fetchCampaignAnalytics() {
  return apiJson<ProductCampaignAnalyticsDto[]>('/v1/admin/notifications/analytics/campaigns')
}

export function fetchReleaseNoteAnalytics() {
  return apiJson<ReleaseNoteAnalyticsDto[]>('/v1/admin/notifications/analytics/release-notes')
}
