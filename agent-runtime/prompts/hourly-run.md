Perform one bounded hourly turn.

1. Read open operation intents. If any exist, switch immediately to reconciliation-only behavior.
2. COLLECT current Fantasy state and relevant Polemica evidence within bounded tool limits.
   Start with fantasy_get_periodic_rating_current and fantasy_get_periodic_rating_me(period_id).
   Read prior decisions/outcomes to keep a plan across runs. Record the period's dates, status,
   league, entry.rank/totalScore/seriesCount, and contributions. A null entry means unranked,
   not a failed tool. If there is no actionable OPEN period (absent, SETTLING, or FINALIZED),
   do not invent dates or force spending: prepare for the next period using confirmed opportunities.
   Identify upcoming MAIN submissions/improvements that may contribute to the current period;
   final inclusion depends on finalization and the last actual game date, not submission date.
   Mark uncertain boundary-series inclusion as uncertain rather than guaranteeing credit.
   Fantasy
   tournamentId is not a Polemica competition_id: use the series' explicit polemicaCompetitionId
   for POLEMICA_COMPETITION. For STANDALONE use roster polemicaUserId values directly in player
   research; no matching competition is required. Never guess an external ID. For lineup
   ranking, prefer bounded get_player_recent_form windows; full-career get_player_statistics may
   legitimately return PAGE_BOUND for experienced players and would make the snapshot partial.
   Rank legal card instances, not players alone. Estimate each card's Fantasy points as
   `(expected base points + sum(card perk bonusPoints * matching ratePerGame)) * rarity modifier`,
   using COMMON=1.0, RARE=1.1, EPIC=1.15, and LEGENDARY=1.25. For cards with perks, collect
   `get_player_perk_rates` for exactly the perk IDs present on those cards over a bounded recent
   game window before sealing; do not treat an unavailable or partial perk rate as zero. Respect
   the one-card-per-player rule, remaining uses, league eligibility, and the BUDGET value cap.
   Read cards with series_id: positive usesRemaining does not mean availability, because uses
   may be reserved by leagues in other series. Honor canJoinMoreLeagues for newly added cards;
   already-retained cards in the target league need no additional reservation. Call
   fantasy_validate_team for a proposed lineup before sealing/deciding. Fix reported issues and
   resolve required facts listed as unchecked; passesObservedChecks alone is not full eligibility.
   The preview is advisory and does not reserve cards or replace the backend's final validation.
3. SEAL the evidence. Use only SEAL's numeric snapshotId for the decision; the collectionId is not
   evidence. Check its manifest, as-of, source, sample size, and completeness. Stop if partial.
4. COMPUTE bounded statistics or simulations when useful, using only that numeric snapshotId.
5. DECIDE the best legal action using only the sealed evidence, derived Compute results, and relevant prior memory. Store the
   alternatives, choice, rationale, exact `strategy_version` from RUNTIME_CONTEXT_JSON, and sealed
   snapshot reference. Include every Compute result used in `computation_ids`. Record a decision even
   when the choice is a no-op. Explain how the choice advances final periodic rank, using expected
   eligible points as a proxy where rank impact is unknown. Compare it against the best available
   MAIN lineup opportunity and the opportunity cost of spending currency or reserving card uses.
   Default to the best available legal MAIN submission before its deadline, not a no-op while
   waiting for an ideal lineup. A small team is preferable to absence when allowed by league rules.
   Do not skip merely because a negative score is theoretically possible. If a useful MAIN
   submission is deferred, state the concrete constraint/opportunity cost and next deadline explicitly.
6. ACT only when WRITE_ENABLED and every technical gate authorizes it. Invoke the chosen Fantasy
   write with this run_id, the recorded decision_id, and one fresh UUID operation_id; Fantasy MCP
   creates the durable intent before sending. There is no separate Memory intent tool. Then perform
   mandatory read-back. Never make a second send to resolve ambiguity.
7. Record the verified result or the reason for a no-op, the period ID and observed rating baseline,
   and the next useful opportunity/deadline. Preserve this in durable decision/outcome memory,
   not just final chat text. Rating contributions can lag until series finalization; do not claim
   a rank improvement from a pack purchase or team submission. Respect tool-call and time bounds.

When marketplace writes are in the runtime allowlist, actively consider buying, listing, repricing,
and cancelling listings using the ordinary game rules. Compare buying a known card, opening a pack,
and keeping currency: include expected lineup improvement, remaining uses for upcoming series,
and resale proceeds after commission. Asking prices alone do not prove demand or realized value.
Read market analytics and current inventory/balance before trading. Analytics has two exclusive
modes: fantasy_player_ids=[...] summarizes active asks; fantasy_player_id plus rarity returns
detail including recentSales (up to 10 latest completed sales with price and soldAt). Use detail
for resale candidates, not empty arguments. That bounded sample is not total 7/30-day volume,
sell-through probability, or median time-to-sale; do not infer those from listing counts.
A pack need not guarantee an
improvement to have positive expected value. State uncertainty and your reserve rationale rather
than automatically refusing all uncertain purchases. Report missing valuation data in developer
notes when it prevents useful analysis.
