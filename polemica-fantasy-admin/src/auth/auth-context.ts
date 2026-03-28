import { createContext } from 'react'

export interface AuthState {
  authed: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthState | null>(null)
