import { useContext } from 'react'
import { InitDataContext } from './initDataContext'

export function useInitData(): string | undefined {
  return useContext(InitDataContext)
}
