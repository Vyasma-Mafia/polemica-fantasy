import { App, Button, Input, Modal, Space, Table, Tabs, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { useMemo, useState } from 'react'
import {
  banPair,
  getPairAnalysis,
  getPairTrades,
  unbanMarketplace,
} from '../api/marketplaceAdmin'
import type {
  BanPairResultDto,
  PairAnalysisDto,
  PairTradeDto,
  PairTradesUserBriefDto,
} from '../api/types'

const DEFAULT_BAN_REASON = 'Перелив фантиков между аккаунтами'

type SelectedPair = { userA: number; userB: number }

function pairKey(a: number, b: number) {
  return a < b ? `${a}-${b}` : `${b}-${a}`
}

function sortPair(p: { userATelegramId: number; userBTelegramId: number }): [number, number] {
  const a = p.userATelegramId
  const b = p.userBTelegramId
  return a < b ? [a, b] : [b, a]
}

export function MarketplaceModerationPage() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState('analysis')
  const [selectedPair, setSelectedPair] = useState<SelectedPair | null>(null)
  const [banOpen, setBanOpen] = useState(false)
  const [banReason, setBanReason] = useState(DEFAULT_BAN_REASON)
  const [unbanTg, setUnbanTg] = useState('')

  const analysisQ = useQuery({
    queryKey: ['admin', 'marketplace', 'pair-analysis'],
    queryFn: getPairAnalysis,
  })

  const tradesQ = useQuery({
    queryKey: ['admin', 'marketplace', 'pair-trades', selectedPair?.userA, selectedPair?.userB],
    queryFn: () => getPairTrades(selectedPair!.userA, selectedPair!.userB),
    enabled: selectedPair != null,
  })

  const banMut = useMutation({
    mutationFn: banPair,
    onSuccess: (data) => {
      message.success('Pair ban applied')
      setBanOpen(false)
      setBanResultPreview(data)
      void qc.invalidateQueries({ queryKey: ['admin', 'marketplace'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const unbanMut = useMutation({
    mutationFn: (telegramId: number) => unbanMarketplace(telegramId),
    onSuccess: () => {
      message.success('Marketplace unbanned for user')
      setUnbanTg('')
    },
    onError: (e: Error) => message.error(e.message),
  })

  const [banResultPreview, setBanResultPreview] = useState<BanPairResultDto | null>(null)

  const analysisColumns = useMemo(
    () => [
      {
        title: 'User A (TG)',
        dataIndex: 'userATelegramId' as const,
        key: 'a',
        sorter: (a: PairAnalysisDto, b: PairAnalysisDto) => a.userATelegramId - b.userATelegramId,
      },
      {
        title: 'User B (TG)',
        dataIndex: 'userBTelegramId' as const,
        key: 'b',
      },
      {
        title: 'Trades A→B',
        key: 't1',
        align: 'right' as const,
        sorter: (a: PairAnalysisDto, b: PairAnalysisDto) => a.tradesAtoB - b.tradesAtoB,
        render: (_: unknown, r: PairAnalysisDto) => `${r.tradesAtoB} / ${r.tradesTotalAtoB.toLocaleString('ru-RU')} ₣`,
      },
      {
        title: 'Trades B→A',
        key: 't2',
        align: 'right' as const,
        sorter: (a: PairAnalysisDto, b: PairAnalysisDto) => a.tradesBtoA - b.tradesBtoA,
        render: (_: unknown, r: PairAnalysisDto) => `${r.tradesBtoA} / ${r.tradesTotalBtoA.toLocaleString('ru-RU')} ₣`,
      },
      {
        title: 'Net transfer (after fee)',
        dataIndex: 'netTransfer' as const,
        key: 'net',
        align: 'right' as const,
        sorter: (a: PairAnalysisDto, b: PairAnalysisDto) => a.netTransfer - b.netTransfer,
        render: (v: number) => v.toLocaleString('ru-RU'),
      },
      {
        title: 'Bidir.',
        dataIndex: 'bidirectional' as const,
        key: 'dir',
        width: 80,
        render: (v: boolean) => (v ? 'Yes' : '—'),
      },
      {
        title: 'Actions',
        key: 'act',
        width: 120,
        render: (_: unknown, r: PairAnalysisDto) => (
          <Button
            type="link"
            onClick={() => {
              const [a, b] = sortPair(r)
              setSelectedPair({ userA: a, userB: b })
              setActiveTab('pair-trades')
            }}
          >
            View trades
          </Button>
        ),
      },
    ],
    [],
  )

  const pairUserColumns = useMemo(
    () => [
      {
        title: 'Role',
        key: 'role',
        width: 80,
        render: (_: unknown, r: { label: string; u: PairTradesUserBriefDto }) => r.label,
      },
      {
        title: 'Username',
        key: 'username',
        render: (_: unknown, r: { u: PairTradesUserBriefDto }) => r.u.username ?? '—',
      },
      {
        title: 'Telegram ID',
        key: 'tg',
        render: (_: unknown, r: { u: PairTradesUserBriefDto }) => r.u.telegramId,
      },
      {
        title: 'Display name',
        key: 'dn',
        render: (_: unknown, r: { u: PairTradesUserBriefDto }) => r.u.displayName,
      },
      {
        title: 'Fantiki',
        key: 'f',
        align: 'right' as const,
        render: (_: unknown, r: { u: PairTradesUserBriefDto }) => r.u.fantiki.toLocaleString('ru-RU'),
      },
    ],
    [],
  )

  const tradeColumns = useMemo(
    () => [
      {
        title: 'Sold at',
        key: 'sold',
        width: 180,
        render: (_: unknown, t: PairTradeDto) =>
          t.soldAt == null ? '—' : dayjs(t.soldAt).format('YYYY-MM-DD HH:mm'),
      },
      {
        title: 'Seller TG',
        dataIndex: 'sellerTelegramId' as const,
        key: 'seller',
      },
      {
        title: 'Buyer TG',
        dataIndex: 'buyerTelegramId' as const,
        key: 'buyer',
      },
      { title: 'Player', dataIndex: 'playerName' as const, key: 'player' },
      { title: 'Rarity', dataIndex: 'rarity' as const, key: 'r' },
      {
        title: 'Price',
        dataIndex: 'price' as const,
        key: 'p',
        align: 'right' as const,
        render: (v: number) => v.toLocaleString('ru-RU'),
      },
      { title: 'Current owner TG', dataIndex: 'currentOwnerTelegramId' as const, key: 'own' },
      {
        title: 'Seize card at ban',
        key: 'seize',
        width: 130,
        render: (_: unknown, t: PairTradeDto) => (t.buyerStillOwnsCard ? 'Yes' : 'No'),
      },
    ],
    [],
  )

  return (
    <div>
      <Typography.Title level={3}>Marketplace moderation</Typography.Title>
      <Typography.Paragraph type="secondary">
        Pair analysis of completed marketplace sales, per-pair trade history, ban pair, and unban by Telegram
        user id.
      </Typography.Paragraph>

      <Space direction="vertical" size="large" style={{ display: 'flex' }}>
        <div>
          <Typography.Text strong>Lift marketplace ban (Telegram user id)</Typography.Text>
          <div style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Input
              placeholder="Telegram ID"
              style={{ maxWidth: 200 }}
              value={unbanTg}
              onChange={(e) => setUnbanTg(e.target.value)}
            />
            <Button
              loading={unbanMut.isPending}
              onClick={() => {
                const n = Number.parseInt(unbanTg.replace(/\s/g, ''), 10)
                if (Number.isNaN(n) || n <= 0) {
                  message.error('Invalid Telegram id')
                  return
                }
                unbanMut.mutate(n)
              }}
            >
              Unban
            </Button>
          </div>
        </div>

        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'analysis',
              label: 'Pair analysis',
              children: (
                <Table<PairAnalysisDto>
                  rowKey={(r) => pairKey(r.userATelegramId, r.userBTelegramId)}
                  rowClassName={(r) => (r.bidirectional ? 'ant-table-row-bidirectional' : '')}
                  loading={analysisQ.isLoading}
                  dataSource={analysisQ.data ?? []}
                  columns={analysisColumns}
                  pagination={false}
                  onRow={(record) => ({
                    onClick: (e) => {
                      if ((e.target as HTMLElement).closest('button,a')) return
                      const [a, b] = sortPair(record)
                      setSelectedPair({ userA: a, userB: b })
                      setActiveTab('pair-trades')
                    },
                    style: { cursor: 'pointer' },
                  })}
                />
              ),
            },
            {
              key: 'pair-trades',
              label: 'Pair trades',
              children: selectedPair == null ? (
                <Typography.Text type="secondary">Select a row on Pair analysis or use View trades.</Typography.Text>
              ) : (
                <>
                  {tradesQ.isError && (
                    <Typography.Text type="danger">{(tradesQ.error as Error).message}</Typography.Text>
                  )}
                  {tradesQ.data && (
                    <Table<{ label: string; u: PairTradesUserBriefDto; key: string }>
                      style={{ marginBottom: 16, maxWidth: 900 }}
                      size="small"
                      pagination={false}
                      rowKey="key"
                      columns={pairUserColumns}
                      dataSource={[
                        { key: 'a', label: 'User A', u: tradesQ.data.userA },
                        { key: 'b', label: 'User B', u: tradesQ.data.userB },
                      ]}
                    />
                  )}
                  {tradesQ.data && (
                    <div style={{ marginBottom: 12 }}>
                      <Typography.Text>
                        Total trades: {tradesQ.data.totalTrades} &middot; Gross:{' '}
                        {tradesQ.data.totalGrossFantiki.toLocaleString('ru-RU')} ₣ &middot; Seller received (net):{' '}
                        {tradesQ.data.totalSellerReceived.toLocaleString('ru-RU')} ₣
                      </Typography.Text>
                    </div>
                  )}
                  <Table<PairTradeDto>
                    rowKey="listingId"
                    size="small"
                    loading={tradesQ.isLoading}
                    dataSource={tradesQ.data?.trades ?? []}
                    columns={tradeColumns}
                    pagination={false}
                  />
                  <div style={{ marginTop: 12 }}>
                    <Button
                      danger
                      type="primary"
                      onClick={() => {
                        setBanReason(DEFAULT_BAN_REASON)
                        setBanOpen(true)
                      }}
                    >
                      Sanction pair
                    </Button>
                  </div>
                </>
              ),
            },
          ]}
        />
      </Space>

      <style>{`
        .ant-table-row-bidirectional { background: rgba(255, 77, 79, 0.1) !important; }
        .ant-table-row-bidirectional:hover > td { background: rgba(255, 77, 79, 0.16) !important; }
      `}</style>

      <Modal
        open={banOpen}
        title="Confirm marketplace ban (pair)"
        okText="Confirm ban"
        okButtonProps={{ danger: true, loading: banMut.isPending }}
        onCancel={() => setBanOpen(false)}
        onOk={() => {
          if (selectedPair == null) return
          const r = banReason.trim()
          if (r.length === 0) {
            message.error('Reason is required')
            return
          }
          banMut.mutate({
            telegramIdA: selectedPair.userA,
            telegramIdB: selectedPair.userB,
            reason: r,
          })
        }}
      >
        {selectedPair && (
          <Typography.Paragraph>
            Bans both users, recovers the seller's net from each sale between them, cancels their active
            listings, and deletes only cards that the original buyer still holds (resold cards are not removed).
            Telegram: <strong>{selectedPair.userA}</strong> and <strong>{selectedPair.userB}</strong>.
          </Typography.Paragraph>
        )}
        <Input.TextArea rows={4} value={banReason} onChange={(e) => setBanReason(e.target.value)} />
      </Modal>

      <Modal
        open={banResultPreview != null}
        title="Ban completed"
        footer={<Button onClick={() => setBanResultPreview(null)}>Close</Button>}
        onCancel={() => setBanResultPreview(null)}
      >
        {banResultPreview && (
          <div style={{ fontFamily: 'monospace', fontSize: 12, whiteSpace: 'pre-wrap' }}>
            {JSON.stringify(banResultPreview, null, 2)}
          </div>
        )}
      </Modal>
    </div>
  )
}
