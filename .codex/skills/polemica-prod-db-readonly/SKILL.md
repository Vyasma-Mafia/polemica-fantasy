---
name: polemica-prod-db-readonly
description: Connect to and inspect the Polemica Fantasy production PostgreSQL database safely. Use when the user asks to read, query, inspect, diagnose, count, export small samples from, verify data in the production database, or collect production perk balance statistics; never use for writes, migrations, data repair, or destructive operations.
---

# Polemica Production DB Readonly

## Scope

Use this skill only for read-only production database inspection in the `polemica-fantasy` project.

Production access goes through the VPS:

- SSH target: `mafia@51.250.18.236`
- SSH key: `~/personal/mafia/id_rsa`
- Remote repo: `~/polemica-fantasy`
- Compose file: `docker-compose.prod.yml`
- PostgreSQL service: `fantasy-db`
- Database name: `fantasy`

Do not paste or request production secrets in the conversation. The DB credentials live in the remote `.env` / container environment.

## Safety Rules

- Only run `SELECT`, `WITH`, `SHOW`, `EXPLAIN` without `ANALYZE`, and catalog/introspection queries.
- Never run DDL, DML, migrations, `VACUUM`, `ANALYZE`, `COPY`, `DO`, `CALL`, advisory lock changes, or psql shell/meta commands.
- Prefer narrow queries with explicit columns and `LIMIT`.
- Avoid queries that can lock or scan large tables without filters. Start with `count(*)`, indexed predicates, or `EXPLAIN`.
- Do not export large datasets. For user-visible answers, summarize counts and small samples.
- If a task requires writing, stop and ask for a separate explicit production-change plan.

## Preferred Helper

From the repo root, use the bundled helper. It SSHes to the VPS, executes `psql` inside the `fantasy-db` container, wraps SQL in a read-only transaction, sets short timeouts, disables the pager, and rejects obvious write/meta commands.

```bash
.codex/skills/polemica-prod-db-readonly/scripts/prod-db-readonly.sh --sql "select now();"
```

For multi-line SQL, use a temporary file under `/private/tmp`:

```bash
.codex/skills/polemica-prod-db-readonly/scripts/prod-db-readonly.sh --file /private/tmp/prod-query.sql
```

Useful environment overrides:

```bash
POLEMICA_PROD_SSH_TARGET=mafia@51.250.18.236
POLEMICA_PROD_SSH_KEY=~/personal/mafia/id_rsa
POLEMICA_PROD_REPO='~/polemica-fantasy'
POLEMICA_PROD_STATEMENT_TIMEOUT=30s
POLEMICA_PROD_LOCK_TIMEOUT=2s
```

## Direct Fallback

Use this only if the helper needs adjustment. Keep the read-only wrapper and do not expose secrets:

```bash
ssh -i ~/personal/mafia/id_rsa mafia@51.250.18.236 \
  'cd ~/polemica-fantasy && docker compose -f docker-compose.prod.yml exec -T fantasy-db sh -lc '"'"'
    psql -X -v ON_ERROR_STOP=1 -P pager=off -U "${POSTGRES_USER:-fantasy}" -d "${POSTGRES_DB:-fantasy}"
  '"'"'' <<'SQL'
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '30s';
SET LOCAL lock_timeout = '2s';
select now();
ROLLBACK;
SQL
```

## Introspection Queries

List application tables:

```sql
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_type = 'BASE TABLE'
order by table_name;
```

List columns for one table:

```sql
select column_name, data_type, is_nullable
from information_schema.columns
where table_schema = 'public'
  and table_name = 'telegram_user'
order by ordinal_position;
```

Check migration state:

```sql
select installed_rank, version, description, installed_on, success
from flyway_schema_history
order by installed_rank desc
limit 10;
```

Estimate table sizes:

```sql
select relname as table_name, n_live_tup as estimated_rows
from pg_stat_user_tables
order by n_live_tup desc
limit 20;
```

## Perk Balance Statistics

When evaluating perk balance from production leaderboard/scoring data, read
[references/perk-balance-statistics.md](references/perk-balance-statistics.md).
It contains the standard definitions and SQL templates for:

- per-perk top-rank prevalence and lift;
- raw perk contribution per scored row and per lineup exposure;
- multiple cards with the same perk in one team;
- counterfactual ranking for candidate `bonus_points` values.

## Reporting

When answering the user:

- State that the query was run against production.
- Include the SQL intent, not secrets or connection strings with passwords.
- Mention if the helper blocked a query and why.
- Keep raw row output small; summarize larger result sets.
