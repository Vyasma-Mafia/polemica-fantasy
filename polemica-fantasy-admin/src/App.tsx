import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedLayout } from './layout/ProtectedLayout'
import { AchievementsPage } from './pages/AchievementsPage'
import { CardPacksPage } from './pages/CardPacksPage'
import { CardTemplatesPage } from './pages/CardTemplatesPage'
import { LoginPage } from './pages/LoginPage'
import { SeriesDetailPage } from './pages/SeriesDetailPage'
import { TournamentDetailPage } from './pages/TournamentDetailPage'
import { TournamentsPage } from './pages/TournamentsPage'
import { EconomyPage } from './pages/EconomyPage'
import { UserToolsPage } from './pages/UserToolsPage'
import { UsersOverviewPage } from './pages/UsersOverviewPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<ProtectedLayout />}>
        <Route index element={<Navigate to="tournaments" replace />} />
        <Route path="tournaments" element={<TournamentsPage />} />
        <Route path="tournaments/:id" element={<TournamentDetailPage />} />
        <Route path="series/:id" element={<SeriesDetailPage />} />
        <Route path="card-templates" element={<CardTemplatesPage />} />
        <Route path="achievements" element={<AchievementsPage />} />
        <Route path="card-packs" element={<CardPacksPage />} />
        <Route path="user-tools" element={<UserToolsPage />} />
        <Route path="users" element={<UsersOverviewPage />} />
        <Route path="economy" element={<EconomyPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
