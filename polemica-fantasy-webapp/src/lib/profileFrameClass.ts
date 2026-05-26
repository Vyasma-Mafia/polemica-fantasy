const profileFrameClassCodes = new Set([
  'budget_master',
  'budget_master_elite',
  'budget_winner',
  'collector',
  'dynasty',
  'dynasty_elite',
  'legendary_crafter',
  'pack_hunter',
  'stable_manager_elite',
  'steady_result',
])

export function profileFrameClassSuffix(code?: string | null): string | null {
  const suffix = code?.trim().toLowerCase().replace(/[^a-z0-9_-]/g, '_')
  return suffix && profileFrameClassCodes.has(suffix) ? suffix : null
}
