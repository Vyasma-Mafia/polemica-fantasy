import type { ReactNode } from 'react'

/** Sticky wrapper for the current user’s row above the main list. */
export function LeaderboardPinnedBlock({ children }: { children: ReactNode }) {
  return <div className="pf-lb-pinned">{children}</div>
}
