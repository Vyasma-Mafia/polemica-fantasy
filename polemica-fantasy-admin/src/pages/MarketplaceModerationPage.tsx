import {
  Alert,
  App,
  Button,
  Checkbox,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { useMemo, useState } from 'react'
import { listEconomyConfig } from '../api/economy'
import {
  banMarketplaceUser,
  banPair,
  getComplainedTransactions,
  getBanPairHistory,
  getBanPairPreview,
  getPairAnalysis,
  getPairTrades,
  getTransactionComplaints,
  getUsersByComplaints,
  markPairCleared,
  sanctionTransaction,
  unbanMarketplace,
  unmarkPairCleared,
} from '../api/marketplaceAdmin'
import type {
  BanUserRequest,
  BanPairResultDto,
  BanPairPreviewUserDto,
  ComplainedTransactionDto,
  ConcurrentListingDto,
  EconomyConfigItemDto,
  PairAnalysisDto,
  PairSanctionHistoryItemDto,
  PairTradeDto,
  PairTradesUserBriefDto,
  Rarity,
  SanctionTransactionRequest,
  TransactionComplaintDetailDto,
  UserByComplaintsDto,
} from '../api/types'

const DEFAULT_BAN_REASON = 'Перелив фантиков между аккаунтами'
const DEFAULT_SANCTION_REASON = 'Нерыночная сделка'

type SelectedPair = { userA: number; userB: number }
type BanMode = '3' | '7' | '30' | 'permanent' | 'custom'

type SanctionFormValues = {
  reason: string
  sellerFine: number
  buyerFine: number
  complainantReward: number
  banSellerEnabled: boolean
  banSellerDays: number
  banBuyerEnabled: boolean
  banBuyerDays: number
}

type BanUserFormValues = {
  mode: BanMode
  customDays: number
}

function pairKey(a: number, b: number) {
  return a < b ? `${a}-${b}` : `${b}-${a}`
}

function sortPair(p: { userATelegramId: number; userBTelegramId: number }): [number, number] {
  const a = p.userATelegramId
  const b = p.userBTelegramId
  return a < b ? [a, b] : [b, a]
}

function rarityColor(rarity: Rarity): string {
  switch (rarity) {
    case 'COMMON':
      return 'default'
    case 'RARE':
      return 'cyan'
    case 'EPIC':
      return 'purple'
    case 'LEGENDARY':
      return 'gold'
    default:
      return 'default'
  }
}

function parseMarketplaceCommissionPercent(rows: EconomyConfigItemDto[] | undefined): number {
  const raw = rows?.find((x) => x.key === 'marketplace.commission_percent')?.value
  const parsed = Number.parseInt(raw ?? '', 10)
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 10
  }
  return parsed
}

function hasBanMarker(record: UserByComplaintsDto): boolean {
  return record.marketplaceBanned || record.marketplaceBannedUntil != null
}

function isBanActive(record: UserByComplaintsDto): boolean {
  if (record.marketplaceBanned) return true
  if (record.marketplaceBannedUntil == null) return false
  return dayjs(record.marketplaceBannedUntil).isAfter(dayjs())
}

function renderBanStatus(record: UserByComplaintsDto) {
  if (record.marketplaceBanned) {
    return <Tag color="red">Перманентный бан</Tag>
  }
  if (record.marketplaceBannedUntil) {
    const until = dayjs(record.marketplaceBannedUntil)
    if (until.isAfter(dayjs())) {
      return <Tag color="orange">Бан до {until.format('DD.MM.YYYY HH:mm')}</Tag>
    }
  }
  return <Tag color="green">Активен</Tag>
}

function formatMarketDuration(createdAt: string, soldAt: string): string {
  const durationMinutes = Math.max(0, dayjs(soldAt).diff(dayjs(createdAt), 'minute'))
  if (durationMinutes < 60) {
    return `${durationMinutes} мин`
  }
  const durationHours = Math.floor(durationMinutes / 60)
  if (durationHours < 24) {
    const restMinutes = durationMinutes % 60
    return restMinutes > 0 ? `${durationHours} ч ${restMinutes} мин` : `${durationHours} ч`
  }
  const durationDays = Math.floor(durationHours / 24)
  const restHours = durationHours % 24
  return restHours > 0 ? `${durationDays} д ${restHours} ч` : `${durationDays} д`
}

function renderConcurrentPriceTag(concurrentPrice: number, reviewedPrice: number) {
  if (concurrentPrice < reviewedPrice) {
    return <Tag color="orange">Дешевле: {concurrentPrice.toLocaleString('ru-RU')} ₣</Tag>
  }
  return <Tag color="green">{concurrentPrice.toLocaleString('ru-RU')} ₣</Tag>
}

