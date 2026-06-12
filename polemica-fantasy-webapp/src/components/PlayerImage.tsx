import { useEffect, useState } from 'react'

type PlayerImageVariant = 'avatar' | 'card' | 'mini'

type PlayerImageProps = {
  src: string | null | undefined
  seedId: number
  alt?: string
  variant: PlayerImageVariant
  className?: string
}

const TONE_COUNT = 8

function toneClass(seedId: number): string {
  const normalized = Math.abs(Math.trunc(seedId))
  return `pf-player-image--tone-${normalized % TONE_COUNT}`
}

function cleanSrc(src: string | null | undefined): string | null {
  const value = src?.trim()
  return value ? value : null
}

export function PlayerImage({ src, seedId, alt = '', variant, className }: PlayerImageProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const imageSrc = cleanSrc(src)

  useEffect(() => {
    setFailedSrc(null)
  }, [imageSrc])

  const classes = [
    'pf-player-image',
    `pf-player-image--${variant}`,
    toneClass(seedId),
    className,
  ].filter(Boolean).join(' ')
  const showImage = imageSrc != null && failedSrc !== imageSrc

  if (showImage) {
    return (
      <img
        src={imageSrc}
        alt={alt}
        className={classes}
        onError={() => setFailedSrc(imageSrc)}
      />
    )
  }

  return (
    <span
      className={`${classes} pf-player-image--fallback`}
      role={alt ? 'img' : undefined}
      aria-label={alt || undefined}
      aria-hidden={alt ? undefined : true}
    >
      <span className="pf-player-image__silhouette" aria-hidden="true" />
    </span>
  )
}
