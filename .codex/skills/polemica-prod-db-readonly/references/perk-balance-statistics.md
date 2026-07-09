# Perk Balance Statistics

Use these read-only SQL templates when users ask whether a perk is too strong,
too common in top ranks, or needs bonus tuning. Run them through the production
readonly helper from the repo root:

```bash
.codex/skills/polemica-prod-db-readonly/scripts/prod-db-readonly.sh --file /private/tmp/perk-query.sql
```

Keep results small in the answer: explain the metric, show the relevant table,
and separate verified production data from recommendations.

## Shared Definitions

- A team "has perk X" if at least one submitted `fantasy_team_card` uses a
  `user_card` whose `card_template` has `card_template_perk.perk_id = X`.
- Rank teams inside each `series_league_id` by
  `row_number() over (partition by series_league_id order by total_score desc, id asc)`.
  This matches the user-visible single-place leaderboard behavior.
- Use only `fantasy_team.total_score is not null`.
- Exclude tiny leaderboards with `league_team_count >= 5` unless the user asks
  for all data.
- `share_all` is the fraction of all eligible teams that have the perk.
- `share_top3` / `share_top10` are the fraction of top-ranked teams that have the perk.
- `top*_lift = share_top* / share_all`.
- `p_top*_given_perk` answers the inverse question: among teams with the perk,
  what fraction reached top-3/top-10.
- Raw perk bonus means `fantasy_team_card_game_perk.bonus_points` before
  multiplying by `fantasy_team_card_game_score.rarity_modifier`.

## Per-Perk Top Prevalence

Use this first when comparing all perks. Adjust the `scopes` CTE if a different
period is needed.

```sql
with team_perks as (
    select distinct ft.id as fantasy_team_id, ctp.perk_id
    from fantasy_team ft
    join fantasy_team_card ftc on ftc.fantasy_team_id = ft.id
    join user_card uc on uc.id = ftc.user_card_id
    join card_template_perk ctp on ctp.card_template_id = uc.card_template_id
    where ft.total_score is not null
),
ranked as (
    select
        ft.id,
        ft.series_league_id,
        s.starts_at,
        ft.total_score,
        row_number() over (
            partition by ft.series_league_id
            order by ft.total_score desc, ft.id asc
        ) as rank,
        count(*) over (partition by ft.series_league_id) as league_team_count
    from fantasy_team ft
    join series s on s.id = ft.series_id
    where ft.total_score is not null
),
eligible as (
    select *
    from ranked
    where league_team_count >= 5
),
perks as (
    select id as perk_id
    from perk
),
scopes as (
    select 'all history'::text as period, *
    from eligible
    union all
    select 'current period'::text as period, *
    from eligible
    where starts_at >= date '2026-07-01'
),
overall as (
    select
        period,
        count(*) as teams,
        count(*) filter (where rank <= 3) as top3_teams,
        count(*) filter (where rank <= 10) as top10_teams
    from scopes
    group by period
),
agg as (
    select
        sc.period,
        p.perk_id,
        count(*) as teams,
        count(*) filter (where tp.perk_id is not null) as perk_teams,
        count(*) filter (where sc.rank <= 1) as top1_teams,
        count(*) filter (where sc.rank <= 1 and tp.perk_id is not null) as perk_top1,
        count(*) filter (where sc.rank <= 3) as top3_teams,
        count(*) filter (where sc.rank <= 3 and tp.perk_id is not null) as perk_top3,
        count(*) filter (where sc.rank <= 10) as top10_teams,
        count(*) filter (where sc.rank <= 10 and tp.perk_id is not null) as perk_top10
    from scopes sc
    cross join perks p
    left join team_perks tp
      on tp.fantasy_team_id = sc.id
     and tp.perk_id = p.perk_id
    group by sc.period, p.perk_id
)
select
    a.period,
    a.perk_id,
    a.perk_teams,
    round(a.perk_teams::numeric / nullif(a.teams, 0), 4) as share_all,
    a.perk_top1,
    round(a.perk_top1::numeric / nullif(a.top1_teams, 0), 4) as share_top1,
    a.perk_top3,
    round(a.perk_top3::numeric / nullif(a.top3_teams, 0), 4) as share_top3,
    round(
        (a.perk_top3::numeric / nullif(a.top3_teams, 0)) /
        nullif(a.perk_teams::numeric / nullif(a.teams, 0), 0),
        2
    ) as top3_lift,
    round(a.perk_top3::numeric / nullif(a.perk_teams, 0), 4) as p_top3_given_perk,
    a.perk_top10,
    round(a.perk_top10::numeric / nullif(a.top10_teams, 0), 4) as share_top10,
    round(
        (a.perk_top10::numeric / nullif(a.top10_teams, 0)) /
        nullif(a.perk_teams::numeric / nullif(a.teams, 0), 0),
        2
    ) as top10_lift,
    round(a.perk_top10::numeric / nullif(a.perk_teams, 0), 4) as p_top10_given_perk
from agg a
join overall o on o.period = a.period
order by a.period, top3_lift desc nulls last, share_top3 desc;
```

## Raw Bonus Contribution

