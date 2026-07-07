import { Button, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { listSeriesByTournament } from '../api/series'
import { listTournaments } from '../api/tournaments'
import type { AdminUserListItemDto } from '../api/types'
import { listAdminUsers } from '../api/usersList'
import { FantikiAdjustmentsTable } from '../components/FantikiAdjustmentsTable'

function dash(v: string | null | undefined) {
  return v != null && v !== '' ? v : '—'
}

export function UsersOverviewPage() {
  const [tournamentId, setTournamentId] = useState<number | undefined>()
  const [seriesId, setSeriesId] = useState<number | undefined>()
  const [userSearch, setUserSearch] = useState('')
  const [userSearchDebounced, setUserSearchDebounced] = useState('')
  const [historyUser, setHistoryUser] = useState<AdminUserListItemDto | null>(null)

  useEffect(() => {
    const t = setTimeout(() => {
      setUserSearchDebounced(userSearch.trim())
    }, 300)
    return () => clearTimeout(t)
  }, [userSearch])

  const tournamentsQ = useQuery({
    queryKey: ['admin', 'tournaments'],
    queryFn: listTournaments,
  })

  const seriesQ = useQuery({
    queryKey: ['admin', 'series', 'tournament', tournamentId],
    queryFn: () => listSeriesByTournament(tournamentId!),
    enabled: tournamentId != null,
  })

  const qParam = userSearchDebounced || undefined

  const usersQ = useQuery({
    queryKey: ['admin', 'users', tournamentId, seriesId, qParam],
    queryFn: () => {
      if (tournamentId != null && seriesId != null) {
        return listAdminUsers({ tournamentId, seriesId, q: qParam })
      }
      return listAdminUsers({ q: qParam })
    },
  })

  const columns = useMemo(
    () => [
      {
        title: 'Username',
        dataIndex: 'username' as const,
        key: 'username',
        render: (_: unknown, r: AdminUserListItemDto) => dash(r.username),
      },
      {
        title: 'Telegram ID',
        dataIndex: 'telegramId' as const,
        key: 'telegramId',
      },
      {
        title: 'Display name',
        dataIndex: 'displayName' as const,
        key: 'displayName',
        render: (_: unknown, r: AdminUserListItemDto) => dash(r.displayName),
      },
      {
        title: 'Fantiki',
        dataIndex: 'fantiki' as const,
        key: 'fantiki',
        align: 'right' as const,
        render: (v: number) => v.toLocaleString('ru-RU'),
      },
      {
        title: 'Bot',
        dataIndex: 'botBlocked' as const,
        key: 'botBlocked',
        render: (v: boolean) => (
          <Tag color={v ? 'red' : 'green'}>{v ? 'Blocked' : 'Available'}</Tag>
        ),
      },
      {
        title: 'Cards (series)',
        dataIndex: 'cardsInSeries' as const,
        key: 'cardsInSeries',
        render: (v: number | null) => (v == null ? '—' : v),
      },
      {
        title: 'Actions',
        key: 'actions',
        render: (_: unknown, r: AdminUserListItemDto) => (
          <Button size="small" onClick={() => setHistoryUser(r)}>
            Fantiki transactions
          </Button>
        ),
      },
    ],
    [],
  )

  return (
    <div>
      <Typography.Title level={3}>Users</Typography.Title>
      <Typography.Paragraph type="secondary">
        Pick a tournament and series to show how many card instances each user has
        for players on that series roster (same rules as in-app collection).
      </Typography.Paragraph>

      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          allowClear
          placeholder="Search username, Telegram ID, or display name"
          value={userSearch}
          onChange={(e) => setUserSearch(e.target.value)}
          style={{ minWidth: 300 }}
        />
        <Select
          allowClear
          placeholder="Tournament"
          style={{ minWidth: 240 }}
          loading={tournamentsQ.isLoading}
          options={tournamentsQ.data?.map((t) => ({
            value: t.id,
            label: `#${t.id} ${t.name}`,
          }))}
          value={tournamentId}
          onChange={(v) => {
            setTournamentId(v ?? undefined)
            setSeriesId(undefined)
          }}
        />
        <Select
          allowClear
          placeholder="Series"
          style={{ minWidth: 260 }}
          disabled={tournamentId == null}
          loading={seriesQ.isLoading}
          options={seriesQ.data?.map((s) => ({
            value: s.id,
            label: `${s.name}`,
          }))}
          value={seriesId}
          onChange={(v) => setSeriesId(v ?? undefined)}
        />
      </Space>

      <Table<AdminUserListItemDto>
        rowKey="id"
        loading={usersQ.isLoading}
        dataSource={usersQ.data ?? []}
        columns={columns}
        pagination={false}
      />

      <Modal
        title={
          historyUser == null
            ? 'Fantiki transactions'
            : `Fantiki transactions · ${dash(historyUser.displayName ?? historyUser.username)} · ${historyUser.telegramId}`
        }
        open={historyUser != null}
        onCancel={() => setHistoryUser(null)}
        footer={null}
        width={900}
        destroyOnHidden
      >
        <FantikiAdjustmentsTable
          telegramUserId={historyUser?.telegramId}
          enabled={historyUser != null}
        />
      </Modal>
    </div>
  )
}
