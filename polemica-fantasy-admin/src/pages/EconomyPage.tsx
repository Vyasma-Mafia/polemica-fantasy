import { App, Button, Input, Table, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { bulkUpdateEconomy, listEconomyConfig } from '../api/economy'
import type { EconomyConfigItemDto } from '../api/types'

function categoryForKey(key: string): string {
  if (key.startsWith('card.uses.')) return 'Card uses'
  if (key.startsWith('recycle.value.')) return 'Recycle'
  if (key.startsWith('renewal.')) return 'Renewal'
  if (key.startsWith('series.reward.')) return 'Series rewards'
  return 'Other'
}

export function EconomyPage() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const q = useQuery({
    queryKey: ['admin', 'economy-config'],
    queryFn: listEconomyConfig,
  })
  const [draft, setDraft] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!q.data) return
    const m: Record<string, string> = {}
    for (const row of q.data) m[row.key] = row.value
    queueMicrotask(() => setDraft(m))
  }, [q.data])

  const rows = useMemo(() => {
    const list = q.data ?? []
    return [...list].sort((a, b) => {
      const ca = categoryForKey(a.key)
      const cb = categoryForKey(b.key)
      if (ca !== cb) return ca.localeCompare(cb)
      return a.key.localeCompare(b.key)
    })
  }, [q.data])

  const saveMut = useMutation({
    mutationFn: async () => {
      const items = rows.map((r) => ({ key: r.key, value: draft[r.key] ?? r.value }))
      return bulkUpdateEconomy({ items })
    },
    onSuccess: () => {
      message.success('Economy config saved')
      void qc.invalidateQueries({ queryKey: ['admin', 'economy-config'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const columns = [
    {
      title: 'Category',
      key: 'cat',
      width: 140,
      render: (_: unknown, r: EconomyConfigItemDto) => categoryForKey(r.key),
    },
    {
      title: 'Key',
      dataIndex: 'key',
      width: 220,
    },
    {
      title: 'Description',
      dataIndex: 'description',
      ellipsis: true,
    },
    {
      title: 'Value',
      key: 'value',
      width: 120,
      render: (_: unknown, r: EconomyConfigItemDto) => (
        <Input
          value={draft[r.key] ?? ''}
          onChange={(e) => setDraft((d) => ({ ...d, [r.key]: e.target.value }))}
        />
      ),
    },
  ]

  return (
    <div>
      <Typography.Title level={3}>Economy config</Typography.Title>
      <Typography.Paragraph type="secondary">
        Numeric parameters for card uses, recycle, renewal, and series leaderboard rewards. Save applies all rows.
      </Typography.Paragraph>
      {q.isLoading && <Typography.Text>Loading…</Typography.Text>}
      {q.isError && <Typography.Text type="danger">{(q.error as Error).message}</Typography.Text>}
      <div style={{ marginBottom: 12 }}>
        <Button type="primary" loading={saveMut.isPending} onClick={() => saveMut.mutate()} disabled={!q.data?.length}>
          Save all
        </Button>
      </div>
      <Table<EconomyConfigItemDto>
        rowKey="key"
        loading={q.isLoading}
        dataSource={rows}
        columns={columns}
        pagination={false}
        size="small"
      />
    </div>
  )
}
