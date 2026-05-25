import { apiJson } from './client'
import type {
  AchievementAdminDefinitionDto,
  AchievementAdminListResponseDto,
  AchievementDryRunResponseDto,
  UpdateAchievementAdminRequest,
} from './types'

export function listAchievements() {
  return apiJson<AchievementAdminListResponseDto>('/v1/admin/achievements')
}

export function updateAchievement(code: string, body: UpdateAchievementAdminRequest) {
  return apiJson<AchievementAdminDefinitionDto>(
    `/v1/admin/achievements/${encodeURIComponent(code)}`,
    {
      method: 'PATCH',
      body: JSON.stringify(body),
    },
  )
}

export function dryRunAchievementBackfill() {
  return apiJson<AchievementDryRunResponseDto>(
    '/v1/admin/achievements/backfill/dry-run',
    { method: 'POST', body: JSON.stringify({}) },
  )
}
