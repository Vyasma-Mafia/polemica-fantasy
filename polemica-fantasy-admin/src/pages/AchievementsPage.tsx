import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { useMemo, useState } from 'react'
import {
  dryRunAchievementBackfill,
  listAchievements,
  updateAchievement,
} from '../api/achievements'
import type {
  AchievementAdminDefinitionDto,
  AchievementAdminRewardDto,
  AchievementDryRunRowDto,
  AchievementRewardType,
  AchievementVisibility,
  Rarity,
  UpdateAchievementAdminRequest,
} from '../api/types'

const RARITY_OPTIONS = ['COMMON', 'RARE', 'EPIC', 'LEGENDARY'].map((value) => ({ value, label: value }))
const VISIBILITY_OPTIONS = ['PUBLIC', 'HIDDEN', 'SECRET', 'PRIVATE'].map((value) => ({ value, label: value }))
const REWARD_TYPE_OPTIONS = ['FANTIKI', 'PROFILE_FRAME', 'COSMETIC_UNLOCK', 'BADGE_STYLE', 'RANDOM_CARD', 'CARD_CHOICE_ROLL'].map((value) => ({ value, label: value }))
const CARD_REWARD_TYPES = new Set(['RANDOM_CARD', 'CARD_CHOICE_ROLL'])

interface AchievementFormValues {
  title: string
  description?: string | null
  iconUrl?: string | null
  accentColor?: string | null
  rarity: Rarity
  visibility: AchievementVisibility
  enabled: boolean
  displayOrder: number
  rewards: {
    type: AchievementRewardType | string
    amount?: number | null
    code?: string | null
    metadata?: string | null
    displayOrder?: number | null
  }[]
}

function nullableText(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed.length ? trimmed : null
}

function normalizeReward(row: AchievementFormValues['rewards'][number]) {
  const type = row.type
  return {
    type,
    amount: type === 'FANTIKI' ? (row.amount ?? null) : null,
    code: type === 'FANTIKI' ? null : nullableText(row.code),
    metadata: nullableText(row.metadata),
    displayOrder: row.displayOrder ?? 0,
  }
}

function rewardSummary(rewards: AchievementAdminRewardDto[]) {
  if (!rewards.length) return 'none'
  return rewards
    .map((reward) => {
      if (reward.type === 'FANTIKI') return `${reward.amount ?? 0}₣`
      if (CARD_REWARD_TYPES.has(reward.type)) return `${reward.type}:${reward.metadata ?? '—'}`
      return `${reward.type}:${reward.code ?? '—'}`
    })
    .join(', ')
}

