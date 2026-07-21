import type { SeriesResultsGameDto, SeriesResultsPlayerDto } from '../api/types'

function sanitizeTsvValue(value: string): string {
  return value.replace(/[\t\r\n]+/g, ' ').trim()
}

function formatPoint(value: number): string {
  return Object.is(value, -0) ? '0' : String(value)
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
        return cell?.participated && cell.points != null ? formatPoint(cell.points) : ''
      })
      return [sanitizeTsvValue(player.nickname), ...points].join('\t')
    })
    .join('\n')
}
