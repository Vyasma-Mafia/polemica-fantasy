import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { rememberCampaign, useReleaseNotes, useTrackProductEvent } from './api/antiChurn'
import { FantikiBalance } from './components/FantikiBalance'
import { TopBarDisplayName } from './components/TopBarDisplayName'
import { InitDataProvider } from './context/InitDataContext.tsx'
import { CardsPage } from './pages/CardsPage'
import { AchievementsPage } from './pages/AchievementsPage'
import { HelpPage } from './pages/HelpPage'
import { FantasyHistoryPage } from './pages/FantasyHistoryPage'
import { FantasyRulesPage } from './pages/FantasyRulesPage'
import { HomePage } from './pages/HomePage'
import { LeaderboardPage } from './pages/LeaderboardPage'
import { LeaderboardPlayerTeamPage } from './pages/LeaderboardPlayerTeamPage'
import { ParticipantsPage } from './pages/ParticipantsPage'
import { SeriesPickerPage } from './pages/SeriesPickerPage'
import { SeriesPage } from './pages/SeriesPage'
import { SeriesComparePage } from './pages/SeriesComparePage'
import { MarketplacePage } from './pages/MarketplacePage'
import { MyListingsPage } from './pages/MyListingsPage'
import { MarketplaceWatchesPage } from './pages/MarketplaceWatchesPage'
import { NotificationSettingsPage } from './pages/NotificationSettingsPage'
import { PlayerProfilePage } from './pages/PlayerProfilePage'
import { ProfileCustomizationPage } from './pages/ProfileCustomizationPage'
import { RatingPage } from './pages/RatingPage'
import { StorePage } from './pages/StorePage'
import { TeamPage } from './pages/TeamPage'
import { TransactionDetailPage } from './pages/TransactionDetailPage'
import { TournamentSubscriptionsPage } from './pages/TournamentSubscriptionsPage'
import { TournamentComparePage } from './pages/TournamentComparePage'
import { TournamentLeaderboardPage } from './pages/TournamentLeaderboardPage'
import { TournamentPage } from './pages/TournamentPage'
import { WhatsNewPage } from './pages/WhatsNewPage'
import { parseShareStartParam, readInitialShareStartParam } from './lib/shareLinks'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

function Shell() {
  const releaseNotesQ = useReleaseNotes()
  const unseenNotes = releaseNotesQ.data?.unseenCount ?? 0
  const location = useLocation()
  const navigate = useNavigate()
  const track = useTrackProductEvent()
  const trackedCampaigns = useRef(new Set<string>())
  const handledStartParam = useRef(false)

  useEffect(() => {
    if (handledStartParam.current) return
    handledStartParam.current = true
    const target = parseShareStartParam(readInitialShareStartParam())
    if (!target) return
    navigate(`${target.path}${target.search ? `?${target.search}` : ''}`, { replace: true })
  }, [navigate])

  useEffect(() => {
    const params = new URLSearchParams(location.search)
    const campaignId = params.get('campaignId')
    if (!campaignId || trackedCampaigns.current.has(campaignId)) return
    const parsed = Number(campaignId)
    if (!Number.isFinite(parsed)) return
    rememberCampaign(parsed)
    trackedCampaigns.current.add(campaignId)
    track({ eventType: 'CAMPAIGN_OPEN', campaignId: parsed, metadata: { path: location.pathname } })
    track({ eventType: 'CAMPAIGN_CLICK', campaignId: parsed, metadata: { path: location.pathname } })
  }, [location.pathname, location.search, track])

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
            to="/whats-new"
            className={({ isActive }) => `top__whats-new${isActive ? ' active' : ''}`}
            aria-label={unseenNotes > 0 ? `Новое: ${unseenNotes} непросмотрено` : 'Новое'}
            title="Новое"
          >
            <svg className="top__news-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4.75 5.25h11.5a3 3 0 0 1 3 3v10.5H7.75a3 3 0 0 1-3-3V5.25Z" />
              <path d="M8 9h7.5M8 12h7.5M8 15h4.25" />
              <path d="M19.25 9.75h1.5v6a3 3 0 0 1-3 3" />
            </svg>
            {unseenNotes > 0 && <span className="top__badge">{unseenNotes}</span>}
          </NavLink>
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
          <NavLink to="/marketplace">Маркетплейс</NavLink>
          <NavLink to="/store" className="nav__store">
            <span className="nav__store-icon" aria-hidden>
              🛒
            </span>
            Магазин
          </NavLink>
          <NavLink to="/rating">Рейтинг</NavLink>
          <NavLink to="/achievements">Достижения</NavLink>
          <NavLink to="/help">Справка</NavLink>
        </nav>
      </header>
      <main className="main">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/tournaments/:tournamentId" element={<TournamentPage />} />
          <Route path="/tournaments/:tournamentId/series" element={<SeriesPickerPage />} />
          <Route path="/tournaments/:tournamentId/leaderboard" element={<TournamentLeaderboardPage />} />
          <Route path="/tournaments/:tournamentId/compare/:telegramId" element={<TournamentComparePage />} />
          <Route path="/tournaments/:tournamentId/rules" element={<FantasyRulesPage />} />
          <Route path="/tournaments/:tournamentId/history" element={<FantasyHistoryPage />} />
          <Route path="/tournaments/:tournamentId/participants" element={<ParticipantsPage />} />
          <Route path="/series/:seriesId" element={<SeriesPage />} />
          <Route path="/series/:seriesId/team" element={<TeamPage />} />
          <Route path="/series/:seriesId/compare/:telegramId" element={<SeriesComparePage />} />
          <Route path="/series/:seriesId/leaderboard" element={<LeaderboardPage />} />
          <Route path="/series/:seriesId/leaderboard/player/:telegramId" element={<LeaderboardPlayerTeamPage />} />
          <Route path="/cards" element={<CardsPage />} />
          <Route path="/achievements" element={<AchievementsPage />} />
          <Route path="/rating" element={<RatingPage />} />
          <Route path="/players/:telegramId" element={<PlayerProfilePage />} />
          <Route path="/profile-customization" element={<ProfileCustomizationPage />} />
          <Route path="/help" element={<HelpPage />} />
          <Route path="/economy" element={<Navigate to="/help" replace />} />
          <Route path="/store" element={<StorePage />} />
          <Route path="/marketplace" element={<MarketplacePage />} />
          <Route path="/marketplace/my" element={<MyListingsPage />} />
          <Route path="/marketplace/transactions/:listingId" element={<TransactionDetailPage />} />
          <Route path="/notifications" element={<NotificationSettingsPage />} />
          <Route path="/notifications/tournaments" element={<TournamentSubscriptionsPage />} />
          <Route path="/notifications/marketplace-watches" element={<MarketplaceWatchesPage />} />
          <Route path="/whats-new" element={<WhatsNewPage />} />
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
