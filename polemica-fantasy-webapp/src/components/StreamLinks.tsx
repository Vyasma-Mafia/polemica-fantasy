import type { StreamLink } from '../api/types'

type StreamProvider = 'twitch' | 'vkvideo' | 'generic'

export function StreamLinks({
  links,
  compact = false,
}: {
  links: StreamLink[]
  compact?: boolean
}) {
  if (links.length === 0) return null
  return (
    <div className={`pf-stream-links${compact ? ' pf-stream-links--compact' : ''}`}>
      {links.map((link, idx) => {
        const provider = detectStreamProvider(link.url)
        const label = link.label || `Трансляция ${idx + 1}`
        return (
          <a
            key={`${link.url}-${idx}`}
            className={`pf-stream-link pf-stream-link--${provider}${compact ? ' pf-stream-link--icon-only' : ''}`}
            href={link.url}
            target="_blank"
            rel="noreferrer"
            title={label}
            aria-label={label}
          >
            <StreamIcon provider={provider} />
            {!compact && <span className="pf-stream-link__label">{label}</span>}
          </a>
        )
      })}
    </div>
  )
}

function detectStreamProvider(url: string): StreamProvider {
  const host = safeHost(url)
  if (host.endsWith('twitch.tv')) return 'twitch'
  if (host === 'vkvideo.ru' || host.endsWith('.vkvideo.ru') || host === 'vk.com' || host.endsWith('.vk.com')) {
    return 'vkvideo'
  }
  return 'generic'
}

function safeHost(url: string) {
  try {
    return new URL(url).hostname.toLowerCase()
  } catch {
    return ''
  }
}

function StreamIcon({ provider }: { provider: StreamProvider }) {
  if (provider === 'twitch') {
    return (
      <svg className="pf-stream-link__icon" viewBox="0 0 24 24" aria-hidden>
        <path d="M5 4h15v10.5l-4 4h-4l-2.8 2.8v-2.8H5V4Z" />
        <path d="M9 8v5M14 8v5" />
      </svg>
    )
  }
  if (provider === 'vkvideo') {
    return (
      <svg className="pf-stream-link__icon" viewBox="0 0 24 24" aria-hidden>
        <circle cx="12" cy="12" r="8.5" />
        <path d="M10 8.5v7l5.5-3.5L10 8.5Z" />
      </svg>
    )
  }
  return (
    <svg className="pf-stream-link__icon" viewBox="0 0 24 24" aria-hidden>
      <path d="M7 12h10M13 8l4 4-4 4" />
      <path d="M5 5h14v14H5V5Z" />
    </svg>
  )
}
