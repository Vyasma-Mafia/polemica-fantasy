import { App, Button, Form, InputNumber, Select, Space, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { giveCards, listCardTemplates, openPack } from '../api/cards'
import { listCardPacks } from '../api/packs'
import { giveFantiki, takeFantiki } from '../api/users'

export function UserToolsPage() {
  const { message } = App.useApp()
  const [telegramUserId, setTelegramUserId] = useState<number | undefined>()
  const [packId, setPackId] = useState<number | undefined>()
  const [templateIds, setTemplateIds] = useState<number[]>([])
  const [fantikiAmount, setFantikiAmount] = useState<number | undefined>()

  const templatesQ = useQuery({
    queryKey: ['admin', 'card-templates', 'all'],
    queryFn: () => listCardTemplates({}),
  })

  const packsQ = useQuery({
    queryKey: ['admin', 'card-packs', 'all'],
    queryFn: () => listCardPacks(),
  })
  const sortedPacks = useMemo(
    () =>
      [...(packsQ.data ?? [])].sort(
        (a, b) => Number(b.active) - Number(a.active),
      ),
    [packsQ.data],
  )
  const instantPacks = useMemo(
    () => sortedPacks.filter((p) => p.openingMode !== 'CHOOSE'),
    [sortedPacks],
  )

  const giveMut = useMutation({
    mutationFn: ({
      uid,
      ids,
    }: {
      uid: number
      ids: number[]
    }) => giveCards(uid, { cardTemplateIds: ids }),
    onSuccess: (rows) => {
      message.success(`Issued ${rows.length} card(s)`)
    },
    onError: (e: Error) => message.error(e.message),
  })

  const fantikiMut = useMutation({
    mutationFn: ({ uid, amount }: { uid: number; amount: number }) =>
      giveFantiki(uid, amount),
    onSuccess: (profile) => {
      message.success(`Balance: ${profile.fantiki} fantiki`)
    },
    onError: (e: Error) => message.error(e.message),
  })

  const takeFantikiMut = useMutation({
    mutationFn: ({ uid, amount }: { uid: number; amount: number }) =>
      takeFantiki(uid, amount),
    onSuccess: (profile) => {
      message.success(`Balance: ${profile.fantiki} fantiki`)
    },
    onError: (e: Error) => message.error(e.message),
  })

  const openMut = useMutation({
    mutationFn: ({
      uid,
      pid,
    }: {
      uid: number
      pid: number
    }) => openPack(uid, pid),
    onSuccess: (r) => {
      message.success(`Opened pack: ${r.userCards.length} card(s)`)
    },
    onError: (e: Error) => message.error(e.message),
  })

  return (
    <div>
      <Typography.Title level={3}>User tools</Typography.Title>
      <Typography.Paragraph type="secondary">
        Operations by Telegram user id: give specific card templates or open a pack.
      </Typography.Paragraph>

      <Form layout="vertical" style={{ maxWidth: 560 }}>
        <Form.Item label="Telegram user id" required>
          <InputNumber
            min={1}
            style={{ width: '100%' }}
            value={telegramUserId}
            onChange={(v) => setTelegramUserId(v ?? undefined)}
          />
        </Form.Item>

        <Typography.Title level={5}>Fantiki</Typography.Title>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
          Grant or take currency by Telegram user id (user must already exist to take).
        </Typography.Paragraph>
        <Space wrap style={{ marginBottom: 24 }}>
          <InputNumber
            min={1}
            placeholder="Amount"
            style={{ width: 160 }}
            value={fantikiAmount}
            onChange={(v) => setFantikiAmount(v ?? undefined)}
          />
          <Button
            type="primary"
            loading={fantikiMut.isPending}
            disabled={telegramUserId == null || fantikiAmount == null || fantikiAmount < 1}
            onClick={() => {
              if (telegramUserId == null || fantikiAmount == null) return
              fantikiMut.mutate({ uid: telegramUserId, amount: fantikiAmount })
            }}
          >
            Grant fantiki
          </Button>
          <Button
            danger
            loading={takeFantikiMut.isPending}
            disabled={telegramUserId == null || fantikiAmount == null || fantikiAmount < 1}
            onClick={() => {
              if (telegramUserId == null || fantikiAmount == null) return
              takeFantikiMut.mutate({ uid: telegramUserId, amount: fantikiAmount })
            }}
          >
            Take fantiki
          </Button>
        </Space>

        <Typography.Title level={5}>Give cards</Typography.Title>
        <Select
          mode="multiple"
          allowClear
          style={{ width: '100%', marginBottom: 8 }}
          loading={templatesQ.isLoading}
          placeholder="Card templates"
          options={templatesQ.data?.map((t) => ({
            value: t.id,
            label: `#${t.id} fantasy ${t.fantasyPlayerId} ${t.rarity}`,
          }))}
          value={templateIds}
          onChange={setTemplateIds}
        />
        <Button
          type="primary"
          loading={giveMut.isPending}
          disabled={telegramUserId == null || templateIds.length === 0}
          onClick={() => {
            if (telegramUserId == null) return
            giveMut.mutate({ uid: telegramUserId, ids: templateIds })
          }}
        >
          Give cards
        </Button>

        <Typography.Title level={5} style={{ marginTop: 24 }}>
          Open pack
        </Typography.Title>
        <Space wrap>
          <Select
            style={{ minWidth: 280 }}
            allowClear
            placeholder="Pack"
            loading={packsQ.isLoading}
            options={instantPacks.map((p) => ({
              value: p.id,
              label: `#${p.id} ${p.name} (tournament ${p.tournamentId})`,
            }))}
            value={packId}
            onChange={(v) => setPackId(v)}
          />
          <Button
            type="primary"
            loading={openMut.isPending}
            onClick={() => {
              if (telegramUserId == null || packId == null) {
                message.warning('Set Telegram user id and pack')
                return
              }
              openMut.mutate({ uid: telegramUserId, pid: packId })
            }}
          >
            Open pack
          </Button>
        </Space>
      </Form>
    </div>
  )
}
