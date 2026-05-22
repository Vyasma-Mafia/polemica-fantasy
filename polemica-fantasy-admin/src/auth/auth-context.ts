import { createContext } from 'react'

export type AdminRole = 'admin' | 'moderator'

export interface AuthState {
  authed: boolean
  role: AdminRole | null
  roleLoading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthState | null>(null)
