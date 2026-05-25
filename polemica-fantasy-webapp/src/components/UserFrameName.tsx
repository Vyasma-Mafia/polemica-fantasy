import type { ReactNode } from 'react'

type Props = {
  profileFrameCode?: string | null
  className?: string
  children: ReactNode
}

export function UserFrameName({ profileFrameCode, className, children }: Props) {
  const frameCode = profileFrameCode?.trim()
  const frameClass = frameCode ? ` pf-user-frame-name--${frameCode}` : ''
  const classSuffix = className ? ` ${className}` : ''
  return (
    <span className={`pf-user-frame-name${frameClass}${classSuffix}`}>
      <span className="pf-user-frame-name__text">{children}</span>
    </span>
  )
}
