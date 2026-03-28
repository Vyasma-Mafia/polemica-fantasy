# polemica-fantasy-admin

Admin SPA for Polemica Fantasy (Vite + React + TypeScript + Ant Design).

## Prerequisites

- Node 20+
- Backend running with `/api/v1/admin/**` (Basic Auth), e.g. `http://localhost:8080`

## Development

```bash
npm install
npm run dev
```

The Vite dev server proxies `/api` to `http://localhost:8080` (see `vite.config.ts`). Open the printed URL (e.g. `http://localhost:5173`), sign in with `ADMIN_USERNAME` / `ADMIN_PASSWORD` from the backend environment.

Optional: `VITE_API_BASE` (default `/api`) if you serve the API under another path.

## Build

```bash
npm run build
```

Static output in `dist/`.

## Lint

```bash
npm run lint
```