function formatDate(value: string | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

export function AchievementsPage() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [editRow, setEditRow] = useState<AchievementAdminDefinitionDto | null>(null)
  const [form] = Form.useForm<AchievementFormValues>()

  const achievementsQ = useQuery({
    queryKey: ['admin', 'achievements'],
    queryFn: listAchievements,
  })

  const updateM = useMutation({
    mutationFn: ({ code, body }: { code: string; body: UpdateAchievementAdminRequest }) =>
      updateAchievement(code, body),
    onSuccess: async () => {
      message.success('Achievement updated')
      setEditRow(null)
      await qc.invalidateQueries({ queryKey: ['admin', 'achievements'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const dryRunM = useMutation({
    mutationFn: dryRunAchievementBackfill,
    onError: (e: Error) => message.error(e.message),
  })

  const rows = achievementsQ.data?.achievements ?? []
  const categoryFilters = useMemo(
    () => [...new Set(rows.map((row) => row.category))]
      .sort()
      .map((value) => ({ text: value, value })),
    [rows],
  )

  const openEdit = (row: AchievementAdminDefinitionDto) => {
    setEditRow(row)
    form.setFieldsValue({
      title: row.title,
      description: row.description,
      iconUrl: row.iconUrl,
      accentColor: row.accentColor,
      rarity: row.rarity,
      visibility: row.visibility as AchievementVisibility,
      enabled: row.enabled,
      displayOrder: row.displayOrder,
      rewards: row.rewards.map((reward) => ({
        type: reward.type,
        amount: reward.amount,
        code: reward.code,
        metadata: reward.metadata,
        displayOrder: reward.displayOrder,
      })),
    })
  }

  const submit = (values: AchievementFormValues) => {
    if (!editRow) return
    updateM.mutate({
      code: editRow.code,
      body: {
        title: values.title,
        description: nullableText(values.description),
        iconUrl: nullableText(values.iconUrl),
        accentColor: nullableText(values.accentColor),
        rarity: values.rarity,
        visibility: values.visibility,
        enabled: values.enabled,
        displayOrder: values.displayOrder,
        rewards: (values.rewards ?? []).map(normalizeReward),
      },
    })
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Typography.Title level={3}>Achievements</Typography.Title>
        <Typography.Paragraph type="secondary">
          Admin catalog management, reward replacement, persisted stats, and launch dry-run.
        </Typography.Paragraph>
      </div>

      <DryRunCard
        rows={dryRunM.data?.rows ?? []}
        instantCompleted={dryRunM.data?.instantCompleted ?? null}
        instantFantikiLiability={dryRunM.data?.instantFantikiLiability ?? null}
        loading={dryRunM.isPending}
        onRun={() => dryRunM.mutate()}
      />

      <Table<AchievementAdminDefinitionDto>
        rowKey="code"
        loading={achievementsQ.isLoading}
        dataSource={rows}
        scroll={{ x: true }}
        columns={[
          { title: 'Code', dataIndex: 'code', width: 190, fixed: 'left' },
          {
            title: 'Category',
            dataIndex: 'category',
            width: 140,
            filters: categoryFilters,
            onFilter: (value, row) => row.category === value,
            render: (value: string) => <Tag>{value}</Tag>,
          },
          { title: 'Title', dataIndex: 'title', width: 220, ellipsis: true },
          {
            title: 'Enabled',
            dataIndex: 'enabled',
            width: 110,
            filters: [
              { text: 'Enabled', value: true },
              { text: 'Disabled', value: false },
            ],
            onFilter: (value, row) => row.enabled === value,
            render: (value: boolean) => value ? <Tag color="green">enabled</Tag> : <Tag>disabled</Tag>,
          },
          {
            title: 'Visibility',
            dataIndex: 'visibility',
            width: 120,
            filters: VISIBILITY_OPTIONS.map((o) => ({ text: o.label, value: o.value })),
            onFilter: (value, row) => row.visibility === value,
            render: (value: string) => <Tag>{value}</Tag>,
          },
          {
            title: 'Rarity',
            dataIndex: 'rarity',
            width: 120,
            filters: RARITY_OPTIONS.map((o) => ({ text: o.label, value: o.value })),
            onFilter: (value, row) => row.rarity === value,
            render: (value: string) => <Tag color={value === 'LEGENDARY' ? 'gold' : value === 'EPIC' ? 'purple' : value === 'RARE' ? 'blue' : undefined}>{value}</Tag>,
          },
          {
            title: 'Rewards',
            dataIndex: 'rewards',
            width: 260,
            ellipsis: true,
            render: (rewards: AchievementAdminRewardDto[]) => rewardSummary(rewards),
          },
          { title: 'Completed', dataIndex: ['stats', 'completedUsers'], width: 110 },
          { title: 'Claimed', dataIndex: ['stats', 'claimedUsers'], width: 100 },
          { title: 'Unclaimed', dataIndex: ['stats', 'unclaimedUsers'], width: 110 },
          { title: 'Near', dataIndex: ['stats', 'nearCompletionUsers'], width: 90 },
          {
            title: 'Last completed',
            dataIndex: ['stats', 'lastCompletedAt'],
            width: 160,
            render: (value: string | null) => formatDate(value),
          },
          {
            title: '',
            key: 'actions',
            fixed: 'right',
            width: 90,
            render: (_, row) => (
              <Button type="link" size="small" onClick={() => openEdit(row)}>
                Edit
              </Button>
            ),
          },
        ]}
      />

      <Drawer
        title={editRow ? `Edit: ${editRow.code}` : 'Edit achievement'}
        open={editRow != null}
        onClose={() => setEditRow(null)}
        width={760}
        destroyOnClose
      >
        {editRow && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label="Code">{editRow.code}</Descriptions.Item>
              <Descriptions.Item label="Condition">{editRow.conditionType}</Descriptions.Item>
              <Descriptions.Item label="History">{editRow.historyPolicy}</Descriptions.Item>
              <Descriptions.Item label="Target">{editRow.targetValue}</Descriptions.Item>
              <Descriptions.Item label="Chain group">{editRow.chainGroup ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Chain level">{editRow.chainLevel ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Tracking started">{formatDate(editRow.trackingStartedAt)}</Descriptions.Item>
            </Descriptions>

            <Form form={form} layout="vertical" onFinish={submit}>
              <Form.Item name="title" label="Title" rules={[{ required: true }, { max: 255 }]}>
                <Input />
              </Form.Item>
              <Form.Item name="description" label="Description" rules={[{ max: 4096 }]}>
                <Input.TextArea rows={3} showCount maxLength={4096} />
              </Form.Item>
              <Form.Item name="iconUrl" label="Icon URL" rules={[{ max: 2048 }]}>
                <Input />
              </Form.Item>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item
                    name="accentColor"
                    label="Accent color"
                    rules={[{ pattern: /^#[0-9A-Fa-f]{6}$/, message: 'Use #RRGGBB' }]}
                  >
                    <Input placeholder="#12ABef" />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="displayOrder" label="Display order" rules={[{ required: true }]}>
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={12}>
                <Col span={8}>
                  <Form.Item name="rarity" label="Rarity" rules={[{ required: true }]}>
                    <Select options={RARITY_OPTIONS} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="visibility" label="Visibility" rules={[{ required: true }]}>
                    <Select options={VISIBILITY_OPTIONS} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="enabled" label="Enabled" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
              </Row>

              <Typography.Title level={5}>Rewards</Typography.Title>
              <Form.List name="rewards">
                {(fields, { add, remove }) => (
                  <Space direction="vertical" style={{ width: '100%' }}>
                    {fields.map((field) => (
                      <Card
                        key={field.key}
                        size="small"
                        title={`Reward ${field.name + 1}`}
                        extra={<Button size="small" danger onClick={() => remove(field.name)}>Remove</Button>}
                      >
                        <Row gutter={12}>
                          <Col span={10}>
                            <Form.Item
                              {...field}
                              name={[field.name, 'type']}
                              label="Type"
                              rules={[{ required: true }]}
                            >
                              <Select options={REWARD_TYPE_OPTIONS} />
                            </Form.Item>
                          </Col>
                          <Col span={7}>
                            <Form.Item
                              {...field}
                              name={[field.name, 'displayOrder']}
                              label="Order"
                              rules={[{ required: true }]}
                            >
                              <InputNumber min={0} style={{ width: '100%' }} />
                            </Form.Item>
                          </Col>
                          <Col span={7}>
                            <Form.Item noStyle shouldUpdate>
                              {({ getFieldValue }) => {
                                const type = getFieldValue(['rewards', field.name, 'type'])
                                return (
                                  <Form.Item
                                    name={[field.name, 'amount']}
                                    label="Amount"
                                    rules={type === 'FANTIKI' ? [{ required: true }, { type: 'number', min: 1 }] : undefined}
                                  >
                                    <InputNumber min={1} disabled={type !== 'FANTIKI'} style={{ width: '100%' }} />
                                  </Form.Item>
                                )
                              }}
                            </Form.Item>
                          </Col>
                        </Row>
                        <Form.Item noStyle shouldUpdate>
                          {({ getFieldValue }) => {
                            const type = getFieldValue(['rewards', field.name, 'type'])
                            const requiresCode = type !== 'FANTIKI' && !CARD_REWARD_TYPES.has(type)
                            return (
                              <Form.Item
                                name={[field.name, 'code']}
                                label="Reward code"
                                rules={requiresCode ? [{ required: true }, { max: 96 }] : [{ max: 96 }]}
                              >
                                <Input disabled={type === 'FANTIKI'} />
                              </Form.Item>
                            )
                          }}
                        </Form.Item>
                        <Form.Item noStyle shouldUpdate>
                          {({ getFieldValue }) => {
                            const type = getFieldValue(['rewards', field.name, 'type'])
                            return (
                              <Form.Item
                                name={[field.name, 'metadata']}
                                label="Metadata JSON"
                                rules={CARD_REWARD_TYPES.has(type) ? [{ required: true }] : undefined}
                              >
                                <Input.TextArea rows={2} placeholder='{"rarity":"RARE","count":2,"options":5,"source":"ACTIVE_PACKS"}' />
                              </Form.Item>
                            )
                          }}
                        </Form.Item>
                      </Card>
                    ))}
                    <Button
                      onClick={() => add({ type: 'FANTIKI', amount: 10, code: null, metadata: null, displayOrder: (fields.length + 1) * 10 })}
                      disabled={fields.length >= 10}
                    >
                      Add reward
                    </Button>
                  </Space>
                )}
              </Form.List>

              <Form.Item style={{ marginTop: 24 }}>
                <Space>
                  <Button type="primary" htmlType="submit" loading={updateM.isPending}>
                    Save
                  </Button>
                  <Button onClick={() => setEditRow(null)}>Cancel</Button>
                </Space>
              </Form.Item>
            </Form>
          </Space>
        )}
      </Drawer>
    </Space>
  )
}

function DryRunCard({
  rows,
  instantCompleted,
  instantFantikiLiability,
  loading,
  onRun,
}: {
  rows: AchievementDryRunRowDto[]
  instantCompleted: number | null
  instantFantikiLiability: number | null
  loading: boolean
  onRun: () => void
}) {
  const hasResult = instantCompleted != null && instantFantikiLiability != null
  const isZero = instantCompleted === 0 && instantFantikiLiability === 0
  return (
    <Card
      title="Backfill dry-run"
      extra={<Button loading={loading} onClick={onRun}>Run dry-run</Button>}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Row gutter={16}>
          <Col span={8}>
            <Statistic title="Instant completed" value={instantCompleted ?? 0} />
          </Col>
          <Col span={8}>
            <Statistic title="Instant liability" value={instantFantikiLiability ?? 0} suffix="₣" />
          </Col>
        </Row>
        {hasResult && (
          <Alert
            type={isZero ? 'success' : 'warning'}
            showIcon
            message={isZero ? 'Launch baseline is zero' : 'Dry-run found instant payout exposure'}
          />
        )}
        <Table<AchievementDryRunRowDto>
          rowKey="code"
          size="small"
          pagination={{ pageSize: 5 }}
          dataSource={rows}
          columns={[
            { title: 'Code', dataIndex: 'code' },
            { title: 'Enabled', dataIndex: 'enabled', render: (value: boolean) => value ? <Tag color="green">yes</Tag> : <Tag>no</Tag> },
            { title: 'Completed', dataIndex: 'instantCompleted' },
            { title: 'Liability', dataIndex: 'instantFantikiLiability', render: (value: number) => `${value}₣` },
          ]}
        />
      </Space>
    </Card>
  )
}
