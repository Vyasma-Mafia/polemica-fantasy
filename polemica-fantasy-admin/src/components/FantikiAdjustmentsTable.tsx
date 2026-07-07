import { Select, Space, Table, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { useEffect, useMemo, useState } from 'react'
import { ADMIN_UNPAGINATED_SIZE } from '../api/pagination'
import { getFantikiTransactions } from '../api/users'
import type { FantikiTransactionDto } from '../api/types'

type Props = {
  telegramUserId?: number
  defaultReason?: string | null
  enabled?: boolean
}

function operationLabel(reason: string) {
  if (reason === 'ADMIN_GRANT') return 'Grant'
  if (reason === 'ADMIN_CONFISCATE') return 'Take'
  return reason
}

const reasonOptions = [
  { value: 'ADMIN_GRANT', label: 'Admin grants' },
  { value: 'ADMIN_CONFISCATE', label: 'Admin takes' },
  { value: 'ALL', label: 'All transactions' },
]

export function FantikiAdjustmentsTable({
  telegramUserId,
  defaultReason = 'ADMIN_GRANT',
  enabled = true,
}: Props) {
  const [reasonFilter, setReasonFilter] = useState<string>(defaultReason ?? 'ALL')

  useEffect(() => {
    setReasonFilter(defaultReason ?? 'ALL')
  }, [defaultReason])

  const query = useQuery({
    queryKey: ['admin', 'fantiki-transactions', telegramUserId ?? null, reasonFilter],
    queryFn: () =>
      getFantikiTransactions({
        telegramUserId,
        reason: reasonFilter === 'ALL' ? null : reasonFilter,
        page: 0,
        size: ADMIN_UNPAGINATED_SIZE,
      }),
    enabled,
  })

  const columns = useMemo(
    () => [
      {
        title: 'Created at',
        dataIndex: 'createdAt' as const,
        key: 'createdAt',
        width: 180,
        render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
      },
      {
        title: 'Telegram ID',
        dataIndex: 'telegramId' as const,
        key: 'telegramId',
        width: 140,
      },
      {
        title: 'Operation',
        dataIndex: 'reason' as const,
        key: 'reason',
        width: 130,
        render: (v: string, row: FantikiTransactionDto) => (
          <Tag color={row.amount >= 0 ? 'green' : 'red'}>{operationLabel(v)}</Tag>
        ),
      },
      {
        title: 'Amount',
        dataIndex: 'amount' as const,
        key: 'amount',
        align: 'right' as const,
        width: 120,
        render: (v: number) => (
          <Typography.Text type={v >= 0 ? 'success' : 'danger'}>
            {v > 0 ? '+' : ''}
            {v.toLocaleString('ru-RU')}
          </Typography.Text>
        ),
      },
      {
        title: 'Reason',
        dataIndex: 'adminReason' as const,
        key: 'adminReason',
        render: (v: string | null) => (v == null || v === '' ? '—' : v),
      },
    ],
    [],
  )

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Select
        style={{ width: 220 }}
        options={reasonOptions}
        value={reasonFilter}
        onChange={(nextReason) => {
          setReasonFilter(nextReason)
        }}
      />
      <Table<FantikiTransactionDto>
        rowKey="id"
        size="small"
        loading={query.isLoading}
        dataSource={query.data?.content ?? []}
        columns={columns}
        pagination={false}
      />
    </Space>
  )
}
