import { useEffect, useState } from 'react'

/** Monotonic wall time for deadline checks without calling Date.now() during render. */
export function useNow(updateIntervalMs = 30_000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), updateIntervalMs)
    return () => clearInterval(id)
  }, [updateIntervalMs])
  return now
}
