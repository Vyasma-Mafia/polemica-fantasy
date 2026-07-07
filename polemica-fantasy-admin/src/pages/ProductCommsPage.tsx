import { App, Button, Card, Form, Input, Modal, Select, Space, Statistic, Switch, Table, Tabs, Tag, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import {
  createReleaseNote,
  dryRunCampaign,
  fetchCampaignAnalytics,
  fetchProductAnalyticsSummary,
  fetchReleaseNoteAnalytics,
  listCampaigns,
  listReleaseNotes,
  previewCampaign,
  publishReleaseNote,
  sendCampaign,
  sendExistingCampaign,
} from '../api/notifications'
import type {
  ProductCampaignAnalyticsDto,
  ProductCampaignDto,
  ReleaseNoteAdminDto,
  ReleaseNoteAnalyticsDto,
} from '../api/types'

const AUDIENCES = [
  { value: 'ALL', label: 'All users' },
  { value: 'NEVER_ACTIVATED', label: 'Never activated' },
  { value: 'ACTION_NO_TEAM', label: 'Action, no team' },
  { value: 'AT_RISK', label: 'At risk' },
  { value: 'ACTIVE_CORE', label: 'Active core' },
]

interface CampaignFormValues {
  title: string
  audience: string
  text: string
  buttonText?: string
  buttonUrl?: string
}

interface ReleaseNoteFormValues {
  title: string
  body: string
  buttonText?: string
  buttonUrl?: string
  audience: string
  minAppVersion?: string
  active: boolean
}

export function ProductCommsPage() {
  return (
    <div>
      <Typography.Title level={3}>Product communication</Typography.Title>
      <Typography.Paragraph type="secondary">
        Use campaigns and release notes for product education. Keep Broadcast for urgent manual messages only.
      </Typography.Paragraph>
      <Tabs
        items={[
          { key: 'campaigns', label: 'Campaigns', children: <CampaignsTab /> },
          { key: 'release-notes', label: 'Release notes', children: <ReleaseNotesTab /> },
          { key: 'analytics', label: 'Analytics', children: <AnalyticsTab /> },
        ]}
      />
    </div>
  )
}

function CampaignsTab() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [form] = Form.useForm<CampaignFormValues>()
  const campaignsQ = useQuery({ queryKey: ['product-campaigns'], queryFn: listCampaigns })
  const dryRunM = useMutation({ mutationFn: (audience: string) => dryRunCampaign(audience) })
  const previewM = useMutation({ mutationFn: (values: CampaignFormValues) => previewCampaign(values) })
  const sendM = useMutation({
    mutationFn: (values: CampaignFormValues) => sendCampaign(values),
    onSuccess: () => {
      message.success('Campaign queued')
      form.resetFields()
      void qc.invalidateQueries({ queryKey: ['product-campaigns'] })
      void qc.invalidateQueries({ queryKey: ['product-campaign-analytics'] })
    },
    onError: (e: Error) => message.error(e.message),
  })
  const sendExistingM = useMutation({
    mutationFn: (id: number) => sendExistingCampaign(id),
    onSuccess: () => {
      message.success('Campaign queued')
      void qc.invalidateQueries({ queryKey: ['product-campaigns'] })
      void qc.invalidateQueries({ queryKey: ['product-campaign-analytics'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const runPreview = async () => {
    const values = await form.validateFields()
    await dryRunM.mutateAsync(values.audience)
    await previewM.mutateAsync(values)
  }

  const submit = async () => {
    const values = await form.validateFields()
    const preview = await previewM.mutateAsync(values)
    Modal.confirm({
      title: 'Send campaign?',
      content: `Eligible recipients: ${preview.eligibleCount.toLocaleString('ru-RU')} of ${preview.rawCount.toLocaleString('ru-RU')}.`,
      okText: 'Send',
      onOk: () => sendM.mutateAsync(values),
    })
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card title="New campaign">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ audience: 'ACTION_NO_TEAM' }}
          style={{ maxWidth: 720 }}
        >
          <Form.Item name="title" label="Internal title" rules={[{ required: true }, { max: 255 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="audience" label="Audience" rules={[{ required: true }]}>
            <Select options={AUDIENCES} />
          </Form.Item>
          <Form.Item name="text" label="Message" rules={[{ required: true }, { max: 4096 }]}>
            <Input.TextArea rows={5} showCount maxLength={4096} />
          </Form.Item>
          <Space align="start" wrap>
            <Form.Item name="buttonText" label="Button text" rules={[{ max: 64 }]}>
              <Input placeholder="Open feature" style={{ width: 220 }} />
            </Form.Item>
            <Form.Item name="buttonUrl" label="Button URL or app path" rules={[{ max: 2048 }]}>
              <Input placeholder="/marketplace" style={{ width: 320 }} />
            </Form.Item>
          </Space>
          {previewM.data && (
            <Typography.Paragraph type="secondary">
              Dry run: {previewM.data.eligibleCount.toLocaleString('ru-RU')} eligible of{' '}
              {previewM.data.rawCount.toLocaleString('ru-RU')} raw recipients.
            </Typography.Paragraph>
          )}
          <Space>
            <Button loading={dryRunM.isPending || previewM.isPending} onClick={runPreview}>
              Dry run / preview
            </Button>
            <Button type="primary" loading={sendM.isPending} onClick={submit}>
              Send now
            </Button>
          </Space>
        </Form>
      </Card>
      <Table<ProductCampaignDto>
        rowKey="id"
        loading={campaignsQ.isLoading}
        dataSource={campaignsQ.data ?? []}
        pagination={false}
        columns={[
          { title: 'Created', dataIndex: 'createdAt', render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm') },
          { title: 'Title', dataIndex: 'title' },
          { title: 'Audience', dataIndex: 'audience', render: (v: string) => <Tag>{v}</Tag> },
          { title: 'Status', dataIndex: 'status', render: (v: string) => <Tag color={v === 'FAILED' ? 'red' : v === 'SENT' ? 'green' : 'blue'}>{v}</Tag> },
          { title: 'Eligible', dataIndex: 'eligibleRecipientCount' },
          { title: 'Sent', dataIndex: 'sentCount' },
          { title: 'Blocked', dataIndex: 'skippedBlockedCount' },
          { title: 'Pref off', dataIndex: 'skippedPreferenceCount' },
          { title: 'Failed', dataIndex: 'failedCount' },
          {
            title: 'Action',
            render: (_, row) =>
              row.status === 'DRAFT' ? (
                <Button
                  size="small"
                  loading={sendExistingM.isPending}
                  onClick={() =>
                    Modal.confirm({
                      title: 'Send draft campaign?',
                      content: row.title,
                      okText: 'Send',
                      onOk: () => sendExistingM.mutateAsync(row.id),
                    })
                  }
                >
                  Send draft
                </Button>
              ) : null,
          },
        ]}
      />
    </Space>
  )
}

function ReleaseNotesTab() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [form] = Form.useForm<ReleaseNoteFormValues>()
  const notesQ = useQuery({ queryKey: ['release-notes-admin'], queryFn: listReleaseNotes })
  const createM = useMutation({
    mutationFn: (values: ReleaseNoteFormValues) =>
      createReleaseNote({
        ...values,
        buttonText: values.buttonText?.trim() || null,
        buttonUrl: values.buttonUrl?.trim() || null,
        minAppVersion: values.minAppVersion?.trim() || null,
      }),
    onSuccess: () => {
      message.success('Release note created')
      form.resetFields()
      void qc.invalidateQueries({ queryKey: ['release-notes-admin'] })
      void qc.invalidateQueries({ queryKey: ['release-note-analytics'] })
    },
    onError: (e: Error) => message.error(e.message),
  })
  const activeM = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => publishReleaseNote(id, active),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['release-notes-admin'] }),
    onError: (e: Error) => message.error(e.message),
  })

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card title="New release note">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ audience: 'ALL', active: true }}
          style={{ maxWidth: 720 }}
          onFinish={(values) => createM.mutate(values)}
        >
          <Form.Item name="title" label="Title" rules={[{ required: true }, { max: 255 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="audience" label="Audience" rules={[{ required: true }]}>
            <Select options={AUDIENCES} />
          </Form.Item>
          <Form.Item name="minAppVersion" label="Min app version">
            <Input placeholder="0.0.0" />
          </Form.Item>
          <Form.Item name="body" label="Body" rules={[{ required: true }]}>
            <Input.TextArea rows={4} />
          </Form.Item>
          <Space align="start" wrap>
            <Form.Item name="buttonText" label="Button text" rules={[{ max: 64 }]}>
              <Input placeholder="Open feature" style={{ width: 220 }} />
            </Form.Item>
            <Form.Item name="buttonUrl" label="Button URL or app path" rules={[{ max: 2048 }]}>
              <Input placeholder="/marketplace" style={{ width: 320 }} />
            </Form.Item>
          </Space>
          <Form.Item name="active" label="Published" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={createM.isPending}>
            Create
          </Button>
        </Form>
      </Card>
      <Table<ReleaseNoteAdminDto>
        rowKey="id"
        loading={notesQ.isLoading}
        dataSource={notesQ.data ?? []}
        pagination={false}
        columns={[
          { title: 'Published at', dataIndex: 'publishedAt', render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm') },
          { title: 'Title', dataIndex: 'title' },
          { title: 'Audience', dataIndex: 'audience', render: (v: string) => <Tag>{v}</Tag> },
          { title: 'Min version', dataIndex: 'minAppVersion', render: (v: string | null) => v ?? 'Any' },
          {
            title: 'Published',
            dataIndex: 'active',
            render: (active: boolean, row) => (
              <Switch
                checked={active}
                loading={activeM.isPending}
                onChange={(next) => activeM.mutate({ id: row.id, active: next })}
              />
            ),
          },
        ]}
        expandable={{
          expandedRowRender: (row) => (
            <Space direction="vertical" align="start">
              <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{row.body}</Typography.Paragraph>
              {row.buttonText && row.buttonUrl ? (
                <Typography.Text type="secondary">
                  {row.buttonText}: {row.buttonUrl}
                </Typography.Text>
              ) : null}
            </Space>
          ),
        }}
      />
    </Space>
  )
}

function AnalyticsTab() {
  const summaryQ = useQuery({ queryKey: ['product-analytics-summary'], queryFn: fetchProductAnalyticsSummary })
  const campaignQ = useQuery({ queryKey: ['product-campaign-analytics'], queryFn: fetchCampaignAnalytics })
  const releaseQ = useQuery({ queryKey: ['release-note-analytics'], queryFn: fetchReleaseNoteAnalytics })
  const summary = summaryQ.data

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space wrap>
        <Card><Statistic title="Users" value={summary?.totalUsers ?? 0} loading={summaryQ.isLoading} /></Card>
        <Card><Statistic title="Bot blocked %" value={summary?.botBlockedPercent ?? 0} precision={1} suffix="%" loading={summaryQ.isLoading} /></Card>
        <Card><Statistic title="First action 24h" value={summary?.startToFirstAction24h ?? 0} loading={summaryQ.isLoading} /></Card>
        <Card><Statistic title="First team 7d" value={summary?.startToFirstTeam7d ?? 0} loading={summaryQ.isLoading} /></Card>
        <Card><Statistic title="Action no team" value={summary?.actionNoTeamUsers ?? 0} loading={summaryQ.isLoading} /></Card>
        <Card><Statistic title="Checklist done %" value={summary?.checklistCompletedPercent ?? 0} precision={1} suffix="%" loading={summaryQ.isLoading} /></Card>
      </Space>
      <Table<ProductCampaignAnalyticsDto>
        rowKey="campaignId"
        title={() => 'Campaign funnel'}
        loading={campaignQ.isLoading}
        dataSource={campaignQ.data ?? []}
        columns={[
          { title: 'Campaign', dataIndex: 'title' },
          { title: 'Audience', dataIndex: 'audience', render: (v: string) => <Tag>{v}</Tag> },
          { title: 'Sent', dataIndex: 'sentCount' },
          { title: 'Open', dataIndex: 'openedCount' },
          { title: 'Click', dataIndex: 'clickedCount' },
          { title: 'Action', dataIndex: 'actedCount' },
        ]}
        pagination={false}
      />
      <Table<ReleaseNoteAnalyticsDto>
        rowKey="releaseNoteId"
        title={() => 'Release notes'}
        loading={releaseQ.isLoading}
        dataSource={releaseQ.data ?? []}
        columns={[
          { title: 'Release note', dataIndex: 'title' },
          { title: 'Audience', dataIndex: 'audience', render: (v: string) => <Tag>{v}</Tag> },
          { title: 'Seen', dataIndex: 'seenCount' },
          { title: 'Feature used', dataIndex: 'featureUsedCount' },
        ]}
        pagination={false}
      />
    </Space>
  )
}
