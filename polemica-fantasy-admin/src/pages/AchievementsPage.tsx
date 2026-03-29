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
import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { listAchievements, updateAchievement } from '../api/achievements'
import type { AchievementAdminDto, OccurrenceType } from '../api/types'

const ROLE_OPTIONS = ['DON', 'MAFIA', 'PEACE', 'SHERIFF'].map((r) => ({
  value: r,
  label: r,
}))

const OCCURRENCE_OPTIONS: { value: OccurrenceType; label: string }[] = [
  { value: 'ONCE_PER_GAME', label: 'ONCE_PER_GAME' },
  { value: 'MULTIPLE_PER_GAME', label: 'MULTIPLE_PER_GAME' },
]

export function AchievementsPage() {
  const { message } = App.useApp()
  const [editRow, setEditRow] = useState<AchievementAdminDto | null>(null)
  const [form] = Form.useForm<{
    name: string
    description: string | null
    bonusPoints: number
    occurrenceType: OccurrenceType
    applicableRoles: string[]
    canAppearOnRandomCards: boolean
  }>()

  const q = useQuery({
    queryKey: ['admin', 'achievements'],
    queryFn: listAchievements,
  })

  const mut = useMutation({
    mutationFn: ({
      id,
      body,
    }: {
      id: string
      body: Parameters<typeof updateAchievement>[1]
    }) => updateAchievement(id, body),
    onSuccess: () => {
      message.success('Achievement updated')
      setEditRow(null)
      void q.refetch()
    },
    onError: (e: Error) => message.error(e.message),
  })

  return (
    <div>
      <Typography.Title level={3}>Achievements</Typography.Title>
      <Typography.Paragraph type="secondary">
        System catalog: bonus, repeatability, roles for scoring, random-card pool flag.
      </Typography.Paragraph>

      <Table<AchievementAdminDto>
        rowKey="id"
        loading={q.isLoading}
        dataSource={q.data}
        scroll={{ x: true }}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 200, ellipsis: true },
          { title: 'Name', dataIndex: 'name', width: 200 },
          {
            title: 'Bonus',
            dataIndex: 'bonusPoints',
            width: 90,
          },
          {
            title: 'Occurrence',
            dataIndex: 'occurrenceType',
            width: 160,
            render: (t: OccurrenceType) => <Tag>{t}</Tag>,
          },
          {
            title: 'Roles',
            dataIndex: 'applicableRoles',
            render: (roles: string[]) =>
              roles?.length ? roles.map((r) => <Tag key={r}>{r}</Tag>) : '—',
          },
          {
            title: 'Random cards',
            dataIndex: 'canAppearOnRandomCards',
            width: 120,
            render: (v: boolean) => (v ? <Tag color="green">yes</Tag> : <Tag>no</Tag>),
          },
          {
            title: '',
            key: 'a',
            width: 90,
            render: (_, row) => (
              <Button
                type="link"
                size="small"
                onClick={() => {
                  setEditRow(row)
                  form.setFieldsValue({
                    name: row.name,
                    description: row.description,
                    bonusPoints: row.bonusPoints,
                    occurrenceType: row.occurrenceType,
                    applicableRoles: row.applicableRoles,
                    canAppearOnRandomCards: row.canAppearOnRandomCards,
                  })
                }}
              >
                Edit
              </Button>
            ),
          },
        ]}
      />

      <Modal
        title={editRow ? `Edit: ${editRow.id}` : 'Edit'}
        open={editRow != null}
        onCancel={() => setEditRow(null)}
        footer={null}
        destroyOnClose
        width={560}
      >
        {editRow && (
          <Form
            form={form}
            layout="vertical"
            onFinish={(v) =>
              mut.mutate({
                id: editRow.id,
                body: {
                  name: v.name,
                  description: v.description,
                  bonusPoints: v.bonusPoints,
                  occurrenceType: v.occurrenceType,
                  applicableRoles: v.applicableRoles,
                  canAppearOnRandomCards: v.canAppearOnRandomCards,
                },
              })
            }
          >
            <Form.Item name="name" label="Name" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="description" label="Description">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="bonusPoints" label="Bonus points" rules={[{ required: true }]}>
              <InputNumber min={0} step={0.1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="occurrenceType"
              label="Occurrence type"
              rules={[{ required: true }]}
            >
              <Select options={OCCURRENCE_OPTIONS} />
            </Form.Item>
            <Form.Item name="applicableRoles" label="Applicable roles (scoring)">
              <Select mode="multiple" allowClear options={ROLE_OPTIONS} placeholder="Roles" />
            </Form.Item>
            <Form.Item
              name="canAppearOnRandomCards"
              label="Can appear on random auto-generated cards"
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" loading={mut.isPending}>
                  Save
                </Button>
                <Button onClick={() => setEditRow(null)}>Cancel</Button>
              </Space>
            </Form.Item>
          </Form>
        )}
      </Modal>
    </div>
  )
}