Use this to explain whether a perk dominates total perk points, independent of
base score and rarity.

```sql
with scoped_scores as (
    select gs.id, gs.perk_bonus
    from fantasy_team_card_game_score gs
    join fantasy_team_card ftc on ftc.id = gs.fantasy_team_card_id
    join fantasy_team ft on ft.id = ftc.fantasy_team_id
    join series s on s.id = ft.series_id
    where s.starts_at >= date '2026-07-01'
),
total as (
    select
        count(*)::numeric as score_rows,
        coalesce(sum(perk_bonus), 0)::numeric as total_perk_bonus
    from scoped_scores
),
perk_rows as (
    select
        gp.perk_id,
        count(*)::numeric as triggers,
        sum(gp.bonus_points)::numeric as raw_bonus,
        avg(gp.bonus_points)::numeric as avg_bonus
    from fantasy_team_card_game_perk gp
    join scoped_scores ss on ss.id = gp.card_game_score_id
    group by gp.perk_id
)
select
    pr.perk_id,
    pr.triggers::bigint,
    round(pr.avg_bonus, 3) as avg_raw_bonus_per_trigger,
    round(pr.raw_bonus, 3) as raw_bonus,
    round(pr.triggers / nullif(t.score_rows, 0), 5) as triggers_per_score_row,
    round(pr.raw_bonus / nullif(t.score_rows, 0), 5) as raw_bonus_per_score_row,
    round(pr.raw_bonus / nullif(t.total_perk_bonus, 0), 5) as share_of_raw_perk_bonus
from perk_rows pr
cross join total t
order by raw_bonus_per_score_row desc;
```

## Raw Bonus Per Lineup Exposure

Use this for the metric "times the perk triggered times bonus, divided by how
often a card with that perk was fielded".

```sql
with scoped_scored_cards as (
    select distinct ftc.id as fantasy_team_card_id, ctp.perk_id
    from fantasy_team_card ftc
    join fantasy_team ft on ft.id = ftc.fantasy_team_id
    join series s on s.id = ft.series_id
    join user_card uc on uc.id = ftc.user_card_id
    join card_template_perk ctp on ctp.card_template_id = uc.card_template_id
    where s.starts_at >= date '2026-07-01'
      and exists (
          select 1
          from fantasy_team_card_game_score gs
          where gs.fantasy_team_card_id = ftc.id
      )
),
exposures as (
    select perk_id, count(*)::numeric as lineup_card_count
    from scoped_scored_cards
    group by perk_id
),
applied as (
    select
        gp.perk_id,
        count(*)::numeric as triggers,
        sum(gp.bonus_points)::numeric as raw_bonus
    from fantasy_team_card_game_perk gp
    join fantasy_team_card_game_score gs on gs.id = gp.card_game_score_id
    join fantasy_team_card ftc on ftc.id = gs.fantasy_team_card_id
    join fantasy_team ft on ft.id = ftc.fantasy_team_id
    join series s on s.id = ft.series_id
    where s.starts_at >= date '2026-07-01'
    group by gp.perk_id
)
select
    e.perk_id,
    e.lineup_card_count::bigint as lineup_exposures,
    coalesce(a.triggers, 0)::bigint as triggers,
    round(coalesce(a.raw_bonus, 0), 3) as triggers_times_bonus,
    round((coalesce(a.raw_bonus, 0) / nullif(e.lineup_card_count, 0))::numeric, 4)
        as raw_bonus_per_lineup_exposure,
    round((coalesce(a.triggers, 0) / nullif(e.lineup_card_count, 0))::numeric, 4)
        as triggers_per_lineup_exposure
from exposures e
left join applied a on a.perk_id = e.perk_id
order by raw_bonus_per_lineup_exposure desc;
```

## Multiple Cards With One Perk

Use this for complaints that decks with repeated copies of one perk dominate.
Replace `'sniper'` with the perk under investigation.

```sql
with team_perk_counts as (
    select
        ft.id as fantasy_team_id,
        count(*) filter (where ctp.perk_id = 'sniper') as perk_card_count
    from fantasy_team ft
    join fantasy_team_card ftc on ftc.fantasy_team_id = ft.id
    join user_card uc on uc.id = ftc.user_card_id
    join card_template_perk ctp on ctp.card_template_id = uc.card_template_id
    group by ft.id
),
base as (
    select
        ft.id,
        ft.series_league_id,
        s.starts_at,
        ft.total_score,
        coalesce(tpc.perk_card_count, 0) as perk_card_count
    from fantasy_team ft
    join series s on s.id = ft.series_id
    left join team_perk_counts tpc on tpc.fantasy_team_id = ft.id
    where ft.total_score is not null
),
ranked as (
    select
        *,
        row_number() over (
            partition by series_league_id
            order by total_score desc, id asc
        ) as rank,
        count(*) over (partition by series_league_id) as league_team_count
    from base
),
eligible as (
    select *
    from ranked
    where league_team_count >= 5
),
scopes as (
    select 'all history'::text as period, *
    from eligible
    union all
    select 'current period'::text as period, *
    from eligible
    where starts_at >= date '2026-07-01'
)
select
    period,
    case
        when perk_card_count = 0 then '0'
        when perk_card_count = 1 then '1'
        when perk_card_count = 2 then '2'
        else '3+'
    end as perk_cards,
    count(*) as teams,
    count(*) filter (where rank <= 3) as top3,
    round((count(*) filter (where rank <= 3))::numeric / nullif(count(*), 0), 4)
        as top3_rate,
    count(*) filter (where rank <= 10) as top10,
    round((count(*) filter (where rank <= 10))::numeric / nullif(count(*), 0), 4)
        as top10_rate
from scopes
group by period, perk_cards
order by period, perk_cards;
```

