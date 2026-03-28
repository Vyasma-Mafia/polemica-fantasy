import { Layout, Menu, theme } from 'antd'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

const { Header, Sider, Content } = Layout

const menuItems = [
  { key: '/tournaments', label: <Link to="/tournaments">Tournaments</Link> },
  {
    key: '/card-templates',
    label: <Link to="/card-templates">Card templates</Link>,
  },
  { key: '/card-packs', label: <Link to="/card-packs">Card packs</Link> },
  { key: '/user-tools', label: <Link to="/user-tools">User tools</Link> },
]

export function AdminLayout() {
  const { logout } = useAuth()
  const location = useLocation()
  const {
    token: { colorBgContainer },
  } = theme.useToken()

  const selected = menuItems
    .map((m) => m.key)
    .find(
      (k) =>
        location.pathname === k ||
        (k !== '/' && location.pathname.startsWith(`${k}/`)),
    )

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth={0}>
        <div
          style={{
            height: 48,
            margin: 12,
            color: 'white',
            fontWeight: 600,
            fontSize: 14,
            lineHeight: '48px',
            textAlign: 'center',
          }}
        >
          Polemica Admin
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selected ? [selected] : []}
          items={menuItems}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            padding: '0 24px',
            background: colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: 16,
          }}
        >
          <a role="button" tabIndex={0} onClick={() => logout()}>
            Log out
          </a>
        </Header>
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
