const base = () => import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function parseError(res: Response): Promise<string> {
  try {
    const j = (await res.json()) as { message?: string }
    return j.message ?? res.statusText
  } catch {
    return res.statusText
  }
}

export async function apiGet<T>(path: string, initData: string | undefined): Promise<T> {
  const headers: Record<string, string> = {}
  if (initData) headers.Authorization = `tma ${initData}`
  const res = await fetch(`${base()}${path}`, { headers })
  if (!res.ok) throw new ApiError(await parseError(res), res.status)
  return res.json() as Promise<T>
}

export async function apiSend<T>(
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  initData: string | undefined,
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (initData) headers.Authorization = `tma ${initData}`
  const res = await fetch(`${base()}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) throw new ApiError(await parseError(res), res.status)
  if (res.status === 204 || res.headers.get('content-length') === '0') return undefined as T
  const text = await res.text()
  if (!text) return undefined as T
  return JSON.parse(text) as T
}
