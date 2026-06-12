import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { apiGet } from '../api/client'
import type { SeriesPlayerEntry } from '../api/types'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { PlayerImage } from '../components/PlayerImage'
import { useInitData } from '../context/useInitData'

export function ParticipantsPage() {
  const { tournamentId } = useParams<{ tournamentId: string }>()
  const id = Number(tournamentId)
  const initData = useInitData()

  const q = useQuery({
    queryKey: ['tournament-participants', id, initData],
    queryFn: () => apiGet<SeriesPlayerEntry[]>(`/api/v1/tournaments/${id}/participants`, initData),
    enabled: !!initData && Number.isFinite(id),
  })

  if (!initData) return <MissingInitDataNotice />
  if (q.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (q.isError) return <p className="pf-err">{(q.error as Error).message}</p>

  const rows = q.data ?? []
  const back = `/tournaments/${id}`

  return (
    <div className="pf-page">
      <PageHeader title="Участники" backTo={back} />
      <ul className="pf-participants">
        {rows.map((p) => (
          <li key={p.tournamentPlayerId} className="pf-participants__row">
            <PlayerImage
              src={p.photoUrl}
              seedId={p.fantasyPlayerId}
              variant="avatar"
              className="pf-participants__avatar"
            />
            <span className="pf-participants__name">{p.nickname}</span>
          </li>
        ))}
      </ul>
      {rows.length === 0 && <p className="pf-muted">В ростере турнира пока никого нет.</p>}
      <p className="pf-footer-link">
        <Link to={back}>← К турниру</Link>
      </p>
    </div>
  )
}
