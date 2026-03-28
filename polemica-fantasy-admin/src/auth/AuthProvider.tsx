import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  clearStoredBasicB64,
  getStoredBasicB64,
  loginWithPassword,
} from '../api/client'
import { AuthContext } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authed, setAuthed] = useState(() => !!getStoredBasicB64())

  useEffect(() => {
    const onLost = () => setAuthed(false)
    window.addEventListener('polemica-admin-auth-lost', onLost)
    return () => window.removeEventListener('polemica-admin-auth-lost', onLost)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    await loginWithPassword(username, password)
    setAuthed(true)
  }, [])

  const logout = useCallback(() => {
    clearStoredBasicB64()
    setAuthed(false)
  }, [])

  const value = useMemo(
    () => ({ authed, login, logout }),
    [authed, login, logout],
  )

  return (
    <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
  )
}
