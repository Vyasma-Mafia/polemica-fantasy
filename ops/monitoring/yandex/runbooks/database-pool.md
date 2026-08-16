# FantasyDatabasePoolSaturated

1. Check `hikaricp_connections_active`, `pending`, and `max` together.
2. Check PostgreSQL container health and connection/activity counts.
3. Inspect backend logs for slow external calls or transactions. Polemica HTTP
   calls must remain outside database transactions.
4. Do not increase the pool before finding whether transactions are blocked or
   leaked; a larger pool can overload PostgreSQL.
5. If a recent release introduced the saturation, roll back that backend image
   and verify pending returns to zero.
