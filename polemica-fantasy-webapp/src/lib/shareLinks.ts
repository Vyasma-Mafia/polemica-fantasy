import { initDataStartParam, openTelegramLink, shareURL } from '@telegram-apps/sdk'

type SeriesShareTarget = {
  seriesId: number
  telegramId: number
  leagueCode: string
}

export type ShareTarget =
  | ({ kind: 'team' | 'place' } & SeriesShareTarget)
  | ({ kind: 'card'; userCardId: number } & SeriesShareTarget)
  | { kind: 'profile'; telegramId: number }
  | ({ kind: 'compareS' } & SeriesShareTarget)
  | { kind: 'compareT'; tournamentId: number; telegramId: number; leagueCode: string }

type ParsedShareStart =
  | { path: string; search?: string }
  | null

const KIND_PREFIX: Record<ShareTarget['kind'], string> = {
  team: 'team',
  place: 'place',
  card: 'card',
  profile: 'profile',
  compareS: 'compareS',
  compareT: 'compareT',
}

function compactLeague(code: string): string {
  const normalized = code.trim().toUpperCase()
  return normalized.replace(/[^A-Z0-9-]/g, '-').slice(0, 24) || 'MAIN'
}

function encodeStartParam(target: ShareTarget): string {
  switch (target.kind) {
    case 'team':
    case 'place':
    case 'compareS':
      return [
        KIND_PREFIX[target.kind],
        target.seriesId,
        target.telegramId,
        compactLeague(target.leagueCode),
      ].join('_')
    case 'card':
      return [
        KIND_PREFIX.card,
        target.seriesId,
        target.telegramId,
        compactLeague(target.leagueCode),
        target.userCardId,
      ].join('_')
    case 'profile':
      return [KIND_PREFIX.profile, target.telegramId].join('_')
    case 'compareT':
      return [
        KIND_PREFIX.compareT,
        target.tournamentId,
        target.telegramId,
        compactLeague(target.leagueCode),
      ].join('_')
  }
}

function botUsername(): string | null {
  const raw =
    import.meta.env.VITE_TMA_BOT_USERNAME ??
    import.meta.env.VITE_TELEGRAM_BOT_USERNAME ??
    ''
  const normalized = raw.trim().replace(/^@/, '')
  return normalized || null
}

function tmaShortName(): string | null {
  const raw = import.meta.env.VITE_TMA_APP_SHORT_NAME ?? ''
  const normalized = raw.trim().replace(/^\/+|\/+$/g, '')
  return normalized || null
}

function routeForTarget(target: ShareTarget): ParsedShareStart {
  switch (target.kind) {
    case 'team':
    case 'place':
      return {
        path: `/series/${target.seriesId}/leaderboard/player/${target.telegramId}`,
        search: `league=${encodeURIComponent(compactLeague(target.leagueCode))}`,
      }
    case 'card':
      return {
        path: `/series/${target.seriesId}/leaderboard/player/${target.telegramId}`,
        search: `league=${encodeURIComponent(compactLeague(target.leagueCode))}&cardId=${target.userCardId}`,
      }
    case 'profile':
      return { path: `/players/${target.telegramId}` }
    case 'compareS':
      return {
        path: `/series/${target.seriesId}/compare/${target.telegramId}`,
        search: `league=${encodeURIComponent(compactLeague(target.leagueCode))}`,
      }
    case 'compareT':
      return {
        path: `/tournaments/${target.tournamentId}/compare/${target.telegramId}`,
        search: `league=${encodeURIComponent(compactLeague(target.leagueCode))}`,
      }
  }
}

export function parseShareStartParam(param: string | null | undefined): ParsedShareStart {
  if (!param) return null
  const parts = param.split('_')
  const [kind] = parts
  const n = (value: string | undefined) => {
    const parsed = Number(value)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null
  }
  const league = (value: string | undefined) => compactLeague(value ?? 'MAIN')

  if ((kind === 'team' || kind === 'place' || kind === 'compareS') && parts.length >= 4) {
    const seriesId = n(parts[1])
    const telegramId = n(parts[2])
    if (seriesId == null || telegramId == null) return null
    return routeForTarget({ kind, seriesId, telegramId, leagueCode: league(parts[3]) })
  }
  if (kind === 'card' && parts.length >= 5) {
    const seriesId = n(parts[1])
    const telegramId = n(parts[2])
    const userCardId = n(parts[4])
    if (seriesId == null || telegramId == null || userCardId == null) return null
    return routeForTarget({ kind, seriesId, telegramId, leagueCode: league(parts[3]), userCardId })
  }
  if (kind === 'profile' && parts.length >= 2) {
    const telegramId = n(parts[1])
    if (telegramId == null) return null
    return routeForTarget({ kind, telegramId })
  }
  if (kind === 'compareT' && parts.length >= 4) {
    const tournamentId = n(parts[1])
    const telegramId = n(parts[2])
    if (tournamentId == null || telegramId == null) return null
    return routeForTarget({ kind, tournamentId, telegramId, leagueCode: league(parts[3]) })
  }
  return null
}

export function readInitialShareStartParam(): string | null {
  try {
    const sdkParam = initDataStartParam()
    if (sdkParam) return sdkParam
  } catch {
    // Outside Telegram or before the SDK bridge is available.
  }
  return new URLSearchParams(window.location.search).get('tgWebAppStartParam')
}

export function buildShareUrl(target: ShareTarget): string {
  const bot = botUsername()
  const shortName = tmaShortName()
  if (bot) {
    const base = shortName ? `https://t.me/${bot}/${shortName}` : `https://t.me/${bot}`
    return `${base}?startapp=${encodeURIComponent(encodeStartParam(target))}`
  }

  const route = routeForTarget(target)
  const search = route?.search ? `?${route.search}` : ''
  return `${window.location.origin}${route?.path ?? '/'}${search}`
}

export function shareToTelegram(target: ShareTarget, text: string) {
  const url = buildShareUrl(target)
  try {
    if (shareURL.isAvailable()) {
      shareURL(url, text)
      return
    }
  } catch {
    // Fall through to a plain t.me share URL.
  }

  const shareLink = `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(text)}`
  try {
    if (openTelegramLink.isAvailable()) {
      openTelegramLink(shareLink)
      return
    }
  } catch {
    // Fall through to browser open.
  }
  window.open(shareLink, '_blank', 'noopener,noreferrer')
}
