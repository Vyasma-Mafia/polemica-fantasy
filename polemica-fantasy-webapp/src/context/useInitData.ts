import { useContext } from 'react'
import { InitDataContext, type InitDataStatus } from './initDataContext'

export function useInitDataState(): InitDataStatus {
  const v = useContext(InitDataContext)
  if (v == null) {
    throw new Error('useInitDataState must be used within InitDataProvider')
  }
  return v
}

export function useInitData(): string | undefined {
  return useInitDataState().initData
}

export type { InitDataStatus } from './initDataContext'
