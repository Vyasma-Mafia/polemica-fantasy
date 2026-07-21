import type { SeriesResultsGameDto, SeriesResultsPlayerDto } from '../api/types'

function sanitizeTsvValue(value: string): string {
  return value.replace(/[\t\r\n]+/g, ' ').trim()
}

const pointFormatter = new Intl.NumberFormat('en-US', {
  useGrouping: false,
  maximumFractionDigits: 2,
})

export function formatSeriesResultPoint(value: number): string {
  const formatted = pointFormatter.format(value)
  return formatted === '-0' ? '0' : formatted
}

export function formatSeriesResultsTsv(
  games: SeriesResultsGameDto[],
  players: SeriesResultsPlayerDto[],
): string {
  return players
    .map((player) => {
      const cellsByGame = new Map(player.cells.map((cell) => [cell.seriesGameId, cell]))
      const points = games.map((game) => {
        const cell = cellsByGame.get(game.seriesGameId)
        return cell?.participated && cell.points != null
          ? formatSeriesResultPoint(cell.points)
          : ''
      })
      return [sanitizeTsvValue(player.nickname), ...points].join('\t')
    })
    .join('\n')
}