export function MarketplaceModerationPage() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState('complaints')
  const [selectedPair, setSelectedPair] = useState<SelectedPair | null>(null)
  const [banOpen, setBanOpen] = useState(false)
  const [banReason, setBanReason] = useState(DEFAULT_BAN_REASON)
  const [unbanTg, setUnbanTg] = useState('')
  const [hideCleared, setHideCleared] = useState(false)
  const [markClearOpen, setMarkClearOpen] = useState(false)
  const [markClearFor, setMarkClearFor] = useState<PairAnalysisDto | null>(null)
  const [markNote, setMarkNote] = useState('')
  const [historyPage, setHistoryPage] = useState(0)
  const historyPageSize = 20
  const [complaintsPage, setComplaintsPage] = useState(0)
  const complaintsPageSize = 20
  const [usersComplaintsPage, setUsersComplaintsPage] = useState(0)
  const usersComplaintsPageSize = 20
  const [minComplaints, setMinComplaints] = useState(1)
  const [sanctionOpen, setSanctionOpen] = useState(false)
  const [selectedTransaction, setSelectedTransaction] = useState<ComplainedTransactionDto | null>(null)
  const [banUserTarget, setBanUserTarget] = useState<UserByComplaintsDto | null>(null)
  const [sanctionForm] = Form.useForm<SanctionFormValues>()
  const [banUserForm] = Form.useForm<BanUserFormValues>()

  const watchedSellerFine = Form.useWatch('sellerFine', sanctionForm) ?? 0
  const watchedBuyerFine = Form.useWatch('buyerFine', sanctionForm) ?? 0
  const watchedComplainantReward = Form.useWatch('complainantReward', sanctionForm) ?? 0
  const watchedBanSellerEnabled = Form.useWatch('banSellerEnabled', sanctionForm) ?? false
  const watchedBanBuyerEnabled = Form.useWatch('banBuyerEnabled', sanctionForm) ?? false
  const watchedBanSellerDays = Form.useWatch('banSellerDays', sanctionForm) ?? 3
  const watchedBanBuyerDays = Form.useWatch('banBuyerDays', sanctionForm) ?? 3
  const watchedBanMode = Form.useWatch('mode', banUserForm) ?? '3'

  const economyQ = useQuery({
    queryKey: ['admin', 'economy-config'],
    queryFn: listEconomyConfig,
  })

  const analysisQ = useQuery({
    queryKey: ['admin', 'marketplace', 'pair-analysis'],
    queryFn: getPairAnalysis,
  })

  const tradesQ = useQuery({
    queryKey: ['admin', 'marketplace', 'pair-trades', selectedPair?.userA, selectedPair?.userB],
    queryFn: () => getPairTrades(selectedPair!.userA, selectedPair!.userB),
    enabled: selectedPair != null,
  })

  const banPreviewQ = useQuery({
    queryKey: ['admin', 'marketplace', 'ban-pair-preview', selectedPair?.userA, selectedPair?.userB],
    queryFn: () => getBanPairPreview(selectedPair!.userA, selectedPair!.userB),
    enabled: banOpen && selectedPair != null,
  })

  const historyQ = useQuery({
    queryKey: ['admin', 'marketplace', 'ban-pair-history', historyPage],
    queryFn: () => getBanPairHistory({ page: historyPage, size: historyPageSize }),
  })

  const complainedTransactionsQ = useQuery({
    queryKey: ['admin', 'marketplace', 'complained-transactions', complaintsPage, minComplaints],
    queryFn: () =>
      getComplainedTransactions({
        page: complaintsPage,
        size: complaintsPageSize,
        minComplaints,
        sortBy: 'complaints_desc',
      }),
  })

  const usersByComplaintsQ = useQuery({
    queryKey: ['admin', 'users-by-complaints', usersComplaintsPage],
    queryFn: () =>
      getUsersByComplaints({
        page: usersComplaintsPage,
        size: usersComplaintsPageSize,
        sortBy: 'total_complaints_desc',
      }),
  })

  const transactionComplaintsQ = useQuery({
    queryKey: ['admin', 'marketplace', 'transaction-complaints', selectedTransaction?.listingId],
    queryFn: () => getTransactionComplaints(selectedTransaction!.listingId),
    enabled: sanctionOpen && selectedTransaction != null,
  })

  const banMut = useMutation({
    mutationFn: banPair,
    onSuccess: (data) => {
      message.success('Pair sanctions applied')
      setBanOpen(false)
      setBanResultPreview(data)
      void qc.invalidateQueries({ queryKey: ['admin', 'marketplace'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'users-by-complaints'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const unbanMut = useMutation({
    mutationFn: (telegramId: number) => unbanMarketplace(telegramId),
    onSuccess: () => {
      message.success('Marketplace unbanned for user')
      setUnbanTg('')
      void qc.invalidateQueries({ queryKey: ['admin', 'marketplace'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'users-by-complaints'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const banUserMut = useMutation({
    mutationFn: (args: { telegramId: number; request: BanUserRequest }) => banMarketplaceUser(args.telegramId, args.request),
    onSuccess: () => {
      message.success('Бан применён')
      setBanUserTarget(null)
      void qc.invalidateQueries({ queryKey: ['admin', 'marketplace'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'users-by-complaints'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const sanctionMut = useMutation({
    mutationFn: (args: { listingId: number; request: SanctionTransactionRequest }) =>
      sanctionTransaction(args.listingId, args.request),
    onSuccess: (result) => {
      setSanctionOpen(false)
      setSelectedTransaction(null)
      Modal.success({
        title: 'Санкция применена',
        content: (
          <Space direction="vertical" size="small">
            <Typography.Text>
              Сделка #{result.listingId}: продавцу −{result.sellerFined.toLocaleString('ru-RU')} ₣, покупателю −
              {result.buyerFined.toLocaleString('ru-RU')} ₣
            </Typography.Text>
            <Typography.Text>
              Награждено жалобщиков: {result.complainantsRewarded}, всего +{result.totalRewardPaid.toLocaleString('ru-RU')}{' '}
              ₣
            </Typography.Text>
            <Typography.Text>
              Балансы после санкции: продавец {result.sellerNewBalance.toLocaleString('ru-RU')} ₣, покупатель{' '}
              {result.buyerNewBalance.toLocaleString('ru-RU')} ₣
            </Typography.Text>
            <Typography.Text>
              Бан продавца:{' '}
              {result.sellerBannedUntil ? dayjs(result.sellerBannedUntil).format('DD.MM.YYYY HH:mm') : 'нет'}; бан
              покупателя: {result.buyerBannedUntil ? dayjs(result.buyerBannedUntil).format('DD.MM.YYYY HH:mm') : 'нет'}
            </Typography.Text>
          </Space>
        ),
      })
      void qc.invalidateQueries({ queryKey: ['admin', 'marketplace'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'users-by-complaints'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const markClearMut = useMutation({
    mutationFn: (args: { telegramIdA: number; telegramIdB: number; note?: string }) => markPairCleared(args),
    onSuccess: () => {
      message.success('Пара отмечена как проверенная')
      setMarkClearOpen(false)
      setMarkClearFor(null)
      setMarkNote('')
      void qc.invalidateQueries({ queryKey: ['admin', 'marketplace'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const unmarkClearMut = useMutation({
    mutationFn: (pair: [number, number]) => unmarkPairCleared(pair[0], pair[1]),
    onSuccess: () => {
      message.success('Пометка снята')
      void qc.invalidateQueries({ queryKey: ['admin', 'marketplace'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const [banResultPreview, setBanResultPreview] = useState<BanPairResultDto | null>(null)
  const commissionPercent = parseMarketplaceCommissionPercent(economyQ.data)
  const complainantsCountForSelected = transactionComplaintsQ.data?.complaints.length ?? selectedTransaction?.complaintsCount ?? 0
  const selectedTransactionCommission = selectedTransaction
    ? Math.floor((selectedTransaction.price * commissionPercent) / 100)
    : 0
  const sellerFineForRewardPreview = Math.max(0, Math.trunc(watchedSellerFine))
  const totalRewardPreview = Math.max(0, Math.trunc(watchedComplainantReward)) * complainantsCountForSelected

  const openSanctionModal = (tx: ComplainedTransactionDto) => {
    const commission = Math.floor((tx.price * commissionPercent) / 100)
    const sellerReceived = tx.price - commission
    const complaintsCount = Math.max(1, tx.complaintsCount)
    setSelectedTransaction(tx)
    setSanctionOpen(true)
    sanctionForm.setFieldsValue({
      reason: DEFAULT_SANCTION_REASON,
      sellerFine: sellerReceived,
      buyerFine: 0,
      complainantReward: Math.floor(sellerReceived / complaintsCount),
      banSellerEnabled: false,
      banSellerDays: 3,
      banBuyerEnabled: false,
      banBuyerDays: 3,
    })
  }

  const applySanction = async () => {
    if (selectedTransaction == null) return
    const values = await sanctionForm.validateFields()
    const request: SanctionTransactionRequest = {
      reason: values.reason.trim(),
      sellerFine: Math.max(0, Math.trunc(values.sellerFine)),
      buyerFine: Math.max(0, Math.trunc(values.buyerFine)),
      complainantReward: Math.max(0, Math.trunc(values.complainantReward)),
      banSeller: values.banSellerEnabled ? { days: Math.max(1, Math.trunc(values.banSellerDays)) } : null,
      banBuyer: values.banBuyerEnabled ? { days: Math.max(1, Math.trunc(values.banBuyerDays)) } : null,
    }
    sanctionMut.mutate({
      listingId: selectedTransaction.listingId,
      request,
    })
  }

  const applyUserBan = async () => {
    if (banUserTarget == null) return
    const values = await banUserForm.validateFields()
    let days: number | null
    switch (values.mode) {
      case '3':
      case '7':
      case '30':
        days = Number.parseInt(values.mode, 10)
        break
      case 'permanent':
        days = null
        break
      case 'custom':
        days = Math.max(1, Math.trunc(values.customDays))
        break
      default:
        days = 3
    }
    banUserMut.mutate({
      telegramId: banUserTarget.telegramId,
      request: { days },
    })
  }

  const analysisRows = useMemo(() => {
    const d = analysisQ.data ?? []
    if (!hideCleared) {
      return d
    }
    return d.filter((r) => !(r.cleared ?? false))
  }, [analysisQ.data, hideCleared])

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
        title: 'Проверено',
        key: 'cl',
        width: 110,
        render: (_: unknown, r: PairAnalysisDto) => {
          if (!(r.cleared ?? false)) {
            return '—'
          }
          const t = r.clearedAt
          return (
            <span title={t != null ? new Date(t).toLocaleString('ru-RU') : undefined}>
              <Tag color="success">Да</Tag>
            </span>
          )
        },
      },
      {
        title: 'Actions',
        key: 'act',
        width: 300,
        render: (_: unknown, r: PairAnalysisDto) => (
          <Space
            size="small"
            wrap
            onClick={(e) => {
              e.stopPropagation()
            }}
          >
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
            {r.cleared ?? false ? (
              <Popconfirm
                title="Снять пометку «проверено» с этой пары?"
                okButtonProps={{ loading: unmarkClearMut.isPending }}
                onConfirm={() => {
                  unmarkClearMut.mutate(sortPair(r))
                }}
              >
                <Button type="link" size="small">
                  Снять
                </Button>
              </Popconfirm>
            ) : (
              <Button
                type="link"
                size="small"
                onClick={() => {
                  setMarkClearFor(r)
                  setMarkNote('')
                  setMarkClearOpen(true)
                }}
              >
                Пометить
              </Button>
            )}
          </Space>
        ),
      },
    ],
    [unmarkClearMut.isPending],
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

  const banPreviewUserColumns = useMemo(
    () => [
      {
        title: 'Роль',
        key: 'role',
        width: 90,
        render: (_: unknown, r: { label: string; p: BanPairPreviewUserDto }) => r.label,
      },
      {
        title: 'Telegram',
        key: 'tg',
        render: (_: unknown, r: { p: BanPairPreviewUserDto }) => r.p.telegramId,
      },
      {
        title: 'Имя',
        key: 'dn',
        render: (_: unknown, r: { p: BanPairPreviewUserDto }) => r.p.displayName,
      },
      {
        title: 'Баланс, ₣',
        key: 'bal',
        align: 'right' as const,
        render: (_: unknown, r: { p: BanPairPreviewUserDto }) => r.p.balance.toLocaleString('ru-RU'),
      },
      {
        title: 'Изъять, ₣',
        key: 'take',
        align: 'right' as const,
        render: (_: unknown, r: { p: BanPairPreviewUserDto }) => r.p.fantikiToConfiscate.toLocaleString('ru-RU'),
      },
      {
        title: 'Станет, ₣',
        key: 'after',
        align: 'right' as const,
        render: (_: unknown, r: { p: BanPairPreviewUserDto }) => r.p.balanceAfter.toLocaleString('ru-RU'),
      },
      {
        title: 'Карт',
        key: 'cards',
        width: 70,
        align: 'right' as const,
        render: (_: unknown, r: { p: BanPairPreviewUserDto }) => r.p.cardsToConfiscate.length,
      },
    ],
    [],
  )

  const complainedTransactionsColumns = useMemo(
    () => [
      {
        title: 'Карта',
        key: 'card',
        render: (_: unknown, r: ComplainedTransactionDto) => (
          <Space>
            <Typography.Text>{r.playerName}</Typography.Text>
            <Tag color={rarityColor(r.rarity)}>{r.rarity}</Tag>
          </Space>
        ),
      },
      {
        title: 'Цена',
        key: 'price',
        align: 'right' as const,
        render: (_: unknown, r: ComplainedTransactionDto) => `${r.price.toLocaleString('ru-RU')} ₣`,
      },
      {
        title: 'Создан',
        key: 'createdAt',
        render: (_: unknown, r: ComplainedTransactionDto) => dayjs(r.createdAt).format('DD.MM.YYYY HH:mm'),
      },
      {
        title: 'Продавец',
        key: 'seller',
        render: (_: unknown, r: ComplainedTransactionDto) => `${r.seller.displayName} (${r.seller.telegramId})`,
      },
      {
        title: 'Покупатель',
        key: 'buyer',
        render: (_: unknown, r: ComplainedTransactionDto) => `${r.buyer.displayName} (${r.buyer.telegramId})`,
      },
      {
        title: 'Дата',
        key: 'soldAt',
        render: (_: unknown, r: ComplainedTransactionDto) => dayjs(r.soldAt).format('DD.MM.YYYY HH:mm'),
      },
      {
        title: 'Жалобы',
        key: 'complaints',
        align: 'center' as const,
        render: (_: unknown, r: ComplainedTransactionDto) =>
          r.complaintsCount >= 3 ? <Tag color="red">{r.complaintsCount}</Tag> : r.complaintsCount,
      },
      {
        title: 'Статус',
        key: 'status',
        render: (_: unknown, r: ComplainedTransactionDto) =>
          r.sanctioned ? <Tag color="red">Санкционирована</Tag> : <Tag color="orange">Ожидает</Tag>,
      },
      {
        title: 'Действие',
        key: 'action',
        render: (_: unknown, r: ComplainedTransactionDto) => (
          <Button type="primary" size="small" disabled={r.sanctioned} onClick={() => openSanctionModal(r)}>
            Санкционировать
          </Button>
        ),
      },
    ],
    [commissionPercent],
  )

  const usersByComplaintsColumns = useMemo(
    () => [
      {
        title: 'Пользователь',
        key: 'user',
        render: (_: unknown, r: UserByComplaintsDto) => `${r.displayName} (${r.telegramId})`,
      },
      {
        title: 'Всего жалоб',
        dataIndex: 'totalComplaints' as const,
        key: 'totalComplaints',
        align: 'right' as const,
      },
      {
        title: 'Сделок с жалобами',
        dataIndex: 'transactionsWithComplaints' as const,
        key: 'transactionsWithComplaints',
        align: 'right' as const,
      },
      {
        title: 'Среднее жалоб/сделка',
        key: 'avg',
        align: 'right' as const,
        render: (_: unknown, r: UserByComplaintsDto) => r.avgComplaintsPerTransaction.toFixed(1),
      },
      {
        title: 'Санкционировано',
        dataIndex: 'sanctionedTransactions' as const,
        key: 'sanctionedTransactions',
        align: 'right' as const,
      },
      {
        title: 'Статус бана',
        key: 'banStatus',
        render: (_: unknown, r: UserByComplaintsDto) => renderBanStatus(r),
      },
      {
        title: 'Действие',
        key: 'action',
        render: (_: unknown, r: UserByComplaintsDto) =>
          hasBanMarker(r) ? (
            <Popconfirm
              title="Снять бан маркетплейса?"
              okButtonProps={{ loading: unbanMut.isPending }}
              onConfirm={() => unbanMut.mutate(r.telegramId)}
            >
              <Button size="small">Разбанить</Button>
            </Popconfirm>
          ) : (
            <Button
              size="small"
              type="primary"
              onClick={() => {
                setBanUserTarget(r)
                banUserForm.setFieldsValue({ mode: '3', customDays: 3 })
              }}
            >
              Забанить
            </Button>
          ),
      },
    ],
    [banUserForm, unbanMut.isPending],
  )

  const transactionComplaintsColumns = useMemo(
    () => [
      {
        title: 'Имя',
        dataIndex: 'displayName' as const,
        key: 'displayName',
      },
      {
        title: 'Telegram ID',
        dataIndex: 'telegramId' as const,
        key: 'telegramId',
      },
      {
        title: 'Дата жалобы',
        key: 'complainedAt',
        render: (_: unknown, r: TransactionComplaintDetailDto) => dayjs(r.complainedAt).format('DD.MM.YYYY HH:mm'),
      },
    ],
    [],
  )

  const concurrentListingsColumns = useMemo(
    () => [
      {
        title: 'Listing ID',
        dataIndex: 'listingId' as const,
        key: 'listingId',
      },
      {
        title: 'Продавец',
        key: 'seller',
        render: (_: unknown, r: ConcurrentListingDto) => `${r.sellerDisplayName} (${r.sellerTelegramId})`,
      },
      {
        title: 'Цена ₣',
        key: 'price',
        render: (_: unknown, r: ConcurrentListingDto) => renderConcurrentPriceTag(r.price, selectedTransaction?.price ?? r.price),
      },
      {
        title: 'Создан',
        key: 'createdAt',
        render: (_: unknown, r: ConcurrentListingDto) => dayjs(r.createdAt).format('DD.MM.YYYY HH:mm'),
      },
      {
        title: 'Статус',
        key: 'status',
        render: (_: unknown, r: ConcurrentListingDto) =>
          r.active ? (
            <Tag color="processing">Активен</Tag>
          ) : (
            <Tag color="default">Продан: {r.soldAt ? dayjs(r.soldAt).format('DD.MM.YYYY HH:mm') : '—'}</Tag>
          ),
      },
    ],
    [selectedTransaction?.price],
  )

  const sanctionHistoryColumns = useMemo(
    () => [
      {
        title: 'Когда',
        key: 'when',
        width: 170,
        render: (_: unknown, r: PairSanctionHistoryItemDto) =>
          dayjs(r.createdAt).format('YYYY-MM-DD HH:mm'),
      },
      {
        title: 'Участник (низ. id в БД)',
        key: 'ul',
        width: 200,
        render: (_: unknown, r: PairSanctionHistoryItemDto) => (
          <span>
            {r.userLowDisplayName} — TG {r.userLowTelegramId}
          </span>
        ),
      },
      {
        title: 'Участник (верх. id в БД)',
        key: 'uh',
        width: 200,
        render: (_: unknown, r: PairSanctionHistoryItemDto) => (
          <span>
            {r.userHighDisplayName} — TG {r.userHighTelegramId}
          </span>
        ),
      },
      {
        title: '₣ (низ.) / карт',
        key: 'fl',
        width: 110,
        align: 'right' as const,
        render: (_: unknown, r: PairSanctionHistoryItemDto) =>
          `${r.fantikiTakenLow.toLocaleString('ru-RU')} / ${r.cardsCountLow}`,
      },
      {
        title: '₣ (верх.) / карт',
        key: 'fh',
        width: 110,
        align: 'right' as const,
        render: (_: unknown, r: PairSanctionHistoryItemDto) =>
          `${r.fantikiTakenHigh.toLocaleString('ru-RU')} / ${r.cardsCountHigh}`,
      },
      {
        title: 'Причина',
        dataIndex: 'reason' as const,
        key: 're',
        ellipsis: true,
      },
    ],
    [],
  )

  const tradeColumns = useMemo(
    () => [
      {
        title: 'Created at',
        key: 'createdAt',
        width: 180,
        render: (_: unknown, t: PairTradeDto) => dayjs(t.createdAt).format('YYYY-MM-DD HH:mm'),
      },
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
      {
        title: 'Жалобы',
        dataIndex: 'complaintsCount' as const,
        key: 'complaints',
        align: 'center' as const,
        render: (v: number) => (v >= 3 ? <Tag color="red">{v}</Tag> : v),
      },
    ],
    [],
  )

  return (
    <div>
      <Typography.Title level={3}>Marketplace moderation</Typography.Title>
      <Typography.Paragraph type="secondary">
        Pair analysis of completed marketplace sales, per-pair trade history, apply pair sanctions, and unban
        (legacy) marketplace access by Telegram user id.
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
              key: 'complaints',
              label: 'Жалобы',
              children: (
                <Space direction="vertical" style={{ display: 'flex' }} size="middle">
                  <Space>
                    <Typography.Text>Мин. жалоб:</Typography.Text>
                    <InputNumber
                      min={1}
                      value={minComplaints}
                      onChange={(v) => {
                        setMinComplaints(v == null ? 1 : Math.max(1, Math.trunc(v)))
                        setComplaintsPage(0)
                      }}
                    />
                  </Space>
                  <Table<ComplainedTransactionDto>
                    rowKey="listingId"
                    loading={complainedTransactionsQ.isLoading}
                    dataSource={complainedTransactionsQ.data?.content ?? []}
                    columns={complainedTransactionsColumns}
                    pagination={{
                      current: complaintsPage + 1,
                      pageSize: complaintsPageSize,
                      total: complainedTransactionsQ.data?.totalElements ?? 0,
                      showSizeChanger: false,
                      onChange: (page) => setComplaintsPage(page - 1),
                    }}
                  />
                </Space>
              ),
            },
            {
              key: 'users-by-complaints',
              label: 'Игроки по жалобам',
              children: (
                <Table<UserByComplaintsDto>
                  rowKey="telegramId"
                  loading={usersByComplaintsQ.isLoading}
                  dataSource={usersByComplaintsQ.data?.content ?? []}
                  columns={usersByComplaintsColumns}
                  pagination={{
                    current: usersComplaintsPage + 1,
                    pageSize: usersComplaintsPageSize,
                    total: usersByComplaintsQ.data?.totalElements ?? 0,
                    showSizeChanger: false,
                    onChange: (page) => setUsersComplaintsPage(page - 1),
                  }}
                />
              ),
            },
            {
              key: 'analysis',
              label: 'Pair analysis',
              children: (
                <>
                  <div style={{ marginBottom: 12 }}>
                    <Checkbox
                      checked={hideCleared}
                      onChange={(e) => {
                        setHideCleared(e.target.checked)
                      }}
                    >
                      Скрыть проверенные пары
                    </Checkbox>
                  </div>
                  <Table<PairAnalysisDto>
                    rowKey={(r) => pairKey(r.userATelegramId, r.userBTelegramId)}
                    rowClassName={(r) => (r.bidirectional ? 'ant-table-row-bidirectional' : '')}
                    loading={analysisQ.isLoading}
                    dataSource={analysisRows}
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
                </>
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
            {
              key: 'sanction-history',
              label: 'История санкций',
              children: (
                <Table<PairSanctionHistoryItemDto>
                  rowKey="id"
                  size="small"
                  loading={historyQ.isLoading}
                  dataSource={historyQ.data?.content ?? []}
                  columns={sanctionHistoryColumns}
                  pagination={{
                    current: historyPage + 1,
                    pageSize: historyPageSize,
                    total: historyQ.data?.totalElements ?? 0,
                    showSizeChanger: false,
                    onChange: (p) => {
                      setHistoryPage(p - 1)
                    },
                  }}
                />
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
        title="Подтверждение парных санкций (перелив)"
        okText="Подтвердить"
        cancelText="Отмена"
        okButtonProps={{
          danger: true,
          loading: banMut.isPending,
          disabled: !banPreviewQ.isSuccess,
        }}
        onCancel={() => {
          setBanOpen(false)
        }}
        onOk={() => {
          if (selectedPair == null) return
          if (!banPreviewQ.isSuccess) {
            message.error('Дождитесь превью изъятий')
            return
          }
          const r = banReason.trim()
          if (r.length === 0) {
            message.error('Укажите причину')
            return
          }
          banMut.mutate({
            telegramIdA: selectedPair.userA,
            telegramIdB: selectedPair.userB,
            reason: r,
          })
        }}
        width={860}
      >
        {selectedPair && (
          <Typography.Paragraph>
            Возвращается чистая сумма продавцу по сделкам внутри пары; снимаются карты, если покупатель всё ещё владеет
            ими. Активные лоты и доступ к маркету отдельно не отключаются.
            <br />
            Telegram: <strong>{selectedPair.userA}</strong> и <strong>{selectedPair.userB}</strong>.
          </Typography.Paragraph>
        )}
        {banPreviewQ.isLoading && <Typography.Text type="secondary">Загрузка превью…</Typography.Text>}
        {banPreviewQ.isError && (
          <Typography.Text type="danger">{(banPreviewQ.error as Error).message}</Typography.Text>
        )}
        {banPreviewQ.data && (
          <Table<{ label: string; p: BanPairPreviewUserDto; key: string }>
            style={{ marginBottom: 16, maxWidth: 820 }}
            size="small"
            pagination={false}
            rowKey="key"
            columns={banPreviewUserColumns}
            dataSource={[
              { key: 'a', label: 'User A (запрос)', p: banPreviewQ.data.userA },
              { key: 'b', label: 'User B (запрос)', p: banPreviewQ.data.userB },
            ]}
          />
        )}
        <Typography.Text strong type="secondary" style={{ display: 'block', marginBottom: 4 }}>
          Причина
        </Typography.Text>
        <Input.TextArea rows={4} value={banReason} onChange={(e) => setBanReason(e.target.value)} />
      </Modal>

      <Modal
        open={sanctionOpen && selectedTransaction != null}
        title="Санкция по сделке"
        onCancel={() => {
          setSanctionOpen(false)
          setSelectedTransaction(null)
        }}
        width={980}
        footer={[
          <Button
            key="cancel"
            onClick={() => {
              setSanctionOpen(false)
              setSelectedTransaction(null)
            }}
          >
            Отмена
          </Button>,
          <Popconfirm
            key="apply"
            title={
              <div>
                <div>Применить санкцию?</div>
                <div>Штраф продавцу: -{Math.max(0, Math.trunc(watchedSellerFine)).toLocaleString('ru-RU')} ₣</div>
                <div>Штраф покупателю: -{Math.max(0, Math.trunc(watchedBuyerFine)).toLocaleString('ru-RU')} ₣</div>
                <div>
                  Награда жалобщикам: {Math.max(0, Math.trunc(watchedComplainantReward)).toLocaleString('ru-RU')} ×{' '}
                  {complainantsCountForSelected} = {totalRewardPreview.toLocaleString('ru-RU')} ₣
                </div>
                <div>
                  Бан продавца:{' '}
                  {watchedBanSellerEnabled ? `${Math.max(1, Math.trunc(watchedBanSellerDays))} дн.` : 'Нет'}
                </div>
                <div>
                  Бан покупателя:{' '}
                  {watchedBanBuyerEnabled ? `${Math.max(1, Math.trunc(watchedBanBuyerDays))} дн.` : 'Нет'}
                </div>
              </div>
            }
            okText="Применить"
            cancelText="Отмена"
            okButtonProps={{ danger: true, loading: sanctionMut.isPending }}
            onConfirm={() => {
              void applySanction()
            }}
            disabled={
              selectedTransaction == null ||
              transactionComplaintsQ.isLoading ||
              transactionComplaintsQ.isError ||
              sanctionMut.isPending
            }
          >
            <Button
              key="confirm"
              type="primary"
              danger
              loading={sanctionMut.isPending}
              disabled={
                selectedTransaction == null ||
                transactionComplaintsQ.isLoading ||
                transactionComplaintsQ.isError ||
                sanctionMut.isPending
              }
            >
              Применить санкцию
            </Button>
          </Popconfirm>,
        ]}
      >
        {selectedTransaction && (
          <Space direction="vertical" style={{ display: 'flex' }} size="middle">
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="Карта">
                {selectedTransaction.playerName} <Tag color={rarityColor(selectedTransaction.rarity)}>{selectedTransaction.rarity}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Цена">{selectedTransaction.price.toLocaleString('ru-RU')} ₣</Descriptions.Item>
              <Descriptions.Item label="Создан" span={2}>
                {dayjs(selectedTransaction.createdAt).format('DD.MM.YYYY HH:mm')}
              </Descriptions.Item>
              <Descriptions.Item label="Продавец">
                {selectedTransaction.seller.displayName} ({selectedTransaction.seller.telegramId})
              </Descriptions.Item>
              <Descriptions.Item label="Покупатель">
                {selectedTransaction.buyer.displayName} ({selectedTransaction.buyer.telegramId})
              </Descriptions.Item>
              <Descriptions.Item label="Дата сделки">
                {dayjs(selectedTransaction.soldAt).format('DD.MM.YYYY HH:mm')}
              </Descriptions.Item>
              <Descriptions.Item label="Жалоб">{selectedTransaction.complaintsCount}</Descriptions.Item>
              <Descriptions.Item label="Время на рынке" span={2}>
                {formatMarketDuration(selectedTransaction.createdAt, selectedTransaction.soldAt)}
              </Descriptions.Item>
            </Descriptions>

            {transactionComplaintsQ.isLoading && <Typography.Text type="secondary">Загрузка жалоб…</Typography.Text>}
            {transactionComplaintsQ.isError && (
              <Typography.Text type="danger">{(transactionComplaintsQ.error as Error).message}</Typography.Text>
            )}
            {transactionComplaintsQ.data && (
              <Table<TransactionComplaintDetailDto>
                rowKey={(r) => `${r.userId}-${r.complainedAt}`}
                size="small"
                pagination={false}
                columns={transactionComplaintsColumns}
                dataSource={transactionComplaintsQ.data.complaints}
              />
            )}
            {transactionComplaintsQ.data && (
              <Space direction="vertical" style={{ display: 'flex' }} size="small">
                <Typography.Text strong>Рынок на момент выкупа</Typography.Text>
                {transactionComplaintsQ.data.marketContext.concurrentSameTemplate.length === 0 &&
                transactionComplaintsQ.data.marketContext.concurrentSamePlayerRarity.length === 0 ? (
                  <Alert type="success" message="Одновременных листингов не найдено" showIcon />
                ) : (
                  <>
                    <Typography.Text strong>Те же перки</Typography.Text>
                    <Table<ConcurrentListingDto>
                      rowKey="listingId"
                      size="small"
                      pagination={false}
                      columns={concurrentListingsColumns}
                      dataSource={transactionComplaintsQ.data.marketContext.concurrentSameTemplate}
                    />
                    <Typography.Text strong>Та же редкость (другие перки)</Typography.Text>
                    <Table<ConcurrentListingDto>
                      rowKey="listingId"
                      size="small"
                      pagination={false}
                      columns={concurrentListingsColumns}
                      dataSource={transactionComplaintsQ.data.marketContext.concurrentSamePlayerRarity}
                    />
                  </>
                )}
                <Typography.Text type="secondary">
                  Листинги той же fantasy-player, но другой редкости, не показываются.
                </Typography.Text>
              </Space>
            )}

            <Form<SanctionFormValues>
              form={sanctionForm}
              layout="vertical"
              initialValues={{
                reason: DEFAULT_SANCTION_REASON,
                sellerFine: 0,
                buyerFine: 0,
                complainantReward: 0,
                banSellerEnabled: false,
                banSellerDays: 3,
                banBuyerEnabled: false,
                banBuyerDays: 3,
              }}
            >
              <Form.Item
                label="Причина"
                name="reason"
                rules={[
                  { required: true, message: 'Укажите причину' },
                  {
                    validator: (_, value: string) =>
                      value?.trim()?.length > 0 ? Promise.resolve() : Promise.reject(new Error('Укажите причину')),
                  },
                ]}
              >
                <Input.TextArea rows={3} />
              </Form.Item>
              <Space align="start" wrap>
                <Form.Item
                  label="Штраф продавцу"
                  name="sellerFine"
                  rules={[{ required: true, type: 'number', min: 0, message: '0 или больше' }]}
                >
                  <InputNumber min={0} precision={0} />
                </Form.Item>
                <Form.Item
                  label="Штраф покупателю"
                  name="buyerFine"
                  rules={[{ required: true, type: 'number', min: 0, message: '0 или больше' }]}
                >
                  <InputNumber min={0} precision={0} />
                </Form.Item>
                <Form.Item
                  label="Награда жалобщику"
                  name="complainantReward"
                  rules={[{ required: true, type: 'number', min: 0, message: '0 или больше' }]}
                >
                  <InputNumber min={0} precision={0} />
                </Form.Item>
              </Space>
              <Space align="start" wrap>
                <Space align="center">
                  <Form.Item name="banSellerEnabled" valuePropName="checked" style={{ marginBottom: 0 }}>
                    <Checkbox>Бан продавца</Checkbox>
                  </Form.Item>
                  <Form.Item name="banSellerDays" style={{ marginBottom: 0 }}>
                    <InputNumber min={1} precision={0} disabled={!watchedBanSellerEnabled} addonAfter="дн." />
                  </Form.Item>
                </Space>
                <Space align="center">
                  <Form.Item name="banBuyerEnabled" valuePropName="checked" style={{ marginBottom: 0 }}>
                    <Checkbox>Бан покупателя</Checkbox>
                  </Form.Item>
                  <Form.Item name="banBuyerDays" style={{ marginBottom: 0 }}>
                    <InputNumber min={1} precision={0} disabled={!watchedBanBuyerEnabled} addonAfter="дн." />
                  </Form.Item>
                </Space>
              </Space>
            </Form>

            {complainantsCountForSelected > 0 &&
              totalRewardPreview > sellerFineForRewardPreview && (
                <Alert
                  type="warning"
                  showIcon
                  message="Суммарная награда жалобщикам превышает штраф продавцу"
                />
              )}
            <Typography.Text type="secondary">
              Комиссия сделки ({commissionPercent}%): {selectedTransactionCommission.toLocaleString('ru-RU')} ₣ ·
              Доступно из штрафа продавцу: {sellerFineForRewardPreview.toLocaleString('ru-RU')} ₣
            </Typography.Text>
          </Space>
        )}
      </Modal>

      <Modal
        open={banResultPreview != null}
        title="Sanctions completed"
        footer={<Button onClick={() => setBanResultPreview(null)}>Close</Button>}
        onCancel={() => setBanResultPreview(null)}
      >
        {banResultPreview && (
          <div style={{ fontFamily: 'monospace', fontSize: 12, whiteSpace: 'pre-wrap' }}>
            {JSON.stringify(banResultPreview, null, 2)}
          </div>
        )}
      </Modal>

      <Modal
        open={markClearOpen}
        title="Пометить пару как проверенную (не перелив)"
        okText="Сохранить"
        okButtonProps={{ loading: markClearMut.isPending }}
        onCancel={() => {
          setMarkClearOpen(false)
          setMarkClearFor(null)
        }}
        onOk={() => {
          if (markClearFor == null) return
          const [a, b] = sortPair(markClearFor)
          const note = markNote.trim()
          markClearMut.mutate({
            telegramIdA: a,
            telegramIdB: b,
            note: note.length > 0 ? note : undefined,
          })
        }}
      >
        {markClearFor && (
          <>
            <Typography.Paragraph>
              Пара: <strong>{markClearFor.userATelegramId}</strong> /{' '}
              <strong>{markClearFor.userBTelegramId}</strong> (Telegram id).
            </Typography.Paragraph>
            <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
              Комментарий (по желанию)
            </Typography.Text>
            <Input.TextArea
              rows={3}
              value={markNote}
              onChange={(e) => {
                setMarkNote(e.target.value)
              }}
              placeholder="Напр. согласовано, близкие играют"
            />
          </>
        )}
      </Modal>

      <Modal
        open={banUserTarget != null}
        title={banUserTarget ? `Бан маркетплейса: ${banUserTarget.displayName} (${banUserTarget.telegramId})` : 'Бан'}
        okText="Применить"
        cancelText="Отмена"
        okButtonProps={{ danger: true, loading: banUserMut.isPending }}
        onCancel={() => setBanUserTarget(null)}
        onOk={() => {
          void applyUserBan()
        }}
      >
        {banUserTarget && (
          <Form<BanUserFormValues> form={banUserForm} layout="vertical" initialValues={{ mode: '3', customDays: 3 }}>
            <Form.Item label="Текущий статус">{renderBanStatus(banUserTarget)}</Form.Item>
            <Form.Item name="mode" label="Срок бана" rules={[{ required: true }]}>
              <Radio.Group>
                <Space direction="vertical">
                  <Radio value="3">3 дня</Radio>
                  <Radio value="7">7 дней</Radio>
                  <Radio value="30">30 дней</Radio>
                  <Radio value="permanent">Перманент</Radio>
                  <Radio value="custom">Кастомный срок</Radio>
                </Space>
              </Radio.Group>
            </Form.Item>
            {watchedBanMode === 'custom' && (
              <Form.Item
                name="customDays"
                label="Дней"
                rules={[{ required: true, type: 'number', min: 1, message: 'Минимум 1 день' }]}
              >
                <InputNumber min={1} precision={0} />
              </Form.Item>
            )}
            {isBanActive(banUserTarget) && (
              <Alert
                showIcon
                type="warning"
                message="У пользователя уже активен бан. Новый бан перезапишет срок."
              />
            )}
          </Form>
        )}
      </Modal>
    </div>
  )
}
