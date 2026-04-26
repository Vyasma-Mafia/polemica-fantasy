import type { SeriesLeagueBrief } from '../api/types'
import { leagueShortName } from '../lib/leagues'

type LeagueTabsProps = {
  leagues: SeriesLeagueBrief[]
  activeCode: string
  onChange: (code: string) => void
}

export function LeagueTabs({ leagues, activeCode, onChange }: LeagueTabsProps) {
  return (
    <div className="pf-league-tabs" role="tablist" aria-label="Лиги серии">
      {leagues.map((league) => {
        const active = activeCode.toUpperCase() === league.code.toUpperCase()
        return (
          <button
            key={league.code}
            type="button"
            role="tab"
            aria-selected={active}
            className={`pf-league-tab${active ? ' pf-league-tab--active' : ''}`}
            onClick={() => onChange(league.code)}
          >
            <span className="pf-league-tab__name">{leagueShortName(league.code, league.name)}</span>
            {league.hasTeam && <span className="pf-league-tab__check">✓</span>}
            {league.valueCap != null && <span className="pf-league-tab__cap">{league.valueCap}₱</span>}
          </button>
        )
      })}
    </div>
  )
}
