import { Layout, Menu, Tag, theme } from 'antd'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

const { Header, Sider, Content } = Layout

const allMenuItems = [
  { key: '/tournaments', label: <Link to="/tournaments">Tournaments</Link> },
  { key: '/players', label: <Link to="/players">Players</Link> },
  {
    key: '/card-templates',
    label: <Link to="/card-templates">Card templates</Link>,
  },
  {
    key: '/perks',
    label: <Link to="/perks">Perks</Link>,
  },
  {
    key: '/achievements',
    label: <Link to="/achievements">Achievements</Link>,
  },
  { key: '/card-packs', label: <Link to="/card-packs">Card packs</Link> },
  { key: '/users', label: <Link to="/users">Users</Link> },
  { key: '/broadcast', label: <Link to="/broadcast">Broadcast</Link> },
  { key: '/product-comms', label: <Link to="/product-comms">Product comms</Link> },
  { key: '/user-tools', label: <Link to="/user-tools">User tools</Link> },
  { key: '/economy', label: <Link to="/economy">Economy</Link> },
  {
    key: '/marketplace-moderation',
    label: <Link to="/marketplace-moderation">Marketplace</Link>,
  },
  { key: '/card-merges', label: <Link to="/card-merges">Card merges</Link> },
]

export function AdminLayout() {
  const { logout, role, roleLoading } = useAuth()
  const location = useLocation()
  const {
    token: { colorBgContainer },
  } = theme.useToken()

  const menuItems =
    role === 'moderator'
      ? allMenuItems.filter((item) => item.key === '/tournaments')
      : roleLoading
        ? []
        : allMenuItems

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
          <Tag color={role === 'admin' ? 'blue' : 'orange'}>
            {roleLoading ? 'Loading role' : role === 'admin' ? 'Admin' : 'Moderator'}
          </Tag>
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
