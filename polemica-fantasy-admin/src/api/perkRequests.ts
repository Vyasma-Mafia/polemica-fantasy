import type { OccurrenceType } from './types'

export interface UpdatePerkRequest {
  name?: string | null
  description?: string | null
  bonusPoints?: number | null
  occurrenceType?: OccurrenceType | null
  applicableRoles?: string[] | null
  canAppearOnRandomCards?: boolean | null
}
