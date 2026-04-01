import { App, Button, Form, Input, Modal, Typography } from 'antd'
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { broadcastMessage } from '../api/notifications'

const MAX_LEN = 4096

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
  const canSend =
    trimmed.length > 0 && trimmed.length <= MAX_LEN && !mut.isPending

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
        Send one text message via the bot to all users who have opened the app
        (every row in telegram_user). Delivery runs in the background. Requires
        Telegram notifications enabled on the server.
      </Typography.Paragraph>

      <Form layout="vertical" style={{ maxWidth: 640 }}>
        <Form.Item label="Message" required>
          <Input.TextArea
            rows={8}
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Message text…"
            maxLength={MAX_LEN}
            showCount
          />
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
