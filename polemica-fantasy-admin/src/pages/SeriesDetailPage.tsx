import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
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
  Table,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { TableProps } from 'antd'
import { MAX_EXPECTED_GAME_COUNT } from '../api/seriesRequests'
import { getTournament } from '../api/tournaments'
import {
  addSeriesGame,
  assignSeriesPlayers,
  calculateScores,
  createResultMafiaOverride,
  deleteSeriesGame,
  finalizeSeries,
  getSeriesCompletionPreview,
  getSeries,
  listSeriesGames,
  syncGames,
  updateSeries,
} from '../api/series'
import type { UpdateSeriesRequest } from '../api/seriesRequests'
import type { AdminSeriesGameDto, SeriesStatus, TournamentKind } from '../api/types'
import { ApiError } from '../api/client'
import { SeriesResultsDrawer } from '../components/SeriesResultsDrawer'

export function SeriesDetailPage() {
  const { id } = useParams<{ id: string }>()
  const seriesId = Number(id)
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [form] = Form.useForm<{
    name: string
    namePrefix: string
    gameStartedOn: ReturnType<typeof dayjs> | null
    gameNumFrom: number | null
    gameNumTo: number | null
    gamePhase: number | 'ALL'
    status: SeriesStatus
    startsAt: ReturnType<typeof dayjs>
    teamDeadline: ReturnType<typeof dayjs>
    expectedGameCount: number | null
    streamLinks: { label?: string | null; url: string }[]
  }>()
  const [addGameForm] = Form.useForm<{ polemicaGameId: number }>()
  const [resultOverrideForm] = Form.useForm<{
    gameNumber: number
    correctedMafiaLine: string
    reason: string
  }>()
  const [selectedPlayerIds, setSelectedPlayerIds] = useState<number[]>([])
  const [replacementPolemicaUserIds, setReplacementPolemicaUserIds] = useState<
    Record<number, number | null>
  >({})
  const [replacementModalOpen, setReplacementModalOpen] = useState(false)
  const [resultsDrawerOpen, setResultsDrawerOpen] = useState(false)
  const [resultOverrideModalOpen, setResultOverrideModalOpen] = useState(false)

  const q = useQuery({
    queryKey: ['admin', 'series', seriesId],
    queryFn: () => getSeries(seriesId),
    enabled: Number.isFinite(seriesId),
  })

  const tournamentId = q.data?.tournamentId

  const invalidateSeriesGameState = () => {
    void qc.invalidateQueries({ queryKey: ['admin', 'series', seriesId] })
    void qc.invalidateQueries({ queryKey: ['admin', 'series', seriesId, 'games'] })
    void qc.invalidateQueries({ queryKey: ['admin', 'series', seriesId, 'results'] })
    if (tournamentId != null) {
      void qc.invalidateQueries({
        queryKey: ['admin', 'series', 'tournament', tournamentId],
      })
    }
  }

  const tq = useQuery({
    queryKey: ['admin', 'tournament', tournamentId],
    queryFn: () => getTournament(tournamentId!),
    enabled: tournamentId != null,
  })

  const tournamentKind: TournamentKind = tq.data?.kind ?? 'STANDALONE'
  const isCompetition = tournamentKind === 'POLEMICA_COMPETITION'

  const gamesQ = useQuery({
    queryKey: ['admin', 'series', seriesId, 'games'],
    queryFn: () => listSeriesGames(seriesId),
    enabled: Number.isFinite(seriesId),
  })

  useEffect(() => {
    const s = q.data
    if (!s) return
    form.setFieldsValue({
      name: s.name,
      namePrefix: s.namePrefix ?? '',
      gameStartedOn: s.gameStartedOn ? dayjs(s.gameStartedOn, 'YYYY-MM-DD') : null,
      gameNumFrom: s.gameNumFrom ?? null,
      gameNumTo: s.gameNumTo ?? null,
      gamePhase: s.gamePhase == null ? 'ALL' : s.gamePhase,
      status: s.status,
      startsAt: dayjs(s.startsAt),
      teamDeadline: dayjs(s.teamDeadline),
      expectedGameCount: s.expectedGameCount,
      streamLinks: s.streamLinks ?? [],
    })
    queueMicrotask(() => setSelectedPlayerIds(s.tournamentPlayerIds ?? []))
    queueMicrotask(() => setReplacementPolemicaUserIds(s.replacementPolemicaUserIds ?? {}))
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
    mutationFn: (ids: number[]) => {
      const selected = new Set(ids)
      const replacements = Object.fromEntries(
        Object.entries(replacementPolemicaUserIds)
          .map(([tpId, replacement]) => [Number(tpId), replacement] as const)
          .filter(([tpId, replacement]) => selected.has(tpId) && replacement != null),
      )
      return assignSeriesPlayers(seriesId, {
        tournamentPlayerIds: ids,
        replacementPolemicaUserIds: replacements,
      })
    },
    onSuccess: () => {
      message.success('Players assigned')
      void qc.invalidateQueries({ queryKey: ['admin', 'series', seriesId] })
      void qc.invalidateQueries({ queryKey: ['admin', 'series', seriesId, 'results'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const syncMut = useMutation({
    mutationFn: () => syncGames(seriesId),
    onSuccess: () => {
      message.success('Sync completed')
      invalidateSeriesGameState()
    },
    onError: (e: Error) => message.error(e.message),
  })

  const scoreMut = useMutation({
    mutationFn: () => calculateScores(seriesId),
    onSuccess: () => {
      message.success('Scores calculated')
      invalidateSeriesGameState()
    },
    onError: (e: Error) => message.error(e.message),
  })

  const addGameMut = useMutation({
    mutationFn: (polemicaGameId: number) => addSeriesGame(seriesId, { polemicaGameId }),
    onSuccess: () => {
      message.success('Game added. Recalculate scores when ready.')
      addGameForm.resetFields()
      invalidateSeriesGameState()
    },
    onError: (e: Error) => message.error(e.message),
  })

  const deleteGameMut = useMutation({
    mutationFn: (gameId: number) => deleteSeriesGame(seriesId, gameId),
    onSuccess: () => {
      message.success('Game deleted from scoring')
      invalidateSeriesGameState()
    },
    onError: (e: Error) => message.error(e.message),
  })

  const resultOverrideMut = useMutation({
    mutationFn: (body: { gameNumber: number; correctedMafiaLine: string; reason: string }) =>
      createResultMafiaOverride(seriesId, body),
    onSuccess: (override) => {
      message.success(`Telegram result corrected for game ${override.gameNumber}`)
      setResultOverrideModalOpen(false)
      resultOverrideForm.resetFields()
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
    onError: async (e: Error) => {
      const reconcileKey = `series-finalization-${seriesId}`
      const reconcile = async () => {
        try {
          const latest = await getSeries(seriesId)
          qc.setQueryData(['admin', 'series', seriesId], latest)
          return latest.finalized
        } catch {
          return false
        }
      }

      if (await reconcile()) {
        message.success('Series finalized; the response was delayed')
        invalidateSeriesGameState()
        void qc.invalidateQueries({ queryKey: ['admin', 'tournaments'] })
        return
      }
      if (e instanceof ApiError && e.status < 500) {
        message.error(e.message)
        return
      }

      message.loading({
        key: reconcileKey,
        content: 'The response was delayed. Checking finalization status…',
        duration: 0,
      })
      for (let attempt = 0; attempt < 60; attempt += 1) {
        await new Promise<void>((resolve) => window.setTimeout(resolve, 5_000))
        if (await reconcile()) {
          message.success({
            key: reconcileKey,
            content: 'Series finalized; the response was delayed',
          })
          invalidateSeriesGameState()
          void qc.invalidateQueries({ queryKey: ['admin', 'tournaments'] })
          return
        }
      }
      message.warning({
        key: reconcileKey,
        content: 'Finalization result is still unknown. Refresh the series before retrying.',
        duration: 0,
      })
    },
  })

  const completionPreviewMut = useMutation({
    mutationFn: () => getSeriesCompletionPreview(seriesId),
    onSuccess: (preview) => {
      if (!preview.ready) {
        message.error(`Series is not ready to finalize: ${preview.reason ?? 'unknown reason'}`)
        return
      }
      Modal.confirm({
        title: 'Finalize series?',
        content: (
          <Space direction="vertical">
            <Typography.Text>
              Card uses will be decremented and leaderboard rewards paid. This cannot be undone.
            </Typography.Text>
            <Typography.Text type="secondary">
              The backend will verify games, scores, roster, cards, rewards, and Telegram result evidence immediately before finalizing.
            </Typography.Text>
          </Space>
        ),
        okText: 'Finalize series',
        okButtonProps: { danger: true },
        onOk: () => finalizeMut.mutateAsync(),
      })
    },
    onError: (e: Error) => message.error(e.message),
  })

  if (!Number.isFinite(seriesId)) {
    return <Typography.Text type="danger">Invalid series id</Typography.Text>
  }

  const s = q.data
  const players = tq.data?.players ?? []
  const updateSelectedPlayerIds = (ids: number[]) => {
    setSelectedPlayerIds(ids)
    const selected = new Set(ids)
    setReplacementPolemicaUserIds((prev) =>
      Object.fromEntries(
        Object.entries(prev)
          .map(([tpId, replacement]) => [Number(tpId), replacement] as const)
          .filter(([tpId]) => selected.has(tpId)),
      ),
    )
  }
  const hasReplacementControl = (tpId: number) =>
    Object.prototype.hasOwnProperty.call(replacementPolemicaUserIds, tpId)
  const replacementRowIds = selectedPlayerIds.filter(hasReplacementControl)
  const addableReplacementIds = selectedPlayerIds.filter((tpId) => !hasReplacementControl(tpId))
  const renderOptionalNumber = (value: number | null) => value ?? '—'
  const gameColumns: TableProps<AdminSeriesGameDto>['columns'] = [
    {
      title: 'Game',
      dataIndex: 'displayName',
      render: (value: string, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          {record.gameName !== value && record.gameName && (
            <Typography.Text type="secondary">{record.gameName}</Typography.Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Polemica ID',
      dataIndex: 'polemicaGameId',
      width: 130,
    },
    {
      title: 'Num',
      dataIndex: 'gameNum',
      width: 80,
      render: renderOptionalNumber,
    },
    {
      title: 'Table',
      dataIndex: 'table',
      width: 80,
      render: renderOptionalNumber,
    },
    {
      title: 'Phase',
      dataIndex: 'phase',
      width: 80,
      render: renderOptionalNumber,
    },
    {
      title: 'Played at',
      dataIndex: 'playedAt',
      width: 160,
      render: (value: string | null) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'),
    },
    {
      title: 'State',
      width: 190,
      render: (_, record) => (
        <Space size={4} wrap>
          <Tag color={record.finished ? 'green' : undefined}>
            {record.finished ? 'finished' : 'unfinished'}
          </Tag>
          <Tag color={record.scored ? 'blue' : 'orange'}>
            {record.scored ? 'scored' : 'not scored'}
          </Tag>
        </Space>
      ),
    },
    {
      title: 'Actions',
      width: 110,
      render: (_, record) => (
        <Button
          danger
          size="small"
          disabled={!!s?.finalized}
          loading={deleteGameMut.isPending}
          onClick={() =>
            Modal.confirm({
              title: 'Delete game from series?',
              content: `${record.displayName} will be removed from saved score breakdowns and totals.`,
              okText: 'Delete',
              okButtonProps: { danger: true },
              onOk: () => deleteGameMut.mutate(record.id),
            })
          }
        >
          Delete
        </Button>
      ),
    },
  ]

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
            expectedGameCount: v.expectedGameCount,
            streamLinks: v.streamLinks ?? [],
          }
          if (isCompetition) {
            updateMut.mutate({
              ...base,
              gameNumFrom: v.gameNumFrom ?? undefined,
              gameNumTo: v.gameNumTo ?? undefined,
              gamePhase: v.gamePhase === 'ALL' ? null : v.gamePhase,
              namePrefix: v.namePrefix?.trim() || undefined,
            })
          } else {
            updateMut.mutate({
              ...base,
              namePrefix: v.namePrefix,
              gameStartedOn: v.gameStartedOn ? v.gameStartedOn.format('YYYY-MM-DD') : null,
            })
          }
        }}
        style={{ maxWidth: 560 }}
      >
        <Form.Item name="name" label="Name" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        {!isCompetition && (
          <>
            <Form.Item name="namePrefix" label="Name prefix" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="gameStartedOn" label="Game started day (optional)">
              <DatePicker format="YYYY-MM-DD" allowClear style={{ width: '100%' }} />
            </Form.Item>
          </>
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
            <Form.Item name="gamePhase" label="Phase filter">
              <Select
                options={[
                  { value: 0, label: 'Phase 0 (default)' },
                  { value: 1, label: 'Phase 1 (semifinal)' },
                  { value: 2, label: 'Phase 2 (final)' },
                  { value: 'ALL', label: 'All phases (null)' },
                ]}
              />
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
        <Form.Item
          name="expectedGameCount"
          label="Expected game count"
          rules={[({ getFieldValue }) => ({
            validator: (_, value) =>
              getFieldValue('status') !== 'SCORING' || value != null
                ? Promise.resolve()
                : Promise.reject(new Error('Required before SCORING')),
          })]}
        >
          <InputNumber min={1} max={MAX_EXPECTED_GAME_COUNT} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item label="Series stream links">
          <Form.List name="streamLinks">
            {(fields, { add, remove }) => (
              <Space direction="vertical" style={{ width: '100%' }}>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" style={{ display: 'flex' }}>
                    <Form.Item name={[field.name, 'label']} style={{ marginBottom: 8 }}>
                      <Input placeholder="Label, e.g. Table 1" />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'url']}
                      rules={[{ required: true, message: 'URL is required' }]}
                      style={{ marginBottom: 8, flex: 1 }}
                    >
                      <Input placeholder="https://..." />
                    </Form.Item>
                    <Button
                      aria-label="Remove stream link"
                      icon={<DeleteOutlined />}
                      onClick={() => remove(field.name)}
                    />
                  </Space>
                ))}
                <Button icon={<PlusOutlined />} onClick={() => add({ label: '', url: '' })}>
                  Add stream link
                </Button>
              </Space>
            )}
          </Form.List>
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
          onChange={updateSelectedPlayerIds}
        />
        <Space wrap>
          <Button
            disabled={players.length === 0 || tq.isLoading}
            onClick={() => updateSelectedPlayerIds(players.map((p) => p.id))}
          >
            Add all tournament players
          </Button>
          <Button
            disabled={selectedPlayerIds.length === 0}
            onClick={() => setReplacementModalOpen(true)}
          >
            Replacements{replacementRowIds.length > 0 ? ` (${replacementRowIds.length})` : ''}
          </Button>
        </Space>
        <Button
          type="primary"
          loading={assignMut.isPending}
          onClick={() => assignMut.mutate(selectedPlayerIds)}
        >
          Assign players
        </Button>
      </Space>

      <Modal
        title="Scoring replacements"
        open={replacementModalOpen}
        footer={null}
        onCancel={() => setReplacementModalOpen(false)}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            allowClear
            showSearch
            placeholder="Add replacement for player"
            style={{ width: '100%' }}
            disabled={addableReplacementIds.length === 0}
            value={null}
            options={addableReplacementIds.map((tpId) => {
              const player = players.find((p) => p.id === tpId)
              return {
                value: tpId,
                label: `${player?.nickname ?? `#${tpId}`} (id ${tpId}, polemica ${player?.polemicaUserId ?? '—'})`,
              }
            })}
            filterOption={(input, option) =>
              String(option?.label ?? '')
                .toLowerCase()
                .includes(input.trim().toLowerCase())
            }
            onChange={(tpId) => {
              if (tpId == null) return
              setReplacementPolemicaUserIds((prev) => ({
                ...prev,
                [tpId]: null,
              }))
            }}
          />
          {replacementRowIds.length === 0 && (
            <Typography.Text type="secondary">No replacements configured.</Typography.Text>
          )}
          {replacementRowIds.map((tpId) => {
            const player = players.find((p) => p.id === tpId)
            return (
              <Space
                key={tpId}
                align="center"
                style={{ width: '100%', justifyContent: 'space-between' }}
              >
                <Typography.Text style={{ flex: 1 }}>
                  {player?.nickname ?? `Tournament player ${tpId}`}
                </Typography.Text>
                <InputNumber
                  min={1}
                  precision={0}
                  placeholder="Polemica ID"
                  value={replacementPolemicaUserIds[tpId] ?? null}
                  onChange={(value) =>
                    setReplacementPolemicaUserIds((prev) => ({
                      ...prev,
                      [tpId]: value == null ? null : Number(value),
                    }))
                  }
                  style={{ width: 160 }}
                />
                <Button
                  size="small"
                  onClick={() =>
                    setReplacementPolemicaUserIds((prev) => {
                      const next = { ...prev }
                      delete next[tpId]
                      return next
                    })
                  }
                >
                  Remove
                </Button>
              </Space>
            )
          })}
          <Typography.Text type="secondary">
            Save with Assign players, then recalculate scores.
          </Typography.Text>
        </Space>
      </Modal>

      <Typography.Title level={4} style={{ marginTop: 24 }}>
        Games
      </Typography.Title>
      <Typography.Paragraph type="secondary">
        Registered Polemica games for this series. Adding a game does not recalculate
        scores automatically.
      </Typography.Paragraph>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Form
          form={addGameForm}
          layout="inline"
          onFinish={(v) => addGameMut.mutate(Number(v.polemicaGameId))}
        >
          <Form.Item
            name="polemicaGameId"
            rules={[{ required: true, message: 'Enter Polemica game id' }]}
          >
            <InputNumber
              min={1}
              precision={0}
              placeholder="Polemica game id"
              disabled={!!s?.finalized}
              style={{ width: 180 }}
            />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              disabled={!!s?.finalized}
              loading={addGameMut.isPending}
            >
              Add game
            </Button>
          </Form.Item>
          <Form.Item>
            <Button onClick={() => void gamesQ.refetch()} loading={gamesQ.isFetching}>
              Refresh
            </Button>
          </Form.Item>
        </Form>
        {s?.finalized && (
          <Typography.Text type="secondary">
            Finalized series cannot be changed.
          </Typography.Text>
        )}
        {gamesQ.error && (
          <Typography.Text type="danger">{gamesQ.error.message}</Typography.Text>
        )}
        <Table<AdminSeriesGameDto>
          rowKey="id"
          size="small"
          columns={gameColumns}
          dataSource={gamesQ.data ?? []}
          loading={gamesQ.isLoading}
          pagination={false}
          scroll={{ x: 950 }}
          locale={{ emptyText: 'No games registered yet' }}
        />
      </Space>

      <Typography.Title level={4} style={{ marginTop: 24 }}>
        Actions
      </Typography.Title>
      <Space wrap>
        <Button onClick={() => setResultsDrawerOpen(true)}>Player results</Button>
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
          disabled={!!q.data?.finalized}
          onClick={() => setResultOverrideModalOpen(true)}
        >
          Correct Telegram result
        </Button>
        <Button
          danger
          disabled={!!q.data?.finalized}
          onClick={() => completionPreviewMut.mutate()}
          loading={completionPreviewMut.isPending || finalizeMut.isPending}
        >
          {finalizeMut.isPending ? 'Finalizing…' : 'Finalize series'}
        </Button>
      </Space>
      <Modal
        title="Correct Telegram mafia line"
        open={resultOverrideModalOpen}
        okText="Save audited correction"
        confirmLoading={resultOverrideMut.isPending}
        onCancel={() => setResultOverrideModalOpen(false)}
        onOk={() => resultOverrideForm.submit()}
      >
        <Typography.Paragraph type="secondary">
          The original Telegram revision remains unchanged. The correction is accepted only when
          it exactly matches the current Polemica roles and is stored with the admin actor and reason.
        </Typography.Paragraph>
        <Form
          form={resultOverrideForm}
          layout="vertical"
          onFinish={(values) => resultOverrideMut.mutate(values)}
        >
          <Form.Item
            name="gameNumber"
            label="Game number"
            rules={[{ required: true }]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="correctedMafiaLine"
            label="Correct mafia line"
            rules={[{ required: true, whitespace: true, max: 512 }]}
          >
            <Input placeholder="Player 1, Player 2, Player 3" />
          </Form.Item>
          <Form.Item
            name="reason"
            label="Reason"
            rules={[{ required: true, whitespace: true, min: 5, max: 512 }]}
          >
            <Input.TextArea rows={3} placeholder="Source post cannot be edited; duplicate nickname corrected from Polemica roles" />
          </Form.Item>
        </Form>
      </Modal>
      <SeriesResultsDrawer
        seriesId={seriesId}
        open={resultsDrawerOpen}
        onClose={() => setResultsDrawerOpen(false)}
      />
    </div>
  )
}
