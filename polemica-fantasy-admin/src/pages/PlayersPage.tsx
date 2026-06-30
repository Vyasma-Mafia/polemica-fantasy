import {
  Alert,
  App,
  Avatar,
  Button,
  Checkbox,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd'
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addFantasyPlayerAlias,
  createFantasyPlayer,
  getFantasyPlayerMergePreview,
  listFantasyPlayers,
  mergeFantasyPlayers,
  updateFantasyPlayer,
  uploadFantasyPlayerPhoto,
} from '../api/fantasyPlayers'
import { listTournaments, addTournamentPlayer } from '../api/tournaments'
import type { FantasyPlayerAdminDto, FantasyPlayerMergePreviewDto } from '../api/types'

interface CreatePlayerFormValues {
  polemicaUserId: number
  nickname: string
}

interface EditPlayerFormValues {
  nickname: string
}

interface AliasFormValues {
  polemicaUserId: number
  primary?: boolean
}

interface MergeFormValues {
  targetFantasyPlayerId: number
  reason: string
}

interface TargetPlayerOption {
  value: number
  label: string
  searchText: string
  fantasyIdText: string
  nicknameText: string
  aliasTexts: string[]
}

function targetPlayerRank(option: TargetPlayerOption, searchValue: string): number {
  const value = searchValue.trim().toLowerCase()
  if (!value) return 5
  if (option.fantasyIdText === value) return 0
  if (option.fantasyIdText.startsWith(value)) return 1
  if (option.aliasTexts.some((alias) => alias === value)) return 2
  if (option.aliasTexts.some((alias) => alias.startsWith(value))) return 3
  if (option.nicknameText.startsWith(value)) return 4
  return 5
}

