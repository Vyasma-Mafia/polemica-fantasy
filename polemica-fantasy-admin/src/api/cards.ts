import type {
  AddCardTemplateAchievementRequest,
  CreateCardTemplateRequest,
  GiveCardsRequest,
  UpdateCardTemplateRequest,
} from './cardRequests'
import type {
  CardTemplateDto,
  OpenPackResultDto,
  Rarity,
  UserCardDto,
} from './types'
import { apiJson } from './client'

export type {
  CreateCardTemplateRequest,
  UpdateCardTemplateRequest,
  AddCardTemplateAchievementRequest,
  GiveCardsRequest,
} from './cardRequests'

export function listCardTemplates(params: {
  tournamentId?: number
  fantasyPlayerId?: number
  rarity?: Rarity
}) {
  const q = new URLSearchParams()
  if (params.tournamentId != null) {
    q.set('tournamentId', String(params.tournamentId))
  }
  if (params.fantasyPlayerId != null) {
    q.set('fantasyPlayerId', String(params.fantasyPlayerId))
  }
  if (params.rarity != null) {
    q.set('rarity', params.rarity)
  }
  const s = q.toString()
  return apiJson<CardTemplateDto[]>(
    `/v1/admin/card-templates${s ? `?${s}` : ''}`,
  )
}

export function createCardTemplate(body: CreateCardTemplateRequest) {
  return apiJson<CardTemplateDto>('/v1/admin/card-templates', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateCardTemplate(id: number, body: UpdateCardTemplateRequest) {
  return apiJson<CardTemplateDto>(`/v1/admin/card-templates/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function addCardTemplateAchievement(
  id: number,
  body: AddCardTemplateAchievementRequest,
) {
  return apiJson<CardTemplateDto>(`/v1/admin/card-templates/${id}/achievements`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function uploadCardImage(id: number, file: File) {
  const fd = new FormData()
  fd.append('file', file)
  return apiJson<CardTemplateDto>(`/v1/admin/card-templates/${id}/image`, {
    method: 'POST',
    body: fd,
  })
}

export function giveCards(telegramUserId: number, body: GiveCardsRequest) {
  return apiJson<UserCardDto[]>(
    `/v1/admin/users/${telegramUserId}/give-cards`,
    { method: 'POST', body: JSON.stringify(body) },
  )
}

export function openPack(telegramUserId: number, packId: number) {
  return apiJson<OpenPackResultDto>(
    `/v1/admin/users/${telegramUserId}/open-pack/${packId}`,
    { method: 'POST' },
  )
}
