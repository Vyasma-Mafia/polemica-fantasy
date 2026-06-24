import {
  App,
  Avatar,
  Button,
  Card,
  Checkbox,
  List,
  Modal,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addTournamentPlayer,
  getTournament,
  openTournamentReport,
  patchTournamentPlayer,
  removeTournamentPlayer,
  uploadPlayerPhoto,
} from '../api/tournaments'
import {
  createSeries as createSeriesApi,
  listSeriesByTournament,
} from '../api/series'
import { listFantasyPlayers } from '../api/fantasyPlayers'
import type { AddTournamentPlayerRequest } from '../api/tournamentRequests'
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
  const [reportOpen, setReportOpen] = useState(false)
  const [selectedReportSeriesIds, setSelectedReportSeriesIds] = useState<number[]>([])

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

  const playersCatalogQ = useQuery({
    queryKey: ['admin', 'fantasy-players', 'add-to-tournament'],
    queryFn: () => listFantasyPlayers(),
    enabled: addPlayerOpen,
  })

  const addPlayer = useMutation({
    mutationFn: (body: AddTournamentPlayerRequest) => addTournamentPlayer(tournamentId, body),
    onSuccess: () => {
      message.success('Player added')
      setAddPlayerOpen(false)
      void qc.invalidateQueries({ queryKey: ['admin', 'tournament', tournamentId] })
      void qc.invalidateQueries({ queryKey: ['admin', 'fantasy-players'] })
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

  const patchPackPool = useMutation({
    mutationFn: ({
      playerId,
      excludedFromPackPool,
    }: {
      playerId: number
      excludedFromPackPool: boolean
    }) =>
      patchTournamentPlayer(tournamentId, playerId, { excludedFromPackPool }),
    onSuccess: () => {
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
      void qc.invalidateQueries({ queryKey: ['admin', 'tournaments'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const openReportMut = useMutation({
    mutationFn: (seriesIds: number[]) =>
      openTournamentReport(tournamentId, seriesIds),
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

      <Typography.Title level={4}>Series</Typography.Title>
      <Space style={{ marginBottom: 8 }}>
        <Button
          type="primary"
          disabled={!t}
          onClick={() => setSeriesOpen(true)}
        >
          New series
        </Button>
        <Button
          disabled={!t || (sq.data?.length ?? 0) === 0}
          onClick={() => {
            setSelectedReportSeriesIds((sq.data ?? []).map((series) => series.id))
            setReportOpen(true)
          }}
        >
          HTML report
        </Button>
      </Space>
      <Table
        rowKey="id"
        loading={sq.isLoading}
        dataSource={sq.data}
        style={{ marginBottom: 24 }}
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
                : row.gameStartedOn
                  ? `${row.namePrefix ?? '—'} · ${row.gameStartedOn}`
                  : (row.namePrefix ?? '—'),
          },
          {
            title: 'Synced',
            dataIndex: 'syncedGamesCount',
            width: 90,
            render: (n: number) => n,
          },
          {
            title: 'Scored',
            dataIndex: 'scoredGamesCount',
            width: 90,
            render: (n: number) => n,
          },
          {
            title: 'Status',
            dataIndex: 'status',
            render: (s: string) => <Tag>{s}</Tag>,
          },
          {
            title: 'Finalized',
            dataIndex: 'finalized',
            width: 100,
            render: (v: boolean) => (v ? <Tag color="blue">Yes</Tag> : <Tag>No</Tag>),
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

      <Typography.Title level={4} style={{ marginTop: 8 }}>
        Players
      </Typography.Title>
      <Space style={{ marginBottom: 8 }}>
        <Button type="primary" onClick={() => setAddPlayerOpen(true)}>
          Add player
        </Button>
      </Space>

      {(t?.players?.length ?? 0) > 0 && (
        <List
          grid={{
            gutter: [16, 16],
            xs: 1,
            sm: 2,
            md: 3,
            lg: 4,
            xl: 5,
          }}
          dataSource={t?.players ?? []}
          loading={tq.isLoading}
          style={{ marginBottom: 24 }}
          renderItem={(p) => (
            <List.Item>
              <Card size="small" styles={{ body: { padding: 12 } }}>
                <Card.Meta
                  avatar={
                    <Avatar
                      src={p.photoUrl ?? undefined}
                      size={72}
                      style={{ flexShrink: 0 }}
                    >
                      {(p.nickname || '?').slice(0, 1).toUpperCase()}
                    </Avatar>
                  }
                  title={
                    <Typography.Text ellipsis title={p.nickname}>
                      {p.nickname}
                    </Typography.Text>
                  }
                  description={
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      #{p.id}
                    </Typography.Text>
                  }
                />
              </Card>
            </List.Item>
          )}
        />
      )}

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
            width: 100,
            render: (url: string | null, row) => (
              <Space size="small" align="center">
                <Avatar src={url ?? undefined} size={40}>
                  {(row.nickname || '?').slice(0, 1).toUpperCase()}
                </Avatar>
                {url ? (
                  <a href={url} target="_blank" rel="noreferrer">
                    open
                  </a>
                ) : null}
              </Space>
            ),
          },
          {
            title: (
              <Tooltip title="Исключить из рандомного пула паков этого турнира (вылетевшие и т.п.)">
                <span>Пул пака</span>
              </Tooltip>
            ),
            key: 'packPool',
            width: 110,
            render: (_, row) => (
              <Tooltip title="Выкл — может выпасть в паке; вкл — не выпадает">
                <Switch
                  checked={row.excludedFromPackPool ?? false}
                  loading={
                    patchPackPool.isPending &&
                    patchPackPool.variables?.playerId === row.id
                  }
                  onChange={(checked) =>
                    patchPackPool.mutate({
                      playerId: row.id,
                      excludedFromPackPool: checked,
                    })
                  }
                />
              </Tooltip>
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

      <Modal
        title="Add player"
        open={addPlayerOpen}
        onCancel={() => setAddPlayerOpen(false)}
        footer={null}
        destroyOnClose
      >
        <PlayerAddForm
          loading={addPlayer.isPending}
          players={playersCatalogQ.data}
          playersLoading={playersCatalogQ.isLoading}
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

      <Modal
        title="HTML report"
        open={reportOpen}
        onCancel={() => setReportOpen(false)}
        footer={[
          <Button key="clear" onClick={() => setSelectedReportSeriesIds([])}>
            Clear
          </Button>,
          <Button
            key="all"
            onClick={() =>
              setSelectedReportSeriesIds((sq.data ?? []).map((series) => series.id))
            }
          >
            Select all
          </Button>,
          <Button
            key="open"
            type="primary"
            loading={openReportMut.isPending}
            disabled={selectedReportSeriesIds.length === 0}
            onClick={() => openReportMut.mutate(selectedReportSeriesIds)}
          >
            Open report
          </Button>,
        ]}
        width={680}
      >
        <Typography.Paragraph type="secondary">
          Choose series to include in the standalone HTML report.
        </Typography.Paragraph>
        <Checkbox.Group
          value={selectedReportSeriesIds}
          onChange={(values) =>
            setSelectedReportSeriesIds(values.map((value) => Number(value)))
          }
          style={{ width: '100%' }}
        >
          <List
            dataSource={sq.data ?? []}
            loading={sq.isLoading}
            bordered
            renderItem={(series) => (
              <List.Item>
                <Checkbox value={series.id} style={{ width: '100%' }}>
                  <Space wrap>
                    <Typography.Text strong>{series.name}</Typography.Text>
                    <Tag>{series.status}</Tag>
                    <Typography.Text type="secondary">
                      synced {series.syncedGamesCount} · scored{' '}
                      {series.scoredGamesCount}
                    </Typography.Text>
                  </Space>
                </Checkbox>
              </List.Item>
            )}
          />
        </Checkbox.Group>
      </Modal>
    </div>
  )
}
