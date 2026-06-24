import { Button, Form, InputNumber, Input, Select, Typography } from 'antd'
import type { AddTournamentPlayerRequest } from '../api/tournamentRequests'
import type { FantasyPlayerAdminDto } from '../api/types'

interface PlayerAddFormValues {
  fantasyPlayerId?: number
  polemicaUserId?: number
  nickname?: string
}

export function PlayerAddForm({
  onSubmit,
  loading,
  players,
  playersLoading,
}: {
  onSubmit: (v: AddTournamentPlayerRequest) => void
  loading?: boolean
  players?: FantasyPlayerAdminDto[]
  playersLoading?: boolean
}) {
  const [form] = Form.useForm<PlayerAddFormValues>()
  const selectedFantasyPlayerId = Form.useWatch('fantasyPlayerId', form)

  return (
    <Form
      form={form}
      layout="vertical"
      onFinish={(values) => {
        if (values.fantasyPlayerId != null) {
          onSubmit({ fantasyPlayerId: values.fantasyPlayerId })
          return
        }
        onSubmit({
          polemicaUserId: values.polemicaUserId,
          nickname: values.nickname?.trim(),
        })
      }}
      initialValues={{
        fantasyPlayerId: undefined,
        polemicaUserId: undefined,
        nickname: '',
      }}
    >
      <Form.Item name="fantasyPlayerId" label="Existing player">
        <Select
          allowClear
          showSearch
          loading={playersLoading}
          placeholder="Search existing fantasy_player"
          filterOption={(input, option) =>
            String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
          }
          options={(players ?? []).map((player) => ({
            value: player.id,
            label: `#${player.id} ${player.nickname} · Polemica ${player.polemicaUserId}`,
          }))}
        />
      </Form.Item>
      <Typography.Paragraph type="secondary">
        Leave existing player empty to create or reuse by Polemica user id.
      </Typography.Paragraph>
      <Form.Item
        name="polemicaUserId"
        label="Polemica user id"
        rules={[
          ({ getFieldValue }) => ({
            validator(_, value) {
              if (getFieldValue('fantasyPlayerId') != null || value != null) {
                return Promise.resolve()
              }
              return Promise.reject(new Error('Polemica user id is required'))
            },
          }),
        ]}
      >
        <InputNumber
          style={{ width: '100%' }}
          min={1}
          disabled={selectedFantasyPlayerId != null}
        />
      </Form.Item>
      <Form.Item
        name="nickname"
        label="Nickname"
        rules={[
          ({ getFieldValue }) => ({
            validator(_, value) {
              if (
                getFieldValue('fantasyPlayerId') != null ||
                String(value ?? '').trim()
              ) {
                return Promise.resolve()
              }
              return Promise.reject(new Error('Nickname is required'))
            },
          }),
        ]}
      >
        <Input disabled={selectedFantasyPlayerId != null} />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>
          Add
        </Button>
      </Form.Item>
    </Form>
  )
}
