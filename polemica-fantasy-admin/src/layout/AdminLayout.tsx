import { MenuOutlined } from '@ant-design/icons'
import { Button, Drawer, Grid, Layout, Menu, Tag, theme } from 'antd'
import { useState } from 'react'
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
  const screens = Grid.useBreakpoint()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const {
    token: { colorBgContainer },
  } = theme.useToken()
  const isMobile = screens.md === false

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

  const navigation = (
    <>
      <div className="pf-admin-brand">Polemica Admin</div>
      <Menu
        theme={isMobile ? 'light' : 'dark'}
        mode="inline"
        selectedKeys={selected ? [selected] : []}
        items={menuItems}
        onClick={() => setMobileMenuOpen(false)}
      />
    </>
  )

  return (
    <Layout className="pf-admin-layout">
      {!isMobile && <Sider width={228}>{navigation}</Sider>}
      <Layout>
        <Header
          className="pf-admin-header"
          style={{ background: colorBgContainer }}
        >
          {isMobile && (
            <Button
              aria-label="Open navigation"
              icon={<MenuOutlined />}
              onClick={() => setMobileMenuOpen(true)}
            />
          )}
          {isMobile && <div className="pf-admin-header__title">Polemica Admin</div>}
          <div className="pf-admin-header__spacer" />
          <Tag color={role === 'admin' ? 'blue' : 'orange'}>
            {roleLoading ? 'Loading role' : role === 'admin' ? 'Admin' : 'Moderator'}
          </Tag>
          <a role="button" tabIndex={0} onClick={() => logout()}>
            Log out
          </a>
        </Header>
        <Content className="pf-admin-content">
          <Outlet />
        </Content>
      </Layout>
      <Drawer
        title="Navigation"
        placement="left"
        open={mobileMenuOpen}
        onClose={() => setMobileMenuOpen(false)}
        size="default"
        styles={{ body: { padding: 0 } }}
      >
        {navigation}
      </Drawer>
    </Layout>
  )
}
