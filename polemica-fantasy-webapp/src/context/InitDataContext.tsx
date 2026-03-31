import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { retrieveRawInitData } from '@telegram-apps/sdk'
import { InitDataContext } from './initDataContext'

export function InitDataProvider({ children }: { children: ReactNode }) {
  const [value, setValue] = useState<string | undefined>(() => {
    const dev = import.meta.env.VITE_DEV_INIT_DATA
    if (import.meta.env.DEV && dev) return dev
    return undefined
  })

  useEffect(() => {
    if (value !== undefined) return
    queueMicrotask(() => {
      try {
        setValue(retrieveRawInitData())
      } catch {
        setValue(undefined)
      }
    })
  }, [value])

  const memo = useMemo(() => value, [value])
  return <InitDataContext.Provider value={memo}>{children}</InitDataContext.Provider>
}
