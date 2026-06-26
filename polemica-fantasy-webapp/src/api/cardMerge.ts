import { apiGet, apiSend } from './client'
import type {
  CardMergeConfirmRequest,
  CardMergeConfirmResponse,
  CardMergeOptionsResponse,
  CardMergePreviewRequest,
  CardMergePreviewResponse,
} from './types'

export function fetchCardMergeOptions(initData: string | undefined) {
  return apiGet<CardMergeOptionsResponse>('/api/v1/cards/merge/options', initData)
}

export function fetchCardMergePreview(
  initData: string | undefined,
  body: CardMergePreviewRequest,
) {
  return apiSend<CardMergePreviewResponse>('POST', '/api/v1/cards/merge/preview', initData, body)
}

export function confirmCardMerge(
  initData: string | undefined,
  body: CardMergeConfirmRequest,
) {
  return apiSend<CardMergeConfirmResponse>('POST', '/api/v1/cards/merge/confirm', initData, body)
}
