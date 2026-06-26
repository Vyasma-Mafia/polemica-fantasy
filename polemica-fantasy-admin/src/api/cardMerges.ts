import { apiJson } from './client'
import type {
  AdminCardMergeDetailDto,
  AdminCardMergeListItemDto,
  AdminCardMergePageDto,
} from './types'

export type ListCardMergesParams = {
  page?: number
  size?: number
  telegramUserId?: string
  resultUserCardId?: string
}

type RawCardMergePage =
  | AdminCardMergeListItemDto[]
  | (Partial<AdminCardMergePageDto> & {
      number?: number
      items?: AdminCardMergeListItemDto[]
      data?: AdminCardMergeListItemDto[]
      results?: AdminCardMergeListItemDto[]
      merges?: AdminCardMergeListItemDto[]
      total?: number
    })

function firstArray(raw: RawCardMergePage): AdminCardMergeListItemDto[] {
  if (Array.isArray(raw)) return raw
  return raw.content ?? raw.items ?? raw.data ?? raw.results ?? raw.merges ?? []
}

export function normalizeCardMergePage(raw: RawCardMergePage): AdminCardMergePageDto {
  const content = firstArray(raw)
  if (Array.isArray(raw)) {
    return {
      content,
      page: 0,
      size: content.length,
      totalElements: content.length,
      totalPages: 1,
    }
  }

  const page = raw.number ?? raw.page ?? 0
  const size = raw.size ?? content.length
  const totalElements = raw.totalElements ?? raw.total ?? content.length
  const totalPages =
    raw.totalPages ?? (size > 0 ? Math.max(1, Math.ceil(totalElements / size)) : 1)

  return {
    content,
    page,
    size,
    totalElements,
    totalPages,
  }
}

export async function listCardMerges(params?: ListCardMergesParams) {
  const page = params?.page ?? 0
  const size = params?.size ?? 20
  const q = new URLSearchParams({ page: String(page), size: String(size) })
  const telegramUserId = params?.telegramUserId?.trim()
  const resultUserCardId = params?.resultUserCardId?.trim()
  if (telegramUserId) q.set('telegramUserId', telegramUserId)
  if (resultUserCardId) q.set('resultUserCardId', resultUserCardId)

  const raw = await apiJson<RawCardMergePage>(`/v1/admin/card-merges?${q.toString()}`)
  return normalizeCardMergePage(raw)
}

export function getCardMerge(id: number) {
  return apiJson<AdminCardMergeDetailDto>(`/v1/admin/card-merges/${encodeURIComponent(id)}`)
}
