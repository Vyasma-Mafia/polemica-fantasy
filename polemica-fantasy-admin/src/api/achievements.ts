import type { UpdateAchievementRequest } from './achievementRequests'
import type { AchievementAdminDto } from './types'
import { apiJson } from './client'

export type { UpdateAchievementRequest } from './achievementRequests'

export function listAchievements() {
  return apiJson<AchievementAdminDto[]>('/v1/admin/achievements')
}

export function updateAchievement(id: string, body: UpdateAchievementRequest) {
  return apiJson<AchievementAdminDto>(`/v1/admin/achievements/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}
