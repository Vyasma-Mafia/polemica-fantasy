import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, App, Button, Card, Checkbox, DatePicker, Descriptions, Drawer, Form, Input,
  Modal, Select, Space, Statistic, Table, Tag, Typography,
} from 'antd'
import type { Dayjs } from 'dayjs'
import { useState } from 'react'
import { ApiError } from '../api/client'
import {
  approveAndIssuePeriodicRatingReward,
  createPeriodicRatingPeriod,
  finalizePeriodicRatingPeriod,
  listPeriodicRatingPeriods,
  listPeriodicRatingRewards,
  openPeriodicRatingPeriod,
  previewPeriodicRatingPeriod,
  requestPeriodicRatingRewardChanges,
  updatePeriodicRatingSeries,
  type PeriodicRatingPeriod,
  type PeriodicRatingPreview,
  type PeriodicRatingReward,
  type PeriodicRatingRewardStatus,
  type PeriodicRatingSeriesPreview,
} from '../api/periodicRatings'

type CreateValues = { code: string; title: string; range: [Dayjs, Dayjs] }

const rewardStatuses: PeriodicRatingRewardStatus[] = [
  'AVAILABLE', 'DRAFT', 'REVIEW_REQUIRED', 'CHANGES_REQUESTED', 'FULFILLED', 'OVERDUE', 'CANCELLED',
]
const asMoscowInstant = (value: Dayjs) => `${value.format('YYYY-MM-DDTHH:mm:ss')}+03:00`
const formatMoscow = (value: string) => new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Moscow',
}).format(new Date(value))
const valueAsString = (value: unknown) => typeof value === 'string' ? value : null
const valueAsNumber = (value: unknown) => typeof value === 'number' ? value : null
const valueAsStrings = (value: unknown) => Array.isArray(value) && value.every((item) => typeof item === 'string') ? value : []

function rewardSelectionComplete(reward: PeriodicRatingReward) {
  const playerId = valueAsNumber(reward.selection.playerId)
  const skinCode = valueAsString(reward.selection.skinCode)
  const perkIds = valueAsStrings(reward.selection.perkIds)
  const requiredPerks = valueAsNumber(reward.policy.perkSelectionCount) ?? 0
  const allowedSkins = valueAsStrings(reward.policy.skinCodes)
  if (!playerId || !skinCode || !allowedSkins.includes(skinCode) || perkIds.length !== requiredPerks) return false
  if (new Set(perkIds).size !== perkIds.length) return false
  if (reward.policy.perkSelectionMode === 'BUNDLED_OPTIONS') {
    const bundles = Array.isArray(reward.policy.bundles) ? reward.policy.bundles : []
    return bundles.some((bundle) => {
      if (!bundle || typeof bundle !== 'object') return false
      const row = bundle as Record<string, unknown>
      const bundlePerks = valueAsStrings(row.perkIds)
      return valueAsNumber(row.playerId) === playerId && bundlePerks.length === perkIds.length && bundlePerks.every((id, index) => id === perkIds[index])
    })
  }
  return valueAsStrings(reward.policy.perkPool).length === 0 || perkIds.every((id) => valueAsStrings(reward.policy.perkPool).includes(id))
}

function JsonBlock({ value }: { value: Record<string, unknown> }) {
  return <Typography.Paragraph style={{ margin: 0 }}><pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', margin: 0 }}>{JSON.stringify(value, null, 2)}</pre></Typography.Paragraph>
}