export function PlayersPage() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [query, setQuery] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [editing, setEditing] = useState<FantasyPlayerAdminDto | null>(null)
  const [aliasing, setAliasing] = useState<FantasyPlayerAdminDto | null>(null)
  const [merging, setMerging] = useState<FantasyPlayerAdminDto | null>(null)
  const [mergePreview, setMergePreview] = useState<FantasyPlayerMergePreviewDto | null>(null)
  const [targetTournamentId, setTargetTournamentId] = useState<number | undefined>()
  const [createForm] = Form.useForm<CreatePlayerFormValues>()
  const [editForm] = Form.useForm<EditPlayerFormValues>()
  const [aliasForm] = Form.useForm<AliasFormValues>()
  const [mergeForm] = Form.useForm<MergeFormValues>()

  const playersQ = useQuery({
    queryKey: ['admin', 'fantasy-players', query],
    queryFn: () => listFantasyPlayers({ query }),
  })

  const mergeTargetsQ = useQuery({
    queryKey: ['admin', 'fantasy-players', 'merge-targets'],
    queryFn: () => listFantasyPlayers(),
    enabled: merging != null,
  })

  const tournamentsQ = useQuery({
    queryKey: ['admin', 'tournaments'],
    queryFn: listTournaments,
  })

  const targetPlayerOptions = useMemo<TargetPlayerOption[]>(
    () =>
      (mergeTargetsQ.data ?? [])
        .filter((player) => player.id !== merging?.id)
        .map((player) => {
          const aliases = player.aliases?.length
            ? player.aliases.map((alias) => alias.polemicaUserId)
            : [player.polemicaUserId]
          const aliasTexts = aliases.map(String)
          return {
            value: player.id,
            label: `#${player.id} ${player.nickname} · fantasy ${player.id} · Polemica ${aliasTexts.join(', ')}`,
            searchText: [
              player.id,
              `#${player.id}`,
              player.nickname,
              ...aliasTexts,
            ].join(' ').toLowerCase(),
            fantasyIdText: String(player.id),
            nicknameText: player.nickname.toLowerCase(),
            aliasTexts,
          }
        }),
    [mergeTargetsQ.data, merging?.id],
  )

  const createMut = useMutation({
    mutationFn: createFantasyPlayer,
    onSuccess: () => {
      message.success('Player created')
      setCreateOpen(false)
      createForm.resetFields()
      void qc.invalidateQueries({ queryKey: ['admin', 'fantasy-players'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const editMut = useMutation({
    mutationFn: ({ id, nickname }: { id: number; nickname: string }) =>
      updateFantasyPlayer(id, { nickname }),
    onSuccess: () => {
      message.success('Player updated')
      setEditing(null)
      void qc.invalidateQueries({ queryKey: ['admin', 'fantasy-players'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'tournament'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const uploadMut = useMutation({
    mutationFn: ({ id, file }: { id: number; file: File }) =>
      uploadFantasyPlayerPhoto(id, file),
    onSuccess: () => {
      message.success('Photo uploaded')
      void qc.invalidateQueries({ queryKey: ['admin', 'fantasy-players'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'tournament'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const aliasMut = useMutation({
    mutationFn: ({ id, values }: { id: number; values: AliasFormValues }) =>
      addFantasyPlayerAlias(id, {
        polemicaUserId: values.polemicaUserId,
        primary: values.primary ?? false,
      }),
    onSuccess: () => {
      message.success('Alias added')
      setAliasing(null)
      aliasForm.resetFields()
      void qc.invalidateQueries({ queryKey: ['admin', 'fantasy-players'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'tournament'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const previewMergeMut = useMutation({
    mutationFn: ({
      sourceId,
      targetId,
    }: {
      sourceId: number
      targetId: number
    }) => getFantasyPlayerMergePreview(targetId, sourceId),
    onSuccess: (preview) => setMergePreview(preview),
    onError: (e: Error) => message.error(e.message),
  })

  const mergeMut = useMutation({
    mutationFn: ({
      targetId,
      sourceId,
      reason,
    }: {
      targetId: number
      sourceId: number
      reason: string
    }) => mergeFantasyPlayers(targetId, { sourceFantasyPlayerId: sourceId, reason }),
    onSuccess: (result) => {
      message.success(`Players merged, audit #${result.auditId}`)
      setMerging(null)
      setMergePreview(null)
      mergeForm.resetFields()
      void qc.invalidateQueries({ queryKey: ['admin', 'fantasy-players'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'tournament'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'tournaments'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'card-merges'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const addToTournamentMut = useMutation({
    mutationFn: ({
      tournamentId,
      playerId,
    }: {
      tournamentId: number
      playerId: number
    }) => addTournamentPlayer(tournamentId, { fantasyPlayerId: playerId }),
    onSuccess: (_result, variables) => {
      message.success('Player added to tournament')
      void qc.invalidateQueries({ queryKey: ['admin', 'fantasy-players'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'tournament', variables.tournamentId] })
      void qc.invalidateQueries({ queryKey: ['admin', 'tournaments'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Typography.Title level={3} style={{ margin: 0 }}>
          Players
        </Typography.Title>
        <Input.Search
          allowClear
          placeholder="Nickname, fantasy id, Polemica id"
          onSearch={(value) => setQuery(value)}
          style={{ width: 320 }}
        />
        <Select
          allowClear
          showSearch
          loading={tournamentsQ.isLoading}
          placeholder="Target tournament"
          style={{ width: 280 }}
          value={targetTournamentId}
          onChange={setTargetTournamentId}
          filterOption={(input, option) =>
            String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
          }
          options={(tournamentsQ.data ?? []).map((tournament) => ({
            value: tournament.id,
            label: `#${tournament.id} ${tournament.name}`,
          }))}
        />
        <Button type="primary" onClick={() => setCreateOpen(true)}>
          New player
        </Button>
      </Space>

      <Table
        rowKey="id"
        loading={playersQ.isLoading}
        dataSource={playersQ.data}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 80 },
          {
            title: 'Photo',
            dataIndex: 'photoUrl',
            width: 110,
            render: (url: string | null, row) => (
              <Space size="small">
                <Avatar src={url ?? undefined} size={44}>
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
            title: 'Nickname',
            dataIndex: 'nickname',
            render: (nickname: string) => <Typography.Text strong>{nickname}</Typography.Text>,
          },
          { title: 'Polemica user', dataIndex: 'polemicaUserId' },
          {
            title: 'Aliases',
            key: 'aliases',
            width: 220,
            render: (_, row) => (
              <Space size={[4, 4]} wrap>
                {(row.aliases?.length ? row.aliases : [
                  { id: 0, polemicaUserId: row.polemicaUserId, primary: true },
                ]).map((alias) => (
                  <Tag key={alias.id || alias.polemicaUserId} color={alias.primary ? 'blue' : 'default'}>
                    {alias.polemicaUserId}{alias.primary ? ' primary' : ''}
                  </Tag>
                ))}
              </Space>
            ),
          },
          {
            title: 'Tournaments',
            key: 'tournaments',
            width: 160,
            render: (_, row) => {
              const inSelected =
                targetTournamentId != null &&
                row.tournamentIds.includes(targetTournamentId)
              return (
                <Space>
                  <Typography.Text>{row.tournamentCount}</Typography.Text>
                  {inSelected ? <Tag color="green">In selected</Tag> : null}
                </Space>
              )
            },
          },
          {
            title: 'Card templates',
            dataIndex: 'cardTemplateCount',
            width: 130,
          },
          {
            title: 'Upload',
            key: 'upload',
            width: 110,
            render: (_, row) => (
              <Upload
                showUploadList={false}
                beforeUpload={(file) => {
                  uploadMut.mutate({ id: row.id, file })
                  return false
                }}
              >
                <Button
                  size="small"
                  loading={uploadMut.isPending && uploadMut.variables?.id === row.id}
                >
                  Upload
                </Button>
              </Upload>
            ),
          },
          {
            title: 'Actions',
            key: 'actions',
            width: 220,
            render: (_, row) => {
              const alreadyInTournament =
                targetTournamentId != null &&
                row.tournamentIds.includes(targetTournamentId)
              return (
                <Space>
                  <Button
                    size="small"
                    onClick={() => {
                      setEditing(row)
                      editForm.setFieldsValue({ nickname: row.nickname })
                    }}
                  >
                    Edit
                  </Button>
                  <Button
                    size="small"
                    onClick={() => {
                      setAliasing(row)
                      aliasForm.resetFields()
                    }}
                  >
                    Alias
                  </Button>
                  <Button
                    size="small"
                    onClick={() => {
                      setMerging(row)
                      setMergePreview(null)
                      mergeForm.resetFields()
                    }}
                  >
                    Merge into
                  </Button>
                  <Button
                    size="small"
                    disabled={targetTournamentId == null || alreadyInTournament}
                    loading={
                      addToTournamentMut.isPending &&
                      addToTournamentMut.variables?.playerId === row.id
                    }
                    onClick={() => {
                      if (targetTournamentId != null) {
                        addToTournamentMut.mutate({
                          tournamentId: targetTournamentId,
                          playerId: row.id,
                        })
                      }
                    }}
                  >
                    Add to tournament
                  </Button>
                </Space>
              )
            },
          },
        ]}
      />

      <Modal
        title="New player"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        footer={null}
        destroyOnClose
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={(values) => createMut.mutate(values)}
        >
          <Form.Item
            name="polemicaUserId"
            label="Polemica user id"
            rules={[{ required: true }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="nickname" label="Nickname" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={createMut.isPending}>
            Create
          </Button>
        </Form>
      </Modal>

      <Modal
        title={aliasing == null ? 'Add alias' : `Add alias to ${aliasing.nickname}`}
        open={aliasing != null}
        onCancel={() => setAliasing(null)}
        footer={null}
        destroyOnClose
      >
        <Form
          form={aliasForm}
          layout="vertical"
          onFinish={(values) => {
            if (aliasing) {
              aliasMut.mutate({ id: aliasing.id, values })
            }
          }}
        >
          <Form.Item
            name="polemicaUserId"
            label="Polemica user id"
            rules={[{ required: true }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="primary" valuePropName="checked">
            <Checkbox>Make primary alias</Checkbox>
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={aliasMut.isPending}>
            Add alias
          </Button>
        </Form>
      </Modal>

      <Modal
        title={merging == null ? 'Merge player' : `Merge ${merging.nickname} into another player`}
        open={merging != null}
        onCancel={() => {
          setMerging(null)
          setMergePreview(null)
        }}
        footer={null}
        destroyOnClose
        width={760}
      >
        <Form
          form={mergeForm}
          layout="vertical"
          onValuesChange={() => setMergePreview(null)}
          onFinish={(values) => {
            if (!merging) return
            if (mergePreview?.canMerge) {
              mergeMut.mutate({
                targetId: values.targetFantasyPlayerId,
                sourceId: merging.id,
                reason: values.reason,
              })
            } else {
              previewMergeMut.mutate({
                sourceId: merging.id,
                targetId: values.targetFantasyPlayerId,
              })
            }
          }}
        >
          <Form.Item
            name="targetFantasyPlayerId"
            label="Target player"
            rules={[{ required: true }]}
          >
            <Select
              showSearch
              loading={mergeTargetsQ.isLoading}
              placeholder="Select target player"
              filterOption={(input, option) =>
                String(option?.searchText ?? '').includes(input.trim().toLowerCase())
              }
              filterSort={(a, b, info) => {
                const aRank = targetPlayerRank(a as TargetPlayerOption, info.searchValue)
                const bRank = targetPlayerRank(b as TargetPlayerOption, info.searchValue)
                if (aRank !== bRank) return aRank - bRank
                return (a.value as number) - (b.value as number)
              }}
              options={targetPlayerOptions}
            />
          </Form.Item>
          <Form.Item name="reason" label="Reason" rules={[{ required: true }]}>
            <Input.TextArea rows={3} maxLength={1024} showCount />
          </Form.Item>

          {mergePreview ? (
            <>
              <Divider />
              <Space direction="vertical" style={{ width: '100%' }}>
                <Typography.Text>
                  Source aliases: {mergePreview.sourceAliases.join(', ') || '—'}
                </Typography.Text>
                <Typography.Text>
                  Target aliases: {mergePreview.targetAliases.join(', ') || '—'}
                </Typography.Text>
                {mergePreview.blockers.map((issue) => (
                  <Alert
                    key={issue.code}
                    type="error"
                    showIcon
                    message={`${issue.code}: ${issue.count}`}
                    description={issue.message}
                  />
                ))}
                {mergePreview.warnings.map((issue) => (
                  <Alert
                    key={issue.code}
                    type="warning"
                    showIcon
                    message={`${issue.code}: ${issue.count}`}
                    description={issue.message}
                  />
                ))}
                {mergePreview.canMerge ? (
                  <Alert
                    type="success"
                    showIcon
                    message="Merge can be confirmed"
                    description="Direct references will move to target; source player row will be deleted after aliases transfer."
                  />
                ) : null}
              </Space>
            </>
          ) : null}

          <Space style={{ marginTop: 16 }}>
            <Button
              onClick={() => {
                const targetId = mergeForm.getFieldValue('targetFantasyPlayerId')
                if (merging && targetId) {
                  previewMergeMut.mutate({ sourceId: merging.id, targetId })
                }
              }}
              loading={previewMergeMut.isPending}
            >
              Preview
            </Button>
            <Button
              type="primary"
              htmlType="submit"
              disabled={!mergePreview?.canMerge}
              loading={mergeMut.isPending}
            >
              Confirm merge
            </Button>
          </Space>
        </Form>
      </Modal>

      <Modal
        title="Edit player"
        open={editing != null}
        onCancel={() => setEditing(null)}
        footer={null}
        destroyOnClose
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={(values) => {
            if (editing) {
              editMut.mutate({ id: editing.id, nickname: values.nickname })
            }
          }}
        >
          <Form.Item name="nickname" label="Nickname" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={editMut.isPending}>
            Save
          </Button>
        </Form>
      </Modal>
    </div>
  )
}
