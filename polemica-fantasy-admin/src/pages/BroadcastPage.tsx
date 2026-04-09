import { App, Alert, Button, Card, Form, Input, Modal, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { TelegramMarkdownV2Preview } from '../components/telegramMarkdownV2Preview'
import { broadcastMessage } from '../api/notifications'
import {
  TELEGRAM_MESSAGE_MAX_LENGTH,
  validateTelegramMarkdownV2,
} from '../lib/validateTelegramMarkdownV2'

const MAX_LEN = TELEGRAM_MESSAGE_MAX_LENGTH

export function BroadcastPage() {
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
    <div>
      <Typography.Title level={3}>Broadcast</Typography.Title>
      <Typography.Paragraph type="secondary">
        Send one message via the bot to all users who have opened the app (every
        row in telegram_user). Text uses{' '}
        <Typography.Link
          href="https://core.telegram.org/bots/api#markdownv2-style"
          target="_blank"
          rel="noreferrer"
        >
          Telegram MarkdownV2
        </Typography.Link>
        . Delivery runs in the background. Requires Telegram notifications
        enabled on the server.
      </Typography.Paragraph>

      <Typography.Paragraph type="secondary" style={{ maxWidth: 640 }}>
        <strong>Links:</strong>{' '}
        <Typography.Text code>[visible text](https://example.com/path)</Typography.Text>
        — use <Typography.Text code>http://</Typography.Text> or{' '}
        <Typography.Text code>https://</Typography.Text> URLs. To show literal
        characters that Markdown treats specially (
        <Typography.Text code>_ * [ ] ( ) ~ ` &gt; # + - = | {'{ }'} . !</Typography.Text>
        ), prefix them with <Typography.Text code>\</Typography.Text> in the
        source.
      </Typography.Paragraph>

      <Form layout="vertical" style={{ maxWidth: 640 }}>
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
        {trimmed.length > 0 ? (
          mdValidation.ok ? (
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
        ) : null}
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
    </div>
  )
}
