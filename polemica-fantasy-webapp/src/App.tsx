import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, NavLink, Route, Routes } from 'react-router-dom'
import { InitDataProvider } from './context/InitDataContext'
import { CardsPage } from './pages/CardsPage'
import { HomePage } from './pages/HomePage'
import { LeaderboardPage } from './pages/LeaderboardPage'
import { SeriesPage } from './pages/SeriesPage'
import { TeamPage } from './pages/TeamPage'
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
        <strong>Polemica Fantasy</strong>
        <nav className="nav">
          <NavLink to="/" end>
            Турниры
          </NavLink>
          <NavLink to="/cards">Коллекция</NavLink>
        </nav>
      </header>
      <main className="main">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/tournaments/:tournamentId" element={<TournamentPage />} />
          <Route path="/series/:seriesId" element={<SeriesPage />} />
          <Route path="/series/:seriesId/team" element={<TeamPage />} />
          <Route path="/series/:seriesId/leaderboard" element={<LeaderboardPage />} />
          <Route path="/cards" element={<CardsPage />} />
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
