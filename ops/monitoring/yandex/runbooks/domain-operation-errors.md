# Repeated sync or scoring errors

1. Split failures by `fantasy_series_sync_duration_seconds_count{result="error"}`
   and `fantasy_scoring_duration_seconds_count{result="error"}`.
2. Inspect backend logs for Polemica authentication, upstream HTTP, JSON parsing,
   and transaction errors. Metric labels deliberately contain no series or game
   identifiers; use timestamps to correlate with logs.
3. Confirm Polemica credentials and public match pages without printing secrets.
4. Retry only the affected series from the admin UI after identifying the cause.
5. An empty STANDALONE sync is normal and is tracked separately; do not treat
   `fantasy_series_sync_empty_total` growth as an error by itself.
