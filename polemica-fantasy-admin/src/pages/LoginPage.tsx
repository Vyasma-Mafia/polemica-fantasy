import { App, Button, Card, Form, Input, Typography } from 'antd'
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function LoginPage() {
  const { login, authed } = useAuth()
  const navigate = useNavigate()
  const { message } = App.useApp()

  useEffect(() => {
    if (authed) {
      navigate('/tournaments', { replace: true })
    }
  }, [authed, navigate])

  if (authed) {
    return null
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f0f2f5',
      }}
    >
      <Card style={{ width: 400 }} title="Admin sign in">
        <Typography.Paragraph type="secondary">
          Use Basic Auth credentials from the backend (
          <code>ADMIN_USERNAME</code> / <code>ADMIN_PASSWORD</code>).
        </Typography.Paragraph>
        <Form
          layout="vertical"
          onFinish={async (v: { username: string; password: string }) => {
            try {
              await login(v.username, v.password)
              navigate('/tournaments', { replace: true })
            } catch (e) {
              message.error(e instanceof Error ? e.message : 'Login failed')
            }
          }}
        >
          <Form.Item
            name="username"
            label="Username"
            rules={[{ required: true }]}
          >
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="password"
            label="Password"
            rules={[{ required: true }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              Sign in
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}
