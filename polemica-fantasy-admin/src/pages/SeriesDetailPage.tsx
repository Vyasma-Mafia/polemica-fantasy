import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getTournament } from '../api/tournaments'
import {
  assignSeriesPlayers,
  calculateScores,
  finalizeSeries,
  getSeries,
  syncGames,
  updateSeries,
} from '../api/series'
import type { UpdateSeriesRequest } from '../api/seriesRequests'
import type { SeriesStatus, TournamentKind } from '../api/types'

export function SeriesDetailPage() {
  const { id } = useParams<{ id: string }>()
  const seriesId = Number(id)
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [form] = Form.useForm<{
    name: string
    namePrefix: string
    gameNumFrom: number | null
    gameNumTo: number | null
    status: SeriesStatus
    startsAt: ReturnType<typeof dayjs>
    teamDeadline: ReturnType<typeof dayjs>
  }>()
  const [selectedPlayerIds, setSelectedPlayerIds] = useState<number[]>([])

  const q = useQuery({
    queryKey: ['admin', 'series', seriesId],
    queryFn: () => getSeries(seriesId),
    enabled: Number.isFinite(seriesId),
  })

  const tournamentId = q.data?.tournamentId

  const tq = useQuery({
    queryKey: ['admin', 'tournament', tournamentId],
    queryFn: () => getTournament(tournamentId!),
    enabled: tournamentId != null,
  })

  const tournamentKind: TournamentKind = tq.data?.kind ?? 'STANDALONE'
  const isCompetition = tournamentKind === 'POLEMICA_COMPETITION'

  useEffect(() => {
    const s = q.data
    if (!s) return
    form.setFieldsValue({
      name: s.name,
      namePrefix: s.namePrefix ?? '',
      gameNumFrom: s.gameNumFrom ?? null,
      gameNumTo: s.gameNumTo ?? null,
      status: s.status,
      startsAt: dayjs(s.startsAt),
      teamDeadline: dayjs(s.teamDeadline),
    })
    queueMicrotask(() => setSelectedPlayerIds(s.tournamentPlayerIds ?? []))
  }, [q.data, form])

  const updateMut = useMutation({
    mutationFn: (body: UpdateSeriesRequest) => updateSeries(seriesId, body),
    onSuccess: () => {
      message.success('Series saved')
      void qc.invalidateQueries({ queryKey: ['admin', 'series', seriesId] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const assignMut = useMutation({
    mutationFn: (ids: number[]) =>
      assignSeriesPlayers(seriesId, { tournamentPlayerIds: ids }),
    onSuccess: () => {
      message.success('Players assigned')
      void qc.invalidateQueries({ queryKey: ['admin', 'series', seriesId] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const syncMut = useMutation({
    mutationFn: () => syncGames(seriesId),
    onSuccess: () => {
      message.success('Sync completed')
      void qc.invalidateQueries({ queryKey: ['admin', 'series', seriesId] })
      void qc.invalidateQueries({
        queryKey: ['admin', 'series', 'tournament', tournamentId],
      })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const scoreMut = useMutation({
    mutationFn: () => calculateScores(seriesId),
    onSuccess: () => {
      message.success('Scores calculated')
      void qc.invalidateQueries({ queryKey: ['admin', 'series', seriesId] })
      void qc.invalidateQueries({
        queryKey: ['admin', 'series', 'tournament', tournamentId],
      })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const finalizeMut = useMutation({
    mutationFn: () => finalizeSeries(seriesId),
    onSuccess: (res) => {
      message.success(
        `Finalized: rewards ${res.rewardsDistributed}, cards updated ${res.cardsDecremented}`,
      )
      void qc.invalidateQueries({ queryKey: ['admin', 'series', seriesId] })
      void qc.invalidateQueries({
        queryKey: ['admin', 'series', 'tournament', tournamentId],
      })
      void qc.invalidateQueries({ queryKey: ['admin', 'tournaments'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  if (!Number.isFinite(seriesId)) {
    return <Typography.Text type="danger">Invalid series id</Typography.Text>
  }

  const s = q.data
  const players = tq.data?.players ?? []

  return (
    <div>
      <Typography.Title level={3}>
        {s ? (
          <>
            {s.name} <Tag>{s.status}</Tag>
            {s.finalized && <Tag color="blue">Finalized</Tag>}
          </>
        ) : (
          '…'
        )}
      </Typography.Title>
      {s && (
        <Typography.Paragraph type="secondary" style={{ marginTop: -8 }}>
          Games in DB (synced): {s.syncedGamesCount} · With scores calculated:{' '}
          {s.scoredGamesCount}
        </Typography.Paragraph>
      )}

      <Form
        form={form}
        layout="vertical"
        onFinish={(v) => {
          const base: UpdateSeriesRequest = {
            name: v.name,
            status: v.status,
            startsAt: v.startsAt.toISOString(),
            teamDeadline: v.teamDeadline.toISOString(),
          }
          if (isCompetition) {
            updateMut.mutate({
              ...base,
              gameNumFrom: v.gameNumFrom ?? undefined,
              gameNumTo: v.gameNumTo ?? undefined,
              namePrefix: v.namePrefix?.trim() || undefined,
            })
          } else {
            updateMut.mutate({
              ...base,
              namePrefix: v.namePrefix,
            })
          }
        }}
        style={{ maxWidth: 560 }}
      >
        <Form.Item name="name" label="Name" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        {!isCompetition && (
          <Form.Item name="namePrefix" label="Name prefix" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        )}
        {isCompetition && (
          <>
            <Form.Item
              name="gameNumFrom"
              label="Game num from (inclusive)"
              rules={[{ required: true }]}
            >
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="gameNumTo"
              label="Game num to (inclusive)"
              rules={[{ required: true }]}
            >
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="namePrefix" label="Display label (optional)">
              <Input />
            </Form.Item>
          </>
        )}
        <Form.Item name="status" label="Status" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'UPCOMING', label: 'UPCOMING' },
              { value: 'ACTIVE', label: 'ACTIVE' },
              { value: 'SCORING', label: 'SCORING' },
              { value: 'FINISHED', label: 'FINISHED' },
            ]}
          />
        </Form.Item>
        <Form.Item name="startsAt" label="Starts at" rules={[{ required: true }]}>
          <DatePicker showTime style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="teamDeadline"
          label="Team deadline"
          rules={[{ required: true }]}
        >
          <DatePicker showTime style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={updateMut.isPending}>
            Save
          </Button>
        </Form.Item>
      </Form>

      <Typography.Title level={4}>Assign players</Typography.Title>
      <Typography.Paragraph type="secondary">
        Tournament players in this series. Save replaces the full roster with your selection.
      </Typography.Paragraph>
      <Space direction="vertical" style={{ width: '100%', maxWidth: 560 }}>
        <Select
          mode="multiple"
          allowClear
          showSearch
          placeholder="Tournament players — type to filter by nickname"
          style={{ width: '100%' }}
          loading={tq.isLoading}
          options={players.map((p) => ({
            value: p.id,
            label: `${p.nickname} (id ${p.id}, polemica ${p.polemicaUserId})`,
          }))}
          filterOption={(input, option) =>
            String(option?.label ?? '')
              .toLowerCase()
              .includes(input.trim().toLowerCase())
          }
          value={selectedPlayerIds}
          onChange={setSelectedPlayerIds}
        />
        <Button
          type="primary"
          loading={assignMut.isPending}
          onClick={() => assignMut.mutate(selectedPlayerIds)}
        >
          Assign players
        </Button>
      </Space>

      <Typography.Title level={4} style={{ marginTop: 24 }}>
        Actions
      </Typography.Title>
      <Space wrap>
        <Button
          onClick={() =>
            Modal.confirm({
              title: 'Sync games from Polemica?',
              onOk: () => syncMut.mutate(),
            })
          }
          loading={syncMut.isPending}
        >
          Sync games
        </Button>
        <Button
          type="primary"
          onClick={() =>
            Modal.confirm({
              title: 'Calculate scores for this series?',
              onOk: () => scoreMut.mutate(),
            })
          }
          loading={scoreMut.isPending}
        >
          Calculate scores
        </Button>
        <Button
          danger
          disabled={!!q.data?.finalized}
          onClick={() =>
            Modal.confirm({
              title: 'Finalize series?',
              content:
                'Card uses will be decremented and leaderboard rewards paid. This cannot be undone.',
              okText: 'Finalize',
              okButtonProps: { danger: true },
              onOk: () => finalizeMut.mutate(),
            })
          }
          loading={finalizeMut.isPending}
        >
          Finalize series
        </Button>
      </Space>
    </div>
  )
}
