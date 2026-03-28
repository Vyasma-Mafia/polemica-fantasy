import {
  App,
  Button,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addTournamentPlayer,
  getTournament,
  removeTournamentPlayer,
  uploadPlayerPhoto,
} from '../api/tournaments'
import {
  createSeries as createSeriesApi,
  listSeriesByTournament,
} from '../api/series'
import type { CreateSeriesRequest } from '../api/seriesRequests'
import { SeriesFormModal } from './SeriesFormModal'
import { PlayerAddForm } from './PlayerAddForm'

export function TournamentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const tournamentId = Number(id)
  const qc = useQueryClient()
  const { message } = App.useApp()

  const [addPlayerOpen, setAddPlayerOpen] = useState(false)
  const [seriesOpen, setSeriesOpen] = useState(false)

  const tq = useQuery({
    queryKey: ['admin', 'tournament', tournamentId],
    queryFn: () => getTournament(tournamentId),
    enabled: Number.isFinite(tournamentId),
  })

  const sq = useQuery({
    queryKey: ['admin', 'series', 'tournament', tournamentId],
    queryFn: () => listSeriesByTournament(tournamentId),
    enabled: Number.isFinite(tournamentId),
  })

  const addPlayer = useMutation({
    mutationFn: (body: { polemicaUserId: number; nickname: string }) =>
      addTournamentPlayer(tournamentId, body),
    onSuccess: () => {
      message.success('Player added')
      setAddPlayerOpen(false)
      void qc.invalidateQueries({ queryKey: ['admin', 'tournament', tournamentId] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const removePlayer = useMutation({
    mutationFn: (playerId: number) =>
      removeTournamentPlayer(tournamentId, playerId),
    onSuccess: () => {
      message.success('Player removed')
      void qc.invalidateQueries({ queryKey: ['admin', 'tournament', tournamentId] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const uploadPhoto = useMutation({
    mutationFn: ({
      playerId,
      file,
    }: {
      playerId: number
      file: File
    }) => uploadPlayerPhoto(tournamentId, playerId, file),
    onSuccess: () => {
      message.success('Photo uploaded')
      void qc.invalidateQueries({ queryKey: ['admin', 'tournament', tournamentId] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const createSeriesMut = useMutation({
    mutationFn: ({
      tournamentId: tid,
      body,
    }: {
      tournamentId: number
      body: CreateSeriesRequest
    }) => createSeriesApi(tid, body),
    onSuccess: () => {
      message.success('Series created')
      setSeriesOpen(false)
      void qc.invalidateQueries({
        queryKey: ['admin', 'series', 'tournament', tournamentId],
      })
    },
    onError: (e: Error) => message.error(e.message),
  })

  if (!Number.isFinite(tournamentId)) {
    return <Typography.Text type="danger">Invalid tournament id</Typography.Text>
  }

  const t = tq.data

  return (
    <div>
      <Typography.Title level={3}>
        {t ? (
          <>
            {t.name}{' '}
            <Tag>{t.kind}</Tag>
            <Tag>{t.status}</Tag>
            {t.kind === 'POLEMICA_COMPETITION' && t.polemicaCompetitionId != null && (
              <Typography.Text type="secondary">
                {' '}
                competition #{t.polemicaCompetitionId}
              </Typography.Text>
            )}
          </>
        ) : (
          '…'
        )}
      </Typography.Title>
      {t?.description && (
        <Typography.Paragraph>{t.description}</Typography.Paragraph>
      )}

      <Typography.Title level={4}>Players</Typography.Title>
      <Space style={{ marginBottom: 8 }}>
        <Button type="primary" onClick={() => setAddPlayerOpen(true)}>
          Add player
        </Button>
      </Space>
      <Table
        rowKey="id"
        loading={tq.isLoading}
        dataSource={t?.players}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 70 },
          { title: 'Polemica user', dataIndex: 'polemicaUserId' },
          { title: 'Nickname', dataIndex: 'nickname' },
          {
            title: 'Photo',
            dataIndex: 'photoUrl',
            render: (url: string | null) =>
              url ? (
                <a href={url} target="_blank" rel="noreferrer">
                  link
                </a>
              ) : (
                '—'
              ),
          },
          {
            title: 'Upload',
            key: 'up',
            render: (_, row) => (
              <Upload
                showUploadList={false}
                beforeUpload={(file) => {
                  uploadPhoto.mutate({ playerId: row.id, file })
                  return false
                }}
              >
                <Button size="small" loading={uploadPhoto.isPending}>
                  Upload
                </Button>
              </Upload>
            ),
          },
          {
            title: '',
            key: 'rm',
            render: (_, row) => (
              <Button
                type="link"
                danger
                size="small"
                onClick={() => {
                  void Modal.confirm({
                    title: 'Remove player from tournament?',
                    onOk: () => removePlayer.mutate(row.id),
                  })
                }}
              >
                Remove
              </Button>
            ),
          },
        ]}
      />

      <Typography.Title level={4} style={{ marginTop: 24 }}>
        Series
      </Typography.Title>
      <Button
        type="primary"
        style={{ marginBottom: 8 }}
        disabled={!t}
        onClick={() => setSeriesOpen(true)}
      >
        New series
      </Button>
      <Table
        rowKey="id"
        loading={sq.isLoading}
        dataSource={sq.data}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 70 },
          {
            title: 'Name',
            dataIndex: 'name',
            render: (name: string, row) => (
              <Link to={`/series/${row.id}`}>{name}</Link>
            ),
          },
          {
            title: t?.kind === 'POLEMICA_COMPETITION' ? 'Games (num)' : 'Prefix',
            key: 'pf',
            render: (_, row) =>
              t?.kind === 'POLEMICA_COMPETITION'
                ? row.gameNumFrom != null && row.gameNumTo != null
                  ? `${row.gameNumFrom}–${row.gameNumTo}`
                  : '—'
                : (row.namePrefix ?? '—'),
          },
          {
            title: 'Status',
            dataIndex: 'status',
            render: (s: string) => <Tag>{s}</Tag>,
          },
          {
            title: 'Starts',
            dataIndex: 'startsAt',
            render: (x: string) => new Date(x).toLocaleString(),
          },
          {
            title: 'Deadline',
            dataIndex: 'teamDeadline',
            render: (x: string) => new Date(x).toLocaleString(),
          },
        ]}
      />

      <Modal
        title="Add player"
        open={addPlayerOpen}
        onCancel={() => setAddPlayerOpen(false)}
        footer={null}
        destroyOnClose
      >
        <PlayerAddForm
          loading={addPlayer.isPending}
          onSubmit={(v) => addPlayer.mutate(v)}
        />
      </Modal>

      <Modal
        title="Create series"
        open={seriesOpen}
        onCancel={() => setSeriesOpen(false)}
        footer={null}
        destroyOnClose
        width={560}
      >
        <SeriesFormModal
          tournamentKind={t?.kind ?? 'STANDALONE'}
          loading={createSeriesMut.isPending}
          onSubmit={(body) =>
            createSeriesMut.mutate({ tournamentId, body })
          }
        />
      </Modal>
    </div>
  )
}