## Candidate Bonus Counterfactual

Use this after a single perk looks suspicious. It recalculates ranks as if that
perk had different `bonus_points`, using stored per-game score breakdown and
the original rarity modifier. Replace `'sniper'` and the candidate list.

```sql
with perk_contrib as (
    select
        ft.id as fantasy_team_id,
        sum(gp.bonus_points * gs.rarity_modifier)::numeric as current_perk_score_bonus,
        sum(gs.rarity_modifier)::numeric as perk_modifier_sum
    from fantasy_team ft
    join fantasy_team_card ftc on ftc.fantasy_team_id = ft.id
    join fantasy_team_card_game_score gs on gs.fantasy_team_card_id = ftc.id
    join fantasy_team_card_game_perk gp
      on gp.card_game_score_id = gs.id
     and gp.perk_id = 'sniper'
    group by ft.id
),
perk_counts as (
    select
        ft.id as fantasy_team_id,
        count(*) filter (where ctp.perk_id = 'sniper') as perk_card_count
    from fantasy_team ft
    join fantasy_team_card ftc on ftc.fantasy_team_id = ft.id
    join user_card uc on uc.id = ftc.user_card_id
    join card_template_perk ctp on ctp.card_template_id = uc.card_template_id
    group by ft.id
),
base as (
    select
        ft.id,
        ft.series_league_id,
        s.starts_at,
        ft.total_score::numeric as current_score,
        coalesce(pc.current_perk_score_bonus, 0) as current_perk_score_bonus,
        coalesce(pc.perk_modifier_sum, 0) as perk_modifier_sum,
        coalesce(cnt.perk_card_count, 0) as perk_card_count
    from fantasy_team ft
    join series s on s.id = ft.series_id
    left join perk_contrib pc on pc.fantasy_team_id = ft.id
    left join perk_counts cnt on cnt.fantasy_team_id = ft.id
    where ft.total_score is not null
      and s.starts_at >= date '2026-07-01'
),
eligible_ids as (
    select id
    from (
        select id, count(*) over (partition by series_league_id) as league_team_count
        from base
    ) x
    where league_team_count >= 5
),
eligible as (
    select b.*
    from base b
    join eligible_ids e using (id)
),
candidates as (
    select *
    from (values
        (0.00::numeric),
        (2.00::numeric),
        (2.50::numeric),
        (2.75::numeric),
        (3.00::numeric),
        (3.25::numeric),
        (3.50::numeric),
        (4.00::numeric),
        (5.00::numeric)
    ) v(candidate_bonus)
),
ranked as (
    select
        c.candidate_bonus,
        e.id,
        e.perk_card_count,
        row_number() over (
            partition by c.candidate_bonus, e.series_league_id
            order by (
                e.current_score
                - e.current_perk_score_bonus
                + e.perk_modifier_sum * c.candidate_bonus
            ) desc, e.id asc
        ) as rank_candidate
    from eligible e
    cross join candidates c
),
current_ranked as (
    select
        e.id,
        e.perk_card_count,
        row_number() over (
            partition by e.series_league_id
            order by e.current_score desc, e.id asc
        ) as rank_current
    from eligible e
)
select
    r.candidate_bonus,
    count(*) filter (where r.rank_candidate <= 3) as top3_teams,
    count(*) filter (where r.rank_candidate <= 3 and r.perk_card_count > 0) as perk_top3,
    round(
        (count(*) filter (where r.rank_candidate <= 3 and r.perk_card_count > 0))::numeric /
        nullif(count(*) filter (where r.rank_candidate <= 3), 0),
        4
    ) as perk_top3_share,
    count(*) filter (
        where cr.rank_current <= 3
          and cr.perk_card_count > 0
          and r.rank_candidate > 3
    ) as current_perk_top3_leaving,
    count(*) filter (where r.rank_candidate <= 10) as top10_teams,
    count(*) filter (where r.rank_candidate <= 10 and r.perk_card_count > 0) as perk_top10,
    round(
        (count(*) filter (where r.rank_candidate <= 10 and r.perk_card_count > 0))::numeric /
        nullif(count(*) filter (where r.rank_candidate <= 10), 0),
        4
    ) as perk_top10_share,
    count(*) filter (
        where cr.rank_current <= 10
          and cr.perk_card_count > 0
          and r.rank_candidate > 10
    ) as current_perk_top10_leaving
from ranked r
join current_ranked cr on cr.id = r.id
group by r.candidate_bonus
order by r.candidate_bonus;
```

