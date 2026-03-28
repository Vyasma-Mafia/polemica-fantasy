import { Button, Form, InputNumber, Input } from 'antd'

export function PlayerAddForm({
  onSubmit,
  loading,
}: {
  onSubmit: (v: { polemicaUserId: number; nickname: string }) => void
  loading?: boolean
}) {
  return (
    <Form
      layout="vertical"
      onFinish={onSubmit}
      initialValues={{ polemicaUserId: undefined, nickname: '' }}
    >
      <Form.Item
        name="polemicaUserId"
        label="Polemica user id"
        rules={[{ required: true }]}
      >
        <InputNumber style={{ width: '100%' }} min={1} />
      </Form.Item>
      <Form.Item name="nickname" label="Nickname" rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>
          Add
        </Button>
      </Form.Item>
    </Form>
  )
}
