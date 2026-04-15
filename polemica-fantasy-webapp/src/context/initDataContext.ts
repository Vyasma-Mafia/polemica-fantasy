import { createContext } from 'react'

export type InitDataStatus = {
  initData: string | undefined
  error: string | null
  pending: boolean
}

export const InitDataContext = createContext<InitDataStatus | null>(null)
