const profileAccentClassTokens = new Set(['budget_top', 'dual_strategy', 'top10'])

export function profileAccentClassSuffix(styleToken?: string | null): string | null {
  const suffix = styleToken?.trim().toLowerCase().replace(/[^a-z0-9_-]/g, '_')
  return suffix && profileAccentClassTokens.has(suffix) ? suffix : null
}