export function PeriodicRatingsPage() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [preview, setPreview] = useState<PeriodicRatingPreview | null>(null)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [exclusion, setExclusion] = useState<PeriodicRatingSeriesPreview | null>(null)
  const [reason, setReason] = useState('')
  const [finalizeOpen, setFinalizeOpen] = useState(false)
  const [finalizeReason, setFinalizeReason] = useState('')
  const [finalizeAcknowledged, setFinalizeAcknowledged] = useState(false)
  const [rewardPeriodId, setRewardPeriodId] = useState<number | undefined>()
  const [rewardStatus, setRewardStatus] = useState<PeriodicRatingRewardStatus | undefined>()
  const [rewardDetails, setRewardDetails] = useState<PeriodicRatingReward | null>(null)
  const [changesOpen, setChangesOpen] = useState(false)
  const [changesReason, setChangesReason] = useState('')
  const [form] = Form.useForm<CreateValues>()

  const periods = useQuery({ queryKey: ['admin', 'periodic-ratings'], queryFn: listPeriodicRatingPeriods })
  const rewards = useQuery({
    queryKey: ['admin', 'periodic-rating-rewards', rewardPeriodId ?? null, rewardStatus ?? null],
    queryFn: () => listPeriodicRatingRewards({ periodId: rewardPeriodId, status: rewardStatus }),
  })
  const refreshPeriods = () => qc.invalidateQueries({ queryKey: ['admin', 'periodic-ratings'] })
  const refreshRewards = () => qc.invalidateQueries({ queryKey: ['admin', 'periodic-rating-rewards'] })

  const createM = useMutation({
    mutationFn: (v: CreateValues) => createPeriodicRatingPeriod({ code: v.code, title: v.title, startsAt: asMoscowInstant(v.range[0]), endsAt: asMoscowInstant(v.range[1]) }),
    onSuccess: async () => { message.success('Draft period created'); setCreateOpen(false); form.resetFields(); await refreshPeriods() },
    onError: (e: Error) => message.error(e.message),
  })
  const openM = useMutation({
    mutationFn: openPeriodicRatingPeriod,
    onSuccess: async () => { message.success('Period opened'); await refreshPeriods() },
    onError: (e: Error) => message.error(e.message),
  })
  const previewM = useMutation({
    mutationFn: previewPeriodicRatingPeriod,
    onSuccess: (result) => { setPreview(result); setPreviewError(null) },
    onError: (e: Error) => { setPreviewError(e.message); message.error(e.message) },
  })
  const seriesM = useMutation({
    mutationFn: ({ series, included, reason: seriesReason }: { series: PeriodicRatingSeriesPreview; included: boolean; reason?: string }) => updatePeriodicRatingSeries(preview!.period.id, series.seriesId, { included, reason: seriesReason }),
    onSuccess: (result) => { setPreview(result); setExclusion(null); setReason(''); message.success('Series selection updated') },
    onError: (e: Error) => message.error(e.message),
  })
  const finalizeM = useMutation({
    mutationFn: ({ periodId, sourceChecksum, reason: finalReason }: { periodId: number; sourceChecksum: string; reason: string }) => finalizePeriodicRatingPeriod(periodId, { sourceChecksum, reason: finalReason }),
    onSuccess: async (result) => {
      setPreview(result)
      setFinalizeOpen(false)
      setFinalizeReason('')
      setFinalizeAcknowledged(false)
      message.success('Period finalized and rewards created')
      await Promise.all([refreshPeriods(), refreshRewards()])
    },
    onError: async (error: Error, variables) => {
      if (error instanceof ApiError && error.status === 409) {
        setPreviewError(`${error.message} Preview has been refreshed; review the new checksum and liability before retrying.`)
        try {
          const fresh = await previewPeriodicRatingPeriod(variables.periodId)
          setPreview(fresh)
        } catch (refreshError) {
          setPreviewError(`${error.message} Automatic preview refresh failed: ${(refreshError as Error).message}`)
        }
        setFinalizeOpen(false)
      }
      message.error(error.message)
    },
  })
  const changesM = useMutation({
    mutationFn: ({ reward, reason: changeReason }: { reward: PeriodicRatingReward; reason: string }) => requestPeriodicRatingRewardChanges(reward.id, { reason: changeReason, version: reward.version }),
    onSuccess: async () => { setChangesOpen(false); setChangesReason(''); setRewardDetails(null); message.success('Changes requested'); await refreshRewards() },
    onError: async (error: Error) => {
      const stale = error instanceof ApiError && error.status === 409
      if (stale) await refreshRewards()
      message.error(stale ? `${error.message} Reward list was reloaded.` : error.message)
    },
  })
  const approveM = useMutation({
    mutationFn: (reward: PeriodicRatingReward) => approveAndIssuePeriodicRatingReward(reward.id, { version: reward.version }),
    onSuccess: async (result) => { setRewardDetails(result); message.success('Reward approved and card issued'); await refreshRewards() },
    onError: async (error: Error) => {
      const stale = error instanceof ApiError && error.status === 409
      if (stale) { setRewardDetails(null); await refreshRewards() }
      message.error(stale ? `${error.message} Reward list was reloaded.` : error.message)
    },
  })

  const statusColor = (status: PeriodicRatingPeriod['status']) => ({ DRAFT: 'default', OPEN: 'green', SETTLING: 'gold', FINALIZED: 'blue', CANCELLED: 'red' }[status])
  const openReward = (reward: PeriodicRatingReward) => setRewardDetails(reward)

  return <Space direction="vertical" size="large" style={{ width: '100%' }}>
    <Space style={{ width: '100%', justifyContent: 'space-between' }}>
      <Typography.Title level={2} style={{ margin: 0 }}>Periodic ratings</Typography.Title>
      <Button type="primary" onClick={() => setCreateOpen(true)}>Create period</Button>
    </Space>

    <Card title="Periods">
      <Table rowKey="id" loading={periods.isLoading} dataSource={periods.data ?? []} pagination={false} columns={[
        { title: 'Period', render: (_: unknown, period: PeriodicRatingPeriod) => <><b>{period.title}</b><br/><Typography.Text type="secondary">{period.code}</Typography.Text></> },
        { title: 'Range (MSK)', render: (_: unknown, period: PeriodicRatingPeriod) => `${formatMoscow(period.startsAt)} — ${formatMoscow(period.endsAt)}` },
        { title: 'Status', dataIndex: 'status', render: (status: PeriodicRatingPeriod['status']) => <Tag color={statusColor(status)}>{status}</Tag> },
        { title: 'Scope', render: (_: unknown, period: PeriodicRatingPeriod) => `${period.league} · ${period.timezone}` },
        { title: 'Actions', render: (_: unknown, period: PeriodicRatingPeriod) => <Space><Button loading={previewM.isPending && previewM.variables === period.id} onClick={() => previewM.mutate(period.id)}>Preview</Button>{period.status === 'DRAFT' && <Button type="primary" loading={openM.isPending} onClick={() => Modal.confirm({ title: 'Open this period?', content: 'It will become visible in the TMA.', onOk: () => openM.mutateAsync(period.id) })}>Open</Button>}</Space> },
      ]}/>
    </Card>

    <Card title="Reward management" extra={<Button loading={rewards.isFetching} onClick={() => rewards.refetch()}>Refresh</Button>}>
      <Space wrap style={{ marginBottom: 16 }}>
        <Select<number> allowClear placeholder="All periods" style={{ minWidth: 240 }} value={rewardPeriodId} onChange={setRewardPeriodId} options={(periods.data ?? []).map((period) => ({ value: period.id, label: `${period.title} (${period.code})` }))}/>
        <Select<PeriodicRatingRewardStatus> allowClear placeholder="All statuses" style={{ minWidth: 200 }} value={rewardStatus} onChange={setRewardStatus} options={rewardStatuses.map((status) => ({ value: status, label: status }))}/>
      </Space>
      {rewards.isError && <Alert type="error" showIcon message="Could not load rewards" description={rewards.error.message} action={<Button onClick={() => rewards.refetch()}>Retry</Button>} style={{ marginBottom: 16 }}/>} 
      <Table rowKey="id" loading={rewards.isLoading} dataSource={rewards.data ?? []} locale={{ emptyText: 'No rewards match these filters' }} columns={[
        { title: 'Reward', render: (_: unknown, reward: PeriodicRatingReward) => <><Typography.Text copyable>#{reward.id}</Typography.Text><br/><Typography.Text type="secondary">{reward.serial}</Typography.Text></> },
        { title: 'Period', render: (_: unknown, reward: PeriodicRatingReward) => <>{reward.periodTitle}<br/><Typography.Text type="secondary">{reward.periodCode}</Typography.Text></> },
        { title: 'Winner', render: (_: unknown, reward: PeriodicRatingReward) => <>{reward.user.displayName || reward.user.firstName || reward.user.username || reward.user.telegramId}<br/><Typography.Text type="secondary">{reward.user.username ? `@${reward.user.username}` : `tg ${reward.user.telegramId}`}</Typography.Text></> },
        { title: 'Rank', dataIndex: 'rank', render: (rank: number) => `#${rank}` },
        { title: 'Status', dataIndex: 'status', render: (status: string) => <Tag color={status === 'FULFILLED' ? 'green' : status === 'REVIEW_REQUIRED' ? 'gold' : undefined}>{status}</Tag> },
        { title: 'Tier', render: (_: unknown, reward: PeriodicRatingReward) => valueAsString(reward.policy.editionTier) ?? '—' },
        { title: 'Selection', render: (_: unknown, reward: PeriodicRatingReward) => rewardSelectionComplete(reward) ? <Tag color="green">complete</Tag> : <Tag color="red">incomplete</Tag> },
        { title: '', render: (_: unknown, reward: PeriodicRatingReward) => <Button size="small" onClick={() => openReward(reward)}>Details</Button> },
      ]}/>
    </Card>

    <Modal title="Create rating period" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={() => form.submit()} confirmLoading={createM.isPending}>
      <Form form={form} layout="vertical" onFinish={(values) => createM.mutate(values)}>
        <Form.Item name="code" label="Code" rules={[{ required: true }, { pattern: /^[a-z0-9_-]+$/, message: 'Use lowercase letters, numbers, _ or -' }]}><Input placeholder="2026-07-20_2026-08-03" /></Form.Item>
        <Form.Item name="title" label="Public title" rules={[{ required: true, whitespace: true }]}><Input placeholder="Рейтинг 20 июля — 2 августа" /></Form.Item>
        <Form.Item name="range" label="Range [start, end), MSK" rules={[{ required: true }]}><DatePicker.RangePicker showTime={{ format: 'HH:mm' }} format="DD.MM.YYYY HH:mm" style={{ width: '100%' }} /></Form.Item>
      </Form>
    </Modal>

    <Drawer title={preview ? `Preview · ${preview.period.title}` : 'Preview'} width={960} open={!!preview} onClose={() => { setPreview(null); setPreviewError(null) }} extra={preview && <Space><Button loading={previewM.isPending} onClick={() => previewM.mutate(preview.period.id)}>Refresh</Button>{preview.period.status === 'SETTLING' && <Button type="primary" disabled={preview.blockers.length > 0} onClick={() => { setFinalizeReason(''); setFinalizeAcknowledged(false); setFinalizeOpen(true) }}>Finalize</Button>}</Space>}>
      {preview && <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {previewError && <Alert type="error" showIcon message="Preview changed or could not be loaded" description={previewError}/>} 
        <Descriptions bordered size="small" column={2} items={[
          { key: 'status', label: 'Status', children: <Tag color={statusColor(preview.period.status)}>{preview.period.status}</Tag> },
          { key: 'checksum', label: 'Source checksum', children: <Typography.Text copyable code>{preview.sourceChecksum}</Typography.Text> },
          { key: 'blockers', label: 'Blockers', children: <Tag color={preview.blockers.length ? 'red' : 'green'}>{preview.blockers.length}</Tag> },
          { key: 'participants', label: 'Participants', children: preview.leaderboard.length },
        ]}/>
        {preview.period.status !== 'SETTLING' && <Alert type="info" showIcon message={`Finalization is unavailable while the period is ${preview.period.status}.`}/>} 
        <Space wrap>{Object.entries(preview.rewardLiability).map(([name, count]) => <Card size="small" key={name}><Statistic title={`Reward · ${name}`} value={count}/></Card>)}</Space>
        {preview.blockers.length > 0 && <Alert type="warning" showIcon message="Period cannot be finalized" description={`${preview.blockers.length} candidate series are not finalized or have no played_at.`}/>} 
        <Typography.Title level={4}>Candidate series</Typography.Title>
        <Table size="small" rowKey="seriesId" pagination={false} dataSource={preview.series} columns={[
          { title: 'Series', render: (_: unknown, series: PeriodicRatingSeriesPreview) => <>{series.tournamentName}<br/><b>{series.seriesName}</b></> },
          { title: 'Effective at', dataIndex: 'effectiveAt', render: (value: string | null) => value ? formatMoscow(value) : <Tag color="red">missing</Tag> },
          { title: 'State', render: (_: unknown, series: PeriodicRatingSeriesPreview) => <Space direction="vertical" size={2}><Tag color={series.finalized ? 'green' : 'gold'}>{series.finalized ? 'finalized' : 'pending'}</Tag>{series.blocker && <Tag color="red">blocker</Tag>}</Space> },
          { title: 'Selection', render: (_: unknown, series: PeriodicRatingSeriesPreview) => series.included ? <Tag color="blue">included</Tag> : <><Tag>excluded</Tag><br/><Typography.Text type="secondary">{series.reason}</Typography.Text></> },
          { title: '', render: (_: unknown, series: PeriodicRatingSeriesPreview) => series.included ? <Button danger size="small" onClick={() => { setExclusion(series); setReason('') }}>Exclude</Button> : <Button size="small" loading={seriesM.isPending} onClick={() => seriesM.mutate({ series, included: true })}>Include</Button> },
        ]}/>
        <Typography.Title level={4}>Leaderboard</Typography.Title>
        <Table size="small" rowKey={(entry) => entry.user.telegramId} dataSource={preview.leaderboard} columns={[
          { title: '#', dataIndex: 'rank', width: 60 },
          { title: 'User', render: (_: unknown, entry) => entry.user.displayName || entry.user.firstName || entry.user.username || entry.user.telegramId },
          { title: 'Total', dataIndex: 'totalScore' }, { title: 'Series', dataIndex: 'seriesCount' }, { title: 'Average', dataIndex: 'averageScore' }, { title: 'Best', dataIndex: 'bestSeriesScore' },
        ]}/>
      </Space>}
    </Drawer>

    <Modal title="Finalize period" open={finalizeOpen} okText="Finalize and create rewards" okButtonProps={{ danger: true, disabled: !finalizeReason.trim() || !finalizeAcknowledged || !!preview?.blockers.length }} confirmLoading={finalizeM.isPending} onCancel={() => setFinalizeOpen(false)} onOk={() => preview && finalizeM.mutate({ periodId: preview.period.id, sourceChecksum: preview.sourceChecksum, reason: finalizeReason.trim() })}>
      <Alert type="warning" showIcon message="This freezes the leaderboard and creates reward entitlements." description="Verify the source checksum, leaderboard, ties and reward liability before continuing." style={{ marginBottom: 16 }}/>
      <Typography.Paragraph><Typography.Text strong>Checksum: </Typography.Text><Typography.Text code copyable>{preview?.sourceChecksum}</Typography.Text></Typography.Paragraph>
      <Input.TextArea rows={3} value={finalizeReason} onChange={(event) => setFinalizeReason(event.target.value)} placeholder="Required audit reason" style={{ marginBottom: 16 }}/>
      <Checkbox checked={finalizeAcknowledged} onChange={(event) => setFinalizeAcknowledged(event.target.checked)}>I reviewed blockers, ties and reward liability and accept this immutable snapshot.</Checkbox>
    </Modal>

    <Modal title="Exclude series" open={!!exclusion} okText="Exclude" okButtonProps={{ danger: true, disabled: !reason.trim() }} confirmLoading={seriesM.isPending} onCancel={() => setExclusion(null)} onOk={() => exclusion && seriesM.mutate({ series: exclusion, included: false, reason: reason.trim() })}>
      <Alert type="warning" showIcon message="The public reason will be visible to users." style={{ marginBottom: 16 }}/><Input.TextArea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Why this series is excluded" />
    </Modal>

    <Drawer title={rewardDetails ? `Reward #${rewardDetails.id} · ${rewardDetails.serial}` : 'Reward details'} width={760} open={!!rewardDetails} onClose={() => setRewardDetails(null)} extra={rewardDetails?.status === 'REVIEW_REQUIRED' && <Space><Button onClick={() => { setChangesReason(''); setChangesOpen(true) }}>Request changes</Button><Button type="primary" disabled={!rewardSelectionComplete(rewardDetails)} loading={approveM.isPending} onClick={() => Modal.confirm({ title: 'Approve and issue this reward?', content: `Version ${rewardDetails.version} will be validated and the issue is irreversible.`, okText: 'Approve & issue', onOk: () => approveM.mutateAsync(rewardDetails) })}>Approve & issue</Button></Space>}>
      {rewardDetails && <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {rewardDetails.status === 'REVIEW_REQUIRED' && <Alert type="info" showIcon message="Legacy review" description="New rewards are issued automatically after the winner confirms the card. Manual approval is available only for rewards submitted before that flow was enabled."/>}
        {!rewardSelectionComplete(rewardDetails) && rewardDetails.status === 'REVIEW_REQUIRED' && <Alert type="error" showIcon message="Selection is incomplete or does not match the frozen policy. Issue is disabled."/>}
        {rewardDetails.changesRequestedReason && <Alert type="warning" showIcon message="Changes requested" description={rewardDetails.changesRequestedReason}/>} 
        <Descriptions bordered size="small" column={2} items={[
          { key: 'period', label: 'Period', children: `${rewardDetails.periodTitle} (${rewardDetails.periodCode})` },
          { key: 'rank', label: 'Rank', children: `#${rewardDetails.rank}` },
          { key: 'winner', label: 'Winner', children: rewardDetails.user.displayName || rewardDetails.user.firstName || rewardDetails.user.username || rewardDetails.user.telegramId },
          { key: 'status', label: 'Status', children: <Tag>{rewardDetails.status}</Tag> },
          { key: 'tier', label: 'Tier', children: valueAsString(rewardDetails.policy.editionTier) ?? '—' },
          { key: 'version', label: 'Version', children: rewardDetails.version },
          { key: 'deadline', label: 'Claim deadline', children: rewardDetails.claimDeadline ? formatMoscow(rewardDetails.claimDeadline) : '—' },
          { key: 'player', label: 'Chosen player', children: rewardDetails.selectedPlayer ? `${rewardDetails.selectedPlayer.nickname} · Polemica ${rewardDetails.selectedPlayer.polemicaUserId}` : valueAsNumber(rewardDetails.selection.playerId) ?? '—' },
          { key: 'skin', label: 'Chosen skin', children: valueAsString(rewardDetails.selection.skinCode) ?? '—' },
          { key: 'perks', label: 'Chosen perks', children: valueAsStrings(rewardDetails.selection.perkIds).length ? <Space wrap>{valueAsStrings(rewardDetails.selection.perkIds).map((perk) => <Tag key={perk}>{perk}</Tag>)}</Space> : 'None' },
          { key: 'fantiki', label: 'Fantiki', children: rewardDetails.fantikiAmount > 0 ? <Tag color={rewardDetails.fantikiGrantedAt ? 'green' : 'gold'}>{rewardDetails.fantikiAmount} ₣ · {rewardDetails.fantikiGrantedAt ? `granted ${formatMoscow(rewardDetails.fantikiGrantedAt)}` : 'not granted'}</Tag> : 'None' },
          { key: 'template', label: 'Issued template ID', children: rewardDetails.issuedCardTemplateId ?? '—' },
          { key: 'card', label: 'Issued user card ID', children: rewardDetails.issuedUserCardId ?? '—' },
          { key: 'issued', label: 'Issued at', children: rewardDetails.issuedAt ? formatMoscow(rewardDetails.issuedAt) : '—' },
          { key: 'updated', label: 'Updated at', children: formatMoscow(rewardDetails.updatedAt) },
        ]}/>
        <Typography.Title level={4}>Frozen policy</Typography.Title><Card size="small"><JsonBlock value={rewardDetails.policy}/></Card>
        <Typography.Title level={4}>Player selection</Typography.Title><Card size="small"><JsonBlock value={rewardDetails.selection}/></Card>
      </Space>}
    </Drawer>

    <Modal title="Request reward changes" open={changesOpen} okText="Request changes" okButtonProps={{ disabled: !changesReason.trim() }} confirmLoading={changesM.isPending} onCancel={() => setChangesOpen(false)} onOk={() => rewardDetails && changesM.mutate({ reward: rewardDetails, reason: changesReason.trim() })}>
      <Typography.Paragraph>The reason is mandatory and will be shown to the reward owner.</Typography.Paragraph>
      <Input.TextArea rows={4} value={changesReason} onChange={(event) => setChangesReason(event.target.value)} placeholder="What must be corrected" />
    </Modal>
  </Space>
}
