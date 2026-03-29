import {
  App,
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
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  addCardTemplateAchievement,
  createCardTemplate,
  listCardTemplates,
  updateCardTemplate,
  uploadCardImage,
} from '../api/cards'
import { listAchievements } from '../api/achievements'
import { listTournaments } from '../api/tournaments'
import type { Rarity } from '../api/types'

export function CardTemplatesPage() {
  const { message } = App.useApp()
  const [tournamentId, setTournamentId] = useState<number | undefined>()
  const [playerId, setPlayerId] = useState<number | undefined>()
  const [rarity, setRarity] = useState<Rarity | undefined>()

  const [createOpen, setCreateOpen] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [achOpen, setAchOpen] = useState<number | null>(null)

  const tq = useQuery({
    queryKey: ['admin', 'tournaments'],
    queryFn: listTournaments,
  })

  const achievementsQ = useQuery({
    queryKey: ['admin', 'achievements'],
    queryFn: listAchievements,
  })

  const cq = useQuery({
    queryKey: ['admin', 'card-templates', tournamentId, playerId, rarity],
    queryFn: () =>
      listCardTemplates({
        tournamentId,
        fantasyPlayerId: playerId,
        rarity,
      }),
  })

  const createMut = useMutation({
    mutationFn: createCardTemplate,
    onSuccess: () => {
      message.success('Template created')
      setCreateOpen(false)
      void cq.refetch()
    },
    onError: (e: Error) => message.error(e.message),
  })

  const updateMut = useMutation({
    mutationFn: ({
      id,
      ...body
    }: {
      id: number
      rarity?: Rarity | null
      description?: string | null
    }) => updateCardTemplate(id, body),
    onSuccess: () => {
      message.success('Template updated')
      setEditId(null)
      void cq.refetch()
    },
    onError: (e: Error) => message.error(e.message),
  })

  const achMut = useMutation({
    mutationFn: ({ id, achievementId }: { id: number; achievementId: string }) =>
      addCardTemplateAchievement(id, { achievementId }),
    onSuccess: () => {
      message.success('Achievement added')
      setAchOpen(null)
      void cq.refetch()
    },
    onError: (e: Error) => message.error(e.message),
  })

  const imgMut = useMutation({
    mutationFn: ({ id, file }: { id: number; file: File }) =>
      uploadCardImage(id, file),
    onSuccess: () => {
      message.success('Image uploaded')
      void cq.refetch()
    },
    onError: (e: Error) => message.error(e.message),
  })

  const editing = cq.data?.find((c) => c.id === editId)
  const achRow = cq.data?.find((c) => c.id === achOpen)

  const achievementOptions =
    achievementsQ.data?.map((a) => ({
      value: a.id,
      label: `${a.name} (${a.id})`,
    })) ?? []

  return (
    <div>
      <Typography.Title level={3}>Card templates</Typography.Title>
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Tournament id filter"
          style={{ width: 200 }}
          options={tq.data?.map((t) => ({
            value: t.id,
            label: `${t.id}: ${t.name}`,
          }))}
          value={tournamentId}
          onChange={(v) => setTournamentId(v)}
        />
        <InputNumber
          placeholder="Fantasy player id"
          min={1}
          value={playerId}
          onChange={(v) => setPlayerId(v ?? undefined)}
        />
        <Select
          allowClear
          placeholder="Rarity"
          style={{ width: 140 }}
          options={['COMMON', 'RARE', 'EPIC', 'LEGENDARY'].map((r) => ({
            value: r as Rarity,
            label: r,
          }))}
          value={rarity}
          onChange={setRarity}
        />
        <Button type="primary" onClick={() => setCreateOpen(true)}>
          New template
        </Button>
      </Space>

      <Table
        rowKey="id"
        loading={cq.isLoading}
        dataSource={cq.data}
        scroll={{ x: true }}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 70 },
          { title: 'Fantasy player', dataIndex: 'fantasyPlayerId' },
          { title: 'Rarity', dataIndex: 'rarity', render: (r: string) => <Tag>{r}</Tag> },
          {
            title: 'Image',
            dataIndex: 'imageUrl',
            render: (url: string | null) =>
              url ? (
                <a href={url} target="_blank" rel="noreferrer">
                  link
                </a>
              ) : (
                '—'
              ),
          },
          { title: 'Description', dataIndex: 'description', ellipsis: true },
          {
            title: 'Achievements',
            dataIndex: 'achievements',
            render: (a: { achievementName: string }[]) =>
              a?.length ? (
                <span>{a.map((x) => x.achievementName).join(', ')}</span>
              ) : (
                '—'
              ),
          },
          {
            title: 'Actions',
            key: 'a',
            render: (_, row) => (
              <Space>
                <Button type="link" size="small" onClick={() => setEditId(row.id)}>
                  Edit
                </Button>
                <Upload
                  showUploadList={false}
                  beforeUpload={(file) => {
                    imgMut.mutate({ id: row.id, file })
                    return false
                  }}
                >
                  <Button size="small" loading={imgMut.isPending}>
                    Image
                  </Button>
                </Upload>
                <Button
                  type="link"
                  size="small"
                  onClick={() => setAchOpen(row.id)}
                >
                  +Achievement
                </Button>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title="Create card template"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        footer={null}
        destroyOnClose
      >
        <Form
          layout="vertical"
          onFinish={(v: {
            fantasyPlayerId: number
            rarity: Rarity
            description?: string
          }) => createMut.mutate(v)}
        >
          <Form.Item
            name="fantasyPlayerId"
            label="Fantasy player id"
            rules={[{ required: true }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="rarity" label="Rarity" rules={[{ required: true }]}>
            <Select
              options={['COMMON', 'RARE', 'EPIC', 'LEGENDARY'].map((r) => ({
                value: r as Rarity,
                label: r,
              }))}
            />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={createMut.isPending}>
              Create
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Edit template"
        open={editId != null}
        onCancel={() => setEditId(null)}
        footer={null}
        destroyOnClose
      >
        {editing && (
          <Form
            layout="vertical"
            initialValues={{
              rarity: editing.rarity,
              description: editing.description ?? '',
            }}
            onFinish={(v: { rarity: Rarity; description?: string }) =>
              updateMut.mutate({
                id: editing.id,
                rarity: v.rarity,
                description: v.description,
              })
            }
          >
            <Form.Item name="rarity" label="Rarity" rules={[{ required: true }]}>
              <Select
                options={['COMMON', 'RARE', 'EPIC', 'LEGENDARY'].map((r) => ({
                  value: r as Rarity,
                  label: r,
                }))}
              />
            </Form.Item>
            <Form.Item name="description" label="Description">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" loading={updateMut.isPending}>
                Save
              </Button>
            </Form.Item>
          </Form>
        )}
      </Modal>

      <Modal
        title="Add achievement"
        open={achOpen != null}
        onCancel={() => setAchOpen(null)}
        footer={null}
        destroyOnClose
      >
        {achRow && (
          <Form
            layout="vertical"
            onFinish={(v: { achievementId: string }) =>
              achMut.mutate({ id: achRow.id, achievementId: v.achievementId })
            }
          >
            <Form.Item
              name="achievementId"
              label="Achievement"
              rules={[{ required: true }]}
            >
              <Select
                showSearch
                loading={achievementsQ.isLoading}
                options={achievementOptions}
                placeholder="From catalog (bonus from catalog)"
              />
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" loading={achMut.isPending}>
                Add
              </Button>
            </Form.Item>
          </Form>
        )}
      </Modal>
    </div>
  )
}
