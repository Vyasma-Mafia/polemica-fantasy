const API_BASE = import.meta.env.VITE_API_BASE ?? '/api'

const STORAGE_KEY = 'polemica_admin_basic_b64'

export function getStoredBasicB64(): string | null {
  return sessionStorage.getItem(STORAGE_KEY)
}

export function setStoredBasicB64(token: string) {
  sessionStorage.setItem(STORAGE_KEY, token)
}

export function clearStoredBasicB64() {
  sessionStorage.removeItem(STORAGE_KEY)
}

export async function loginWithPassword(
  username: string,
  password: string,
): Promise<void> {
  const token = btoa(`${username}:${password}`)
  const url = `${API_BASE}/v1/admin/tournaments`
  const res = await fetch(url, {
    headers: { Authorization: `Basic ${token}` },
  })
  if (res.status === 401) {
    throw new Error('Invalid username or password')
  }
  if (!res.ok) {
    throw new Error(await readErrorMessage(res))
  }
  setStoredBasicB64(token)
}

async function readErrorMessage(res: Response): Promise<string> {
  const text = await res.text()
  try {
    const j = JSON.parse(text) as { message?: string }
    return j.message ?? text
  } catch {
    return text || `HTTP ${res.status}`
  }
}

export async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  const token = getStoredBasicB64()
  if (!token) {
    throw new Error('Not authenticated')
  }
  const url = path.startsWith('http') ? path : `${API_BASE}${path}`
  const headers = new Headers(init?.headers)
  headers.set('Authorization', `Basic ${token}`)
  if (
    init?.body &&
    typeof init.body === 'string' &&
    !headers.has('Content-Type')
  ) {
    headers.set('Content-Type', 'application/json')
  }
  const res = await fetch(url, { ...init, headers })
  if (res.status === 401) {
    clearStoredBasicB64()
    window.dispatchEvent(new CustomEvent('polemica-admin-auth-lost'))
  }
  return res
}

export async function apiJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await apiFetch(path, init)
  if (!res.ok) {
    throw new Error(await readErrorMessage(res))
  }
  if (res.status === 204) {
    return undefined as T
  }
  return res.json() as Promise<T>
}

export async function apiVoid(path: string, init?: RequestInit): Promise<void> {
  const res = await apiFetch(path, init)
  if (!res.ok) {
    throw new Error(await readErrorMessage(res))
  }
}
