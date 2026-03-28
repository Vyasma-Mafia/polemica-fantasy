import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { createCardPack, listCardPacks, updateCardPack } from '../api/packs'
import { listTournaments } from '../api/tournaments'
import type { Rarity } from '../api/types'

export function CardPacksPage() {
  const { message } = App.useApp()
  const [tournamentFilter, setTournamentFilter] = useState<number | undefined>()
  const [createOpen, setCreateOpen] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)

  const tq = useQuery({
    queryKey: ['admin', 'tournaments'],
    queryFn: listTournaments,
  })

  const pq = useQuery({
    queryKey: ['admin', 'card-packs', tournamentFilter],
    queryFn: () => listCardPacks(tournamentFilter),
  })

  const createMut = useMutation({
    mutationFn: createCardPack,
    onSuccess: () => {
      message.success('Pack created')
      setCreateOpen(false)
      void pq.refetch()
    },
    onError: (e: Error) => message.error(e.message),
  })

  const updatePackMut = useMutation({
    mutationFn: (args: { id: number; body: Parameters<typeof updateCardPack>[1] }) =>
      updateCardPack(args.id, args.body),
    onSuccess: () => {
      message.success('Pack updated')
      setEditId(null)
      void pq.refetch()
    },
    onError: (e: Error) => message.error(e.message),
  })

  const editing = pq.data?.find((p) => p.id === editId)

  const rarityOptions = ['COMMON', 'RARE', 'EPIC', 'LEGENDARY'].map((r) => ({
    value: r as Rarity,
    label: r,
  }))

  return (
    <div>
      <Typography.Title level={3}>Card packs</Typography.Title>
      <Space style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Filter by tournament"
          style={{ width: 280 }}
          options={tq.data?.map((t) => ({
            value: t.id,
            label: `${t.id}: ${t.name}`,
          }))}
          value={tournamentFilter}
          onChange={(v) => setTournamentFilter(v)}
        />
        <Button type="primary" onClick={() => setCreateOpen(true)}>
          New pack
        </Button>
      </Space>

      <Table
        rowKey="id"
        loading={pq.isLoading}
        dataSource={pq.data}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 70 },
          { title: 'Name', dataIndex: 'name' },
          { title: 'Tournament', dataIndex: 'tournamentId' },
          {
            title: 'Active',
            dataIndex: 'active',
            render: (a: boolean) => (a ? <Tag color="green">yes</Tag> : <Tag>no</Tag>),
          },
          {
            title: 'Configs',
            dataIndex: 'rarityConfigs',
            render: (c: { rarity: string; probability: number }[]) =>
              c?.map((x) => `${x.rarity}:${x.probability}`).join(', ') ?? '',
          },
          {
            title: '',
            key: 'e',
            render: (_, row) => (
              <Button type="link" onClick={() => setEditId(row.id)}>
                Edit
              </Button>
            ),
          },
        ]}
      />

      <Modal
        title="Create pack"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        footer={null}
        destroyOnClose
        width={640}
      >
        <Form
          layout="vertical"
          initialValues={{ active: true }}
          onFinish={(v: {
            name: string
            tournamentId: number
            active: boolean
            rarityConfigs: { rarity: Rarity; probability: number; cardsCount: number }[]
          }) => createMut.mutate(v)}
        >
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="tournamentId"
            label="Tournament"
            rules={[{ required: true }]}
          >
            <Select
              options={tq.data?.map((t) => ({
                value: t.id,
                label: `${t.id}: ${t.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Typography.Text strong>Rarity configs (probabilities must sum to 1)</Typography.Text>
          <Form.List
            name="rarityConfigs"
            rules={[
              {
                validator: async (_, names) => {
                  if (!names || names.length < 1) {
                    return Promise.reject(new Error('Add at least one config'))
                  }
                },
              },
            ]}
          >
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...rest }) => (
                  <Space key={key} align="baseline" wrap>
                    <Form.Item
                      {...rest}
                      name={[name, 'rarity']}
                      rules={[{ required: true }]}
                    >
                      <Select options={rarityOptions} placeholder="Rarity" />
                    </Form.Item>
                    <Form.Item
                      {...rest}
                      name={[name, 'probability']}
                      rules={[{ required: true }]}
                    >
                      <InputNumber min={0} max={1} step={0.01} placeholder="p" />
                    </Form.Item>
                    <Form.Item
                      {...rest}
                      name={[name, 'cardsCount']}
                      rules={[{ required: true }]}
                    >
                      <InputNumber min={1} placeholder="count" />
                    </Form.Item>
                    <MinusCircleOutlined onClick={() => remove(name)} />
                  </Space>
                ))}
                <Form.Item>
                  <Button
                    type="dashed"
                    onClick={() => add()}
                    icon={<PlusOutlined />}
                  >
                    Add rarity row
                  </Button>
                </Form.Item>
              </>
            )}
          </Form.List>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={createMut.isPending}>
              Create
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Edit pack"
        open={editId != null}
        onCancel={() => setEditId(null)}
        footer={null}
        destroyOnClose
        width={640}
      >
        {editing && (
          <Form
            layout="vertical"
            initialValues={{
              name: editing.name,
              active: editing.active,
              rarityConfigs: editing.rarityConfigs.map((c) => ({
                rarity: c.rarity,
                probability: c.probability,
                cardsCount: c.cardsCount,
              })),
            }}
            onFinish={(v: {
              name: string
              active: boolean
              rarityConfigs: { rarity: Rarity; probability: number; cardsCount: number }[]
            }) =>
              updatePackMut.mutate({
                id: editing.id,
                body: {
                  name: v.name,
                  active: v.active,
                  rarityConfigs: v.rarityConfigs,
                },
              })
            }
          >
            <Form.Item name="name" label="Name" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="active" label="Active" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.List name="rarityConfigs">
              {(fields, { add, remove }) => (
                <>
                  {fields.map(({ key, name, ...rest }) => (
                    <Space key={key} align="baseline" wrap>
                      <Form.Item
                        {...rest}
                        name={[name, 'rarity']}
                        rules={[{ required: true }]}
                      >
                        <Select options={rarityOptions} />
                      </Form.Item>
                      <Form.Item
                        {...rest}
                        name={[name, 'probability']}
                        rules={[{ required: true }]}
                      >
                        <InputNumber min={0} max={1} step={0.01} />
                      </Form.Item>
                      <Form.Item
                        {...rest}
                        name={[name, 'cardsCount']}
                        rules={[{ required: true }]}
                      >
                        <InputNumber min={1} />
                      </Form.Item>
                      <MinusCircleOutlined onClick={() => remove(name)} />
                    </Space>
                  ))}
                  <Form.Item>
                    <Button type="dashed" onClick={() => add()} icon={<PlusOutlined />}>
                      Add rarity row
                    </Button>
                  </Form.Item>
                </>
              )}
            </Form.List>
            <Form.Item>
              <Button type="primary" htmlType="submit" loading={updatePackMut.isPending}>
                Save
              </Button>
            </Form.Item>
          </Form>
        )}
      </Modal>
    </div>
  )
}
