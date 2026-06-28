import { Table, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { useEffect, useMemo, useState } from 'react'
import { getFantikiAdjustments } from '../api/users'
import type { FantikiAdjustmentDto } from '../api/types'

type Props = {
  telegramUserId: number | undefined
  enabled?: boolean
}

function operationLabel(reason: string) {
  if (reason === 'ADMIN_GRANT') return 'Grant'
  if (reason === 'ADMIN_CONFISCATE') return 'Take'
  return reason
}

export function FantikiAdjustmentsTable({ telegramUserId, enabled = true }: Props) {
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)

  useEffect(() => {
    setPage(0)
  }, [telegramUserId])

  const query = useQuery({
    queryKey: ['admin', 'users', telegramUserId, 'fantiki-adjustments', page, pageSize],
    queryFn: () => getFantikiAdjustments(telegramUserId!, { page, size: pageSize }),
    enabled: enabled && telegramUserId != null,
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
        title: 'Operation',
        dataIndex: 'reason' as const,
        key: 'reason',
        width: 130,
        render: (v: string, row: FantikiAdjustmentDto) => (
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
    <Table<FantikiAdjustmentDto>
      rowKey="id"
      size="small"
      loading={query.isLoading}
      dataSource={query.data?.content ?? []}
      columns={columns}
      pagination={{
        current: page + 1,
        pageSize,
        total: query.data?.totalElements ?? 0,
        showSizeChanger: true,
        onChange: (nextPage, nextPageSize) => {
          setPage(nextPage - 1)
          setPageSize(nextPageSize)
        },
      }}
    />
  )
}
