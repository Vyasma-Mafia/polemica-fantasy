import { useEffect, useState, type ReactNode } from 'react'
import { retrieveRawInitData } from '@telegram-apps/sdk'
import { InitDataContext, type InitDataStatus } from './initDataContext'

function initialState(): InitDataStatus {
  const dev = import.meta.env.VITE_DEV_INIT_DATA
  if (import.meta.env.DEV && typeof dev === 'string' && dev.length > 0) {
    return { initData: dev, error: null, pending: false }
  }
  return { initData: undefined, error: null, pending: true }
}

export function InitDataProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<InitDataStatus>(initialState)

  useEffect(() => {
    if (import.meta.env.DEV && import.meta.env.VITE_DEV_INIT_DATA) return

    queueMicrotask(() => {
      try {
        const raw = retrieveRawInitData()
        if (typeof raw !== 'string' || !raw.trim()) {
          setState({
            initData: undefined,
            error: 'Пустая строка initData (приложение открыто не в Telegram?)',
            pending: false,
          })
          return
        }
        setState({ initData: raw, error: null, pending: false })
      } catch (e) {
        const message = e instanceof Error ? e.message : String(e)
        setState({ initData: undefined, error: message, pending: false })
      }
    })
  }, [])

  return <InitDataContext.Provider value={state}>{children}</InitDataContext.Provider>
}
