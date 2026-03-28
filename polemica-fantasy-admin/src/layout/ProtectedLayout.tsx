import { Navigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { AdminLayout } from './AdminLayout'

/** Requires auth; renders shell with sidebar and child routes via Outlet. */
export function ProtectedLayout() {
  const { authed } = useAuth()
  if (!authed) {
    return <Navigate to="/login" replace />
  }
  return <AdminLayout />
}
