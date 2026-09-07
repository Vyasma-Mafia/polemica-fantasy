# Player identity and realized market analytics

Accepted 2026-09-07. Read-only additions for ordinary players and the Fantasy agent.

## Scenario

A choose-pack option identifies a real Mafia player by `fantasyPlayerId`. Resolve
that identity, use its primary `polemicaUserId` for Research, and assess resale
using completed trades rather than asking prices alone. No change to write
permissions, scoring, rewards, or marketplace rules; no new UI screen.

## User API

- `GET /api/v1/players/{fantasyPlayerId}` returns `fantasyPlayerId`,
  `polemicaUserId`, `playerNickname`, nullable `playerPhotoUrl`. Nonpositive IDs
  are rejected, unknown IDs return 404. This is a real Mafia player, not the
  Telegram user returned by the separate `/{telegramId}/profile` route.
- Existing marketplace analytics detail gains `asOf` and `salesWindows` for
  exactly 7 and 30 days. Each contains `windowDays`, `from`, `to`,
  `completedSalesCount`, nullable `minSalePrice`, `maxSalePrice`, `medianSalePrice`,
  `medianTimeToSaleSeconds`, and `timeToSaleSampleSize`.
- Both windows use the same `asOf` and half-open `[from,to)` bounds on `soldAt`.
  Existing active ask statistics, latest ten sales, and their average remain.

## Semantics and limits

Aggregate all SOLD listings within each window in PostgreSQL, not a limited
sample. Use the sale-time template snapshot; legacy rows without a snapshot
fall back to the current card template, as existing analytics does. Prices are
gross buyer-paid prices, before seller commission. Sanctioned trades remain
included, so these figures are not manipulation-free fair-value estimates.

Time-to-sale is `soldAt - createdAt` for completed listings with nonnegative
durations. Invalid durations do not count toward `timeToSaleSampleSize`.
Empty counts are zero and unavailable metrics are null, not zero. Medians may
be fractional. Cancellations and earlier relistings are not included: this is
not sell-through probability or exposure across all attempted sales. Perks,
skins, and contract state can differ within a player/rarity group.

## Delivery and verification

Add `fantasy_get_player` as a closed read-only MCP tool and document the new
analytics fields. Persist requested identities/analytics through the existing
trusted Fantasy evidence collection where needed for decisions. Synchronize
TMA types/client without changing its current UI. Keep all 17 gameplay write
permissions unchanged. Existing SOLD/sold_at index supports the bounded query;
no schema migration is planned.

Test unknown/nonpositive identities, empty windows, more than ten sales,
fractional medians, exact boundaries, historical rarity snapshots, and invalid
durations. Roll out backend before runtime; validate ordinary authenticated
reads without placing test trades. Independently review before release.
