import { App, Button, Modal, Space, Table, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import type { ActiveSeriesBriefDto } from '../api/types'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { batchStartSeries as batchStartSeriesApi } from '../api/series'
import {
  createTournament,
  listTournaments,
  updateTournament,
} from '../api/tournaments'
import type { TournamentKind, TournamentStatus } from '../api/types'
import { TournamentFormModal } from './TournamentFormModal'

const TOURNAMENT_STATUS_PRIORITY: Record<TournamentStatus, number> = {
  ACTIVE: 0,
  DRAFT: 1,
  FINISHED: 2,
}

export function TournamentsPage() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'tournaments'],
    queryFn: listTournaments,
  })

  const [createOpen, setCreateOpen] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)

  const createMut = useMutation({
    mutationFn: createTournament,
    onSuccess: () => {
      message.success('Tournament created')
      setCreateOpen(false)
      void qc.invalidateQueries({ queryKey: ['admin', 'tournaments'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const updateMut = useMutation({
    mutationFn: ({
      id,
      ...body
    }: {
      id: number
      name?: string | null
      description?: string | null
      status?: TournamentStatus | null
      kind?: TournamentKind | null
      polemicaCompetitionId?: number | null
    }) => updateTournament(id, body),
    onSuccess: () => {
      message.success('Tournament updated')
      setEditId(null)
      void qc.invalidateQueries({ queryKey: ['admin', 'tournaments'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const batchStartMut = useMutation({
    mutationFn: (seriesIds: number[]) => batchStartSeriesApi({ seriesIds }),
    onSuccess: (res) => {
      const started = res.startedSeries.length
      const skipped = res.skipped.length
      if (started > 0) {
        message.success(
          `Started ${started} series${skipped > 0 ? `; skipped ${skipped}` : ''}`,
        )
      } else {
        message.info('No series were started')
      }
      void qc.invalidateQueries({ queryKey: ['admin', 'tournaments'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const editing = data?.find((t) => t.id === editId)
  const upcomingSeriesIds = (data ?? [])
    .flatMap((t) => t.activeSeries ?? [])
    .filter((s) => s.status === 'UPCOMING')
    .map((s) => s.id)
  const sortedTournaments = useMemo(
    () =>
      [...(data ?? [])].sort(
        (a, b) =>
          TOURNAMENT_STATUS_PRIORITY[a.status] -
          TOURNAMENT_STATUS_PRIORITY[b.status],
      ),
    [data],
  )

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          Tournaments
        </Typography.Title>
        <Button type="primary" onClick={() => setCreateOpen(true)}>
          New tournament
        </Button>
        <Button
          disabled={upcomingSeriesIds.length === 0}
          loading={batchStartMut.isPending}
          onClick={() =>
            Modal.confirm({
              title: 'Start all UPCOMING series?',
              content: `This will move ${upcomingSeriesIds.length} series to ACTIVE across all tournaments and send one aggregated notification to users.`,
              okText: 'Start all',
              onOk: () => batchStartMut.mutate(upcomingSeriesIds),
            })
          }
        >
          Start all UPCOMING
        </Button>
      </Space>

      <Table
        rowKey="id"
        loading={isLoading}
        dataSource={sortedTournaments}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 80 },
          {
            title: 'Name',
            dataIndex: 'name',
            render: (name: string, row) => (
              <Link to={`/tournaments/${row.id}`}>{name}</Link>
            ),
          },
          {
            title: 'Kind',
            dataIndex: 'kind',
            render: (k: string) => <Tag>{k}</Tag>,
          },
          {
            title: 'Status',
            dataIndex: 'status',
            render: (s: string) => <Tag>{s}</Tag>,
          },
          {
            title: 'Active series',
            key: 'activeSeries',
            render: (_: unknown, row) => {
              const list = row.activeSeries ?? []
              if (list.length === 0) {
                return <Typography.Text type="secondary">—</Typography.Text>
              }
              return (
                <Space size={[4, 4]} wrap>
                  {list.map((s: ActiveSeriesBriefDto) => (
                    <Link key={s.id} to={`/series/${s.id}`}>
                      <Tag>
                        {s.name} <Typography.Text type="secondary">({s.status})</Typography.Text>
                      </Tag>
                    </Link>
                  ))}
                </Space>
              )
            },
          },
          {
            title: 'Created',
            dataIndex: 'createdAt',
            render: (t: string) => new Date(t).toLocaleString(),
          },
          {
            title: 'Actions',
            key: 'a',
            render: (_, row) => (
              <Button type="link" onClick={() => setEditId(row.id)}>
                Edit
              </Button>
            ),
          },
        ]}
      />

      <Modal
        title="Create tournament"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        footer={null}
        destroyOnClose
      >
        <TournamentFormModal
          onSubmit={(v) =>
            createMut.mutate({
              name: v.name,
              description: v.description,
              status: v.status,
              kind: v.kind,
              polemicaCompetitionId:
                v.kind === 'POLEMICA_COMPETITION'
                  ? v.polemicaCompetitionId ?? null
                  : null,
            })
          }
          loading={createMut.isPending}
        />
      </Modal>

      <Modal
        title="Edit tournament"
        open={editId != null}
        onCancel={() => setEditId(null)}
        footer={null}
        destroyOnClose
      >
        {editing && (
          <TournamentFormModal
            initial={{
              name: editing.name,
              description: editing.description,
              status: editing.status,
              kind: editing.kind,
              polemicaCompetitionId: editing.polemicaCompetitionId ?? undefined,
            }}
            onSubmit={(v) =>
              updateMut.mutate({
                id: editing.id,
                name: v.name,
                description: v.description,
                status: v.status,
                kind: v.kind,
                polemicaCompetitionId:
                  v.kind === 'POLEMICA_COMPETITION'
                    ? v.polemicaCompetitionId ?? null
                    : null,
              })
            }
            loading={updateMut.isPending}
            submitLabel="Save"
          />
        )}
      </Modal>
    </div>
  )
}
