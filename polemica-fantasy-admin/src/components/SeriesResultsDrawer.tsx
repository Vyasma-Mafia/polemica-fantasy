import { Alert, App, Button, Drawer, Empty, Space, Table, Tag, Tooltip, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { getSeriesResults } from '../api/series'
import type {
  SeriesResultsGameDto,
  SeriesResultsPlayerDto,
  SeriesResultsPointsStatus,
} from '../api/types'
import { formatSeriesResultsTsv } from '../lib/seriesResultsCopy'

interface SeriesResultsDrawerProps {
  seriesId: number
  open: boolean
  onClose: () => void
}

const statusColors: Partial<Record<SeriesResultsPointsStatus, string>> = {
  AVAILABLE: 'green',
  PARTIAL: 'orange',
  UNFINISHED: 'default',
  CACHE_MISSING: 'red',
  CACHE_INVALID: 'red',
  LOAD_FAILED: 'red',
  EMPTY: 'default',
}

function gameDetails(game: SeriesResultsGameDto): string {
  const details = [`Polemica ID ${game.polemicaGameId}`]
  if (game.table != null) details.push(`table ${game.table}`)
  if (game.phase != null) details.push(`phase ${game.phase}`)
  if (game.gameNum != null) details.push(`game ${game.gameNum}`)
  return details.join(' · ')
}

function sortPlayers(players: SeriesResultsPlayerDto[]): SeriesResultsPlayerDto[] {
  return players
    .map((player, index) => ({ player, index }))
    .sort((a, b) => b.player.totalPoints - a.player.totalPoints || a.index - b.index)
    .map(({ player }) => player)
}

export function SeriesResultsDrawer({ seriesId, open, onClose }: SeriesResultsDrawerProps) {
  const { message } = App.useApp()
  const query = useQuery({
    queryKey: ['admin', 'series', seriesId, 'results'],
    queryFn: () => getSeriesResults(seriesId),
    enabled: open && Number.isFinite(seriesId),
    refetchOnWindowFocus: false,
  })

  const data = query.data
  const players = sortPlayers(data?.players ?? [])
  const hasIncompleteResults =
    players.some((player) => !player.complete) ||
    (data?.games.some((game) => game.pointsStatus !== 'AVAILABLE') ?? false)

  const copyResults = async () => {
    if (!data || players.length === 0) return
    try {
      await navigator.clipboard.writeText(formatSeriesResultsTsv(data.games, players))
      if (hasIncompleteResults) {
        message.warning('Results copied. Missing or incomplete scores were left blank.')
      } else {
        message.success('Results copied')
      }
    } catch {
      message.error('Could not copy results to clipboard')
    }
  }

  const columns: TableProps<SeriesResultsPlayerDto>['columns'] = [
    {
      title: 'Player',
      key: 'player',
      fixed: 'left',
      width: 190,
      render: (_, player) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{player.nickname}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {player.polemicaUserId != null
              ? `Polemica ID ${player.polemicaUserId}`
              : player.playerKey}
          </Typography.Text>
        </Space>
      ),
    },
    ...(data?.games ?? []).map((game) => ({
      title: (
        <Tooltip title={gameDetails(game)}>
          <Space direction="vertical" size={0} align="center">
            <Typography.Text strong>{game.columnLabel}</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {game.table != null ? `T${game.table}` : '—'}
              {game.phase != null ? ` · P${game.phase}` : ''}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 10 }}>
              ID {game.polemicaGameId}
            </Typography.Text>
            {game.pointsStatus !== 'AVAILABLE' && (
              <Tag
                color={statusColors[game.pointsStatus]}
                style={{ marginInlineEnd: 0, fontSize: 10, lineHeight: '16px' }}
              >
                {game.pointsStatus}
              </Tag>
            )}
          </Space>
        </Tooltip>
      ),
      key: `game-${game.seriesGameId}`,
      width: 105,
      align: 'right' as const,
      render: (_: unknown, player: SeriesResultsPlayerDto) => {
        const cell = player.cells.find((item) => item.seriesGameId === game.seriesGameId)
        if (!cell?.participated) return '—'
        if (cell.points != null) return cell.points
        return (
          <Tooltip title="Player participated, but the score is unavailable">
            <Typography.Text type="warning">!</Typography.Text>
          </Tooltip>
        )
      },
    })),
    {
      title: 'Total',
      dataIndex: 'totalPoints',
      key: 'total',
      fixed: 'right',
      width: 100,
      align: 'right',
      render: (value: number, player) =>
        player.complete ? (
          <Typography.Text strong>{value}</Typography.Text>
        ) : (
          <Tooltip title="Partial total: one or more game scores are unavailable">
            <Typography.Text strong type="warning">
              {value}*
            </Typography.Text>
          </Tooltip>
        ),
    },
  ]

  return (
    <Drawer
      title="Player results"
      width="min(1200px, 100vw)"
      open={open}
      onClose={onClose}
    >
      <Alert
        type="info"
        showIcon
        message="Current raw Polemica base points"
        description="Perks and card rarity modifiers are not included."
        style={{ marginBottom: 16 }}
      />
      <Space wrap style={{ marginBottom: 16 }}>
        <Button loading={query.isFetching} onClick={() => void query.refetch()}>
          Refresh
        </Button>
        <Button disabled={!data || players.length === 0} onClick={() => void copyResults()}>
          Copy TSV
        </Button>
      </Space>
      {query.isError && (
        <Alert
          type="error"
          showIcon
          message="Could not load player results"
          description={query.error.message}
          action={<Button onClick={() => void query.refetch()}>Retry</Button>}
          style={{ marginBottom: 16 }}
        />
      )}
      {data?.warnings.map((warning, index) => (
        <Alert
          key={`${index}-${warning}`}
          type="warning"
          showIcon
          message={warning}
          style={{ marginBottom: 8 }}
        />
      ))}
      {data && data.games.length === 0 && (
        <Empty description="No games registered for this series" />
      )}
      {data && data.games.length > 0 && data.players.length === 0 && (
        <Empty description="No player results available" />
      )}
      {(!data || (data.games.length > 0 && data.players.length > 0)) && (
        <Table<SeriesResultsPlayerDto>
          rowKey="playerKey"
          size="small"
          loading={query.isLoading}
          columns={columns}
          dataSource={players}
          pagination={false}
          scroll={{ x: 290 + (data?.games.length ?? 0) * 105 }}
          locale={{ emptyText: 'No player results available' }}
        />
      )}
    </Drawer>
  )
}
