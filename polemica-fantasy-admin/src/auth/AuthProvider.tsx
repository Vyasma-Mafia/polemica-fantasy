import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  clearStoredBasicB64,
  fetchMe,
  getStoredBasicB64,
  loginWithPassword,
} from '../api/client'
import { AuthContext, type AdminRole } from './auth-context'

function normalizeRole(role: string): AdminRole {
  return role.toLowerCase() as AdminRole
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authed, setAuthed] = useState(() => !!getStoredBasicB64())
  const [role, setRole] = useState<AdminRole | null>(null)
  const [roleLoading, setRoleLoading] = useState(() => !!getStoredBasicB64())

  useEffect(() => {
    const onLost = () => {
      setAuthed(false)
      setRole(null)
      setRoleLoading(false)
    }
    window.addEventListener('polemica-admin-auth-lost', onLost)
    return () => window.removeEventListener('polemica-admin-auth-lost', onLost)
  }, [])

  useEffect(() => {
    if (!authed) {
      setRole(null)
      setRoleLoading(false)
      return
    }
    let cancelled = false
    setRoleLoading(true)
    fetchMe()
      .then((me) => {
        if (!cancelled) {
          setRole(normalizeRole(me.role))
        }
      })
      .catch(() => {
        if (!cancelled) {
          clearStoredBasicB64()
          setAuthed(false)
          setRole(null)
        }
      })
      .finally(() => {
        if (!cancelled) {
          setRoleLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [authed])

  const login = useCallback(async (username: string, password: string) => {
    await loginWithPassword(username, password)
    setAuthed(true)
  }, [])

  const logout = useCallback(() => {
    clearStoredBasicB64()
    setAuthed(false)
    setRole(null)
    setRoleLoading(false)
  }, [])

  const value = useMemo(
    () => ({ authed, role, roleLoading, login, logout }),
    [authed, role, roleLoading, login, logout],
  )

  return (
    <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
  )
}
