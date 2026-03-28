import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { retrieveRawInitData } from '@telegram-apps/sdk'

const InitDataContext = createContext<string | undefined>(undefined)

export function InitDataProvider({ children }: { children: ReactNode }) {
  const [value, setValue] = useState<string | undefined>(() => {
    const dev = import.meta.env.VITE_DEV_INIT_DATA
    if (import.meta.env.DEV && dev) return dev
    return undefined
  })

  useEffect(() => {
    if (value !== undefined) return
    try {
      setValue(retrieveRawInitData())
    } catch {
      setValue(undefined)
    }
  }, [value])

  const memo = useMemo(() => value, [value])
  return <InitDataContext.Provider value={memo}>{children}</InitDataContext.Provider>
}

export function useInitData(): string | undefined {
  return useContext(InitDataContext)
}
