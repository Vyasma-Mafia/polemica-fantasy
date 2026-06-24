import {
  App,
  Avatar,
  Button,
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
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createFantasyPlayer,
  listFantasyPlayers,
  updateFantasyPlayer,
  uploadFantasyPlayerPhoto,
} from '../api/fantasyPlayers'
import { listTournaments, addTournamentPlayer } from '../api/tournaments'
import type { FantasyPlayerAdminDto } from '../api/types'

interface CreatePlayerFormValues {
  polemicaUserId: number
  nickname: string
}

interface EditPlayerFormValues {
  nickname: string
}

export function PlayersPage() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [query, setQuery] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [editing, setEditing] = useState<FantasyPlayerAdminDto | null>(null)
  const [targetTournamentId, setTargetTournamentId] = useState<number | undefined>()
  const [createForm] = Form.useForm<CreatePlayerFormValues>()
  const [editForm] = Form.useForm<EditPlayerFormValues>()

  const playersQ = useQuery({
    queryKey: ['admin', 'fantasy-players', query],
    queryFn: () => listFantasyPlayers({ query }),
  })

  const tournamentsQ = useQuery({
    queryKey: ['admin', 'tournaments'],
    queryFn: listTournaments,
  })

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
