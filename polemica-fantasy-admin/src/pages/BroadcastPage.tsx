import { App, Alert, Button, Card, Form, Input, Modal, Select, Tabs, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { TelegramMarkdownV2Preview } from '../components/telegramMarkdownV2Preview'
import { broadcastMessage, sendDirectMessage } from '../api/notifications'
import {
  TELEGRAM_MESSAGE_MAX_LENGTH,
  validateTelegramMarkdownV2,
} from '../lib/validateTelegramMarkdownV2'
import type { AdminUserListItemDto } from '../api/types'
import { listAdminUsers } from '../api/usersList'

const MAX_LEN = TELEGRAM_MESSAGE_MAX_LENGTH

function userLabel(u: AdminUserListItemDto) {
  const name = u.displayName || (u.username ? `@${u.username}` : null)
  return `${name ? `${name} · ` : ''}${u.telegramId}${u.botBlocked ? ' · bot blocked' : ''}`
}

function MarkdownHelp() {
  return (
    <>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 720 }}>
        Text uses{' '}
        <Typography.Link
          href="https://core.telegram.org/bots/api#markdownv2-style"
          target="_blank"
          rel="noreferrer"
        >
          Telegram MarkdownV2
        </Typography.Link>
        . Requires Telegram notifications enabled on the server.
      </Typography.Paragraph>

      <Typography.Paragraph type="secondary" style={{ maxWidth: 720 }}>
        <strong>Links:</strong>{' '}
        <Typography.Text code>[visible text](https://example.com/path)</Typography.Text>
        — use <Typography.Text code>http://</Typography.Text> or{' '}
        <Typography.Text code>https://</Typography.Text> URLs. To show literal
        characters that Markdown treats specially (
        <Typography.Text code>_ * [ ] ( ) ~ ` &gt; # + - = | {'{ }'} . !</Typography.Text>
        ), prefix them with <Typography.Text code>\</Typography.Text> in the
        source.
      </Typography.Paragraph>
    </>
  )
}

function MessageValidationAlert({ text }: { text: string }) {
  const trimmed = text.trim()
  const mdValidation = useMemo(
    () => validateTelegramMarkdownV2(trimmed),
    [trimmed],
  )

  if (trimmed.length === 0) return null

  return mdValidation.ok ? (
    <Alert
      type="success"
      showIcon
      message="Сообщение пройдёт проверку MarkdownV2 (как у Telegram API)."
      style={{ marginBottom: 16 }}
    />
  ) : (
    <Alert
      type="error"
      showIcon
      message="Текст не отправится: ошибка разметки или длины"
      description={
        <ul style={{ marginBottom: 0, paddingLeft: 20 }}>
          {mdValidation.issues.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      }
      style={{ marginBottom: 16 }}
    />
  )
}

function MessagePreview({ text }: { text: string }) {
  const trimmed = text.trim()
  return (
    <Form.Item label="Preview (approximate)">
      <Card size="small" styles={{ body: { background: '#fafafa' } }}>
        {trimmed.length > 0 ? (
          <TelegramMarkdownV2Preview text={text} />
        ) : (
          <Typography.Text type="secondary">
            Type above to see a rough preview of formatting and links.
          </Typography.Text>
        )}
      </Card>
    </Form.Item>
  )
}

export function BroadcastPage() {
  return (
    <div>
      <Typography.Title level={3}>Bot messages</Typography.Title>
      <MarkdownHelp />
      <Tabs
        defaultActiveKey="direct"
        items={[
          { key: 'direct', label: 'Direct message', children: <DirectMessageTab /> },
          { key: 'broadcast', label: 'Broadcast', children: <BroadcastTab /> },
        ]}
      />
    </div>
  )
}

function DirectMessageTab() {
  const { message } = App.useApp()
  const [text, setText] = useState('')
  const [selectedTelegramUserId, setSelectedTelegramUserId] = useState<number>()
  const [userSearch, setUserSearch] = useState('')
  const [userSearchDebounced, setUserSearchDebounced] = useState('')

  useEffect(() => {
    const t = setTimeout(() => {
      setUserSearchDebounced(userSearch.trim())
    }, 300)
    return () => clearTimeout(t)
  }, [userSearch])

  const usersQ = useQuery({
    queryKey: ['admin', 'users', 'direct-message', userSearchDebounced || undefined],
    queryFn: () => listAdminUsers({ q: userSearchDebounced || undefined }),
  })

  const selectedUser = usersQ.data?.find((u) => u.telegramId === selectedTelegramUserId)
  const trimmed = text.trim()
  const mdValidation = useMemo(
    () => validateTelegramMarkdownV2(trimmed),
    [trimmed],
  )

  const mut = useMutation({
    mutationFn: (input: { telegramUserId: number; text: string }) =>
      sendDirectMessage(input.telegramUserId, input.text),
    onSuccess: (data) => {
      if (data.sent) {
        message.success(`Message sent to ${data.telegramUserId}`)
        setText('')
      } else if (data.skippedBlocked) {
        message.warning(`Message was not sent: user ${data.telegramUserId} blocked the bot`)
      } else {
        message.error(`Message was not sent to ${data.telegramUserId}`)
      }
    },
    onError: (e: Error) => message.error(e.message),
  })

  const canSend =
    selectedTelegramUserId != null &&
    trimmed.length > 0 &&
    trimmed.length <= MAX_LEN &&
    mdValidation.ok &&
    !mut.isPending

  const submit = () => {
    if (!canSend || selectedTelegramUserId == null) return
    Modal.confirm({
      title: 'Send direct message?',
      content: `Send this Telegram bot message to ${selectedUser ? userLabel(selectedUser) : selectedTelegramUserId}?`,
      okText: 'Send',
      onOk: () => mut.mutateAsync({ telegramUserId: selectedTelegramUserId, text: trimmed }),
    })
  }

  return (
    <Form layout="vertical" style={{ maxWidth: 720 }}>
      <Typography.Paragraph type="secondary">
        Send one message via the bot to a specific registered user.
      </Typography.Paragraph>
      <Form.Item label="Recipient" required>
        <Select
          showSearch
          allowClear
          filterOption={false}
          placeholder="Search username, Telegram ID, or display name"
          loading={usersQ.isLoading || usersQ.isFetching}
          value={selectedTelegramUserId}
          onChange={(v) => setSelectedTelegramUserId(v ?? undefined)}
          onSearch={setUserSearch}
          options={usersQ.data?.map((u) => ({
            value: u.telegramId,
            label: userLabel(u),
          }))}
        />
      </Form.Item>
      {selectedUser?.botBlocked ? (
        <Alert
          type="warning"
          showIcon
          message="Этот пользователь уже помечен как bot_blocked; сообщение, скорее всего, не будет доставлено."
          style={{ marginBottom: 16 }}
        />
      ) : null}
      <Form.Item label="Message" required>
        <Input.TextArea
          rows={8}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="*Hello* — [Open app](https://…)"
          maxLength={MAX_LEN}
          showCount
        />
      </Form.Item>
      <MessageValidationAlert text={text} />
      <MessagePreview text={text} />
      <Button
        type="primary"
        loading={mut.isPending}
        disabled={!canSend}
        onClick={submit}
      >
        Send to user
      </Button>
    </Form>
  )
}

function BroadcastTab() {
  const { message } = App.useApp()
  const [text, setText] = useState('')

  const mut = useMutation({
    mutationFn: (t: string) => broadcastMessage(t),
    onSuccess: (data) => {
      message.success(
        `Queued for ${data.recipientCount.toLocaleString('ru-RU')} recipient(s). Messages are sent in the background.`,
      )
      setText('')
    },
    onError: (e: Error) => message.error(e.message),
  })

  const trimmed = text.trim()
  const mdValidation = useMemo(
    () => validateTelegramMarkdownV2(trimmed),
    [trimmed],
  )
  const canSend =
    trimmed.length > 0 &&
    trimmed.length <= MAX_LEN &&
    mdValidation.ok &&
    !mut.isPending

  const submit = () => {
    if (!canSend) return
    Modal.confirm({
      title: 'Send broadcast?',
      content:
        'This will queue a Telegram message to every registered user. Continue?',
      okText: 'Send',
      okButtonProps: { danger: true },
      onOk: () => mut.mutateAsync(trimmed),
    })
  }

  return (
    <Form layout="vertical" style={{ maxWidth: 720 }}>
      <Typography.Paragraph type="secondary">
        Send one message via the bot to all users who have opened the app (every
        row in telegram_user). Delivery runs in the background.
      </Typography.Paragraph>

      <Form.Item label="Message" required>
        <Input.TextArea
          rows={8}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="*Hello* — [Open app](https://…)"
          maxLength={MAX_LEN}
          showCount
        />
      </Form.Item>
      <MessageValidationAlert text={text} />
      <MessagePreview text={text} />
      <Button
        type="primary"
        danger
        loading={mut.isPending}
        disabled={!canSend}
        onClick={submit}
      >
        Send to all users
      </Button>
    </Form>
  )
}
