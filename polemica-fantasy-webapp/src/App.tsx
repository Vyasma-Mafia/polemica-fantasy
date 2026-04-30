import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, NavLink, Route, Routes } from 'react-router-dom'
import { FantikiBalance } from './components/FantikiBalance'
import { TopBarDisplayName } from './components/TopBarDisplayName'
import { InitDataProvider } from './context/InitDataContext.tsx'
import { CardsPage } from './pages/CardsPage'
import { HelpPage } from './pages/HelpPage'
import { FantasyHistoryPage } from './pages/FantasyHistoryPage'
import { FantasyRulesPage } from './pages/FantasyRulesPage'
import { HomePage } from './pages/HomePage'
import { LeaderboardPage } from './pages/LeaderboardPage'
import { LeaderboardPlayerTeamPage } from './pages/LeaderboardPlayerTeamPage'
import { ParticipantsPage } from './pages/ParticipantsPage'
import { SeriesPickerPage } from './pages/SeriesPickerPage'
import { SeriesPage } from './pages/SeriesPage'
import { MarketplacePage } from './pages/MarketplacePage'
import { MyListingsPage } from './pages/MyListingsPage'
import { MarketplaceWatchesPage } from './pages/MarketplaceWatchesPage'
import { NotificationSettingsPage } from './pages/NotificationSettingsPage'
import { PlayerProfilePage } from './pages/PlayerProfilePage'
import { RatingPage } from './pages/RatingPage'
import { StorePage } from './pages/StorePage'
import { TeamPage } from './pages/TeamPage'
import { TournamentSubscriptionsPage } from './pages/TournamentSubscriptionsPage'
import { TournamentLeaderboardPage } from './pages/TournamentLeaderboardPage'
import { TournamentPage } from './pages/TournamentPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

function Shell() {
  return (
    <div className="shell">
      <header className="top">
        <div className="top__bar">
          <NavLink to="/" className="top__brand">
            Polemica Fantasy
          </NavLink>
          <TopBarDisplayName />
          <div className="top__balance">
            <FantikiBalance />
          </div>
          <NavLink
            to="/notifications"
            className={({ isActive }) => `top__notifications${isActive ? ' active' : ''}`}
            aria-label="Уведомления"
          >
            🔔
          </NavLink>
        </div>
        <nav className="nav" aria-label="Основное меню">
          <NavLink to="/" end>
            Турниры
          </NavLink>
          <NavLink to="/cards">Коллекция</NavLink>
          <NavLink to="/rating">Рейтинг</NavLink>
          <NavLink to="/help">Справка</NavLink>
          <NavLink to="/marketplace">Маркетплейс</NavLink>
          <NavLink to="/store" className="nav__store">
            <span className="nav__store-icon" aria-hidden>
              🛒
            </span>
            Магазин
          </NavLink>
        </nav>
      </header>
      <main className="main">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/tournaments/:tournamentId" element={<TournamentPage />} />
          <Route path="/tournaments/:tournamentId/series" element={<SeriesPickerPage />} />
          <Route path="/tournaments/:tournamentId/leaderboard" element={<TournamentLeaderboardPage />} />
          <Route path="/tournaments/:tournamentId/rules" element={<FantasyRulesPage />} />
          <Route path="/tournaments/:tournamentId/history" element={<FantasyHistoryPage />} />
          <Route path="/tournaments/:tournamentId/participants" element={<ParticipantsPage />} />
          <Route path="/series/:seriesId" element={<SeriesPage />} />
          <Route path="/series/:seriesId/team" element={<TeamPage />} />
          <Route path="/series/:seriesId/leaderboard" element={<LeaderboardPage />} />
          <Route path="/series/:seriesId/leaderboard/player/:telegramId" element={<LeaderboardPlayerTeamPage />} />
          <Route path="/cards" element={<CardsPage />} />
          <Route path="/rating" element={<RatingPage />} />
          <Route path="/players/:telegramId" element={<PlayerProfilePage />} />
          <Route path="/help" element={<HelpPage />} />
          <Route path="/economy" element={<Navigate to="/help" replace />} />
          <Route path="/store" element={<StorePage />} />
          <Route path="/marketplace" element={<MarketplacePage />} />
          <Route path="/marketplace/my" element={<MyListingsPage />} />
          <Route path="/notifications" element={<NotificationSettingsPage />} />
          <Route path="/notifications/tournaments" element={<TournamentSubscriptionsPage />} />
          <Route path="/notifications/marketplace-watches" element={<MarketplaceWatchesPage />} />
        </Routes>
      </main>
    </div>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <InitDataProvider>
        <BrowserRouter>
          <Shell />
        </BrowserRouter>
      </InitDataProvider>
    </QueryClientProvider>
  )
}
