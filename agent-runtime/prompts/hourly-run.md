Perform one bounded hourly turn.

1. Read open operation intents. If any exist, switch immediately to reconciliation-only behavior.
2. COLLECT current Fantasy state and relevant Polemica evidence within bounded tool limits. Fantasy
   tournamentId is not a Polemica competition_id: resolve the latter by a unique exact name match
   from list_competitions, and never copy the former into a Research competition call. For lineup
   ranking, prefer bounded get_player_recent_form windows; full-career get_player_statistics may
   legitimately return PAGE_BOUND for experienced players and would make the snapshot partial.
   Rank legal card instances, not players alone. Estimate each card's Fantasy points as
   `(expected base points + sum(card perk bonusPoints * matching ratePerGame)) * rarity modifier`,
   using COMMON=1.0, RARE=1.1, EPIC=1.15, and LEGENDARY=1.25. For cards with perks, collect
   `get_player_perk_rates` for exactly the perk IDs present on those cards over a bounded recent
   game window before sealing; do not treat an unavailable or partial perk rate as zero. Respect
   the one-card-per-player rule, remaining uses, league eligibility, and the BUDGET value cap.
3. SEAL the evidence. Use only SEAL's numeric snapshotId for the decision; the collectionId is not
   evidence. Check its manifest, as-of, source, sample size, and completeness. Stop if partial.
4. COMPUTE bounded statistics or simulations when useful, using only that numeric snapshotId.
5. DECIDE the best legal action using only the sealed evidence, derived Compute results, and relevant prior memory. Store the
   alternatives, choice, rationale, exact `strategy_version` from RUNTIME_CONTEXT_JSON, and sealed
   snapshot reference. Include every Compute result used in `computation_ids`. Record a decision even
   when the choice is a no-op.
6. ACT only when WRITE_ENABLED and every technical gate authorizes it. Invoke the chosen Fantasy
   write with this run_id, the recorded decision_id, and one fresh UUID operation_id; Fantasy MCP
   creates the durable intent before sending. There is no separate Memory intent tool. Then perform
   mandatory read-back. Never make a second send to resolve ambiguity.
7. Record the verified result or the reason for a no-op. Respect tool-call and time bounds.

When marketplace writes are in the runtime allowlist, actively consider buying, listing, repricing,
and cancelling listings using the ordinary game rules. Compare buying a known card, opening a pack,
and keeping currency: include expected lineup improvement, remaining uses for upcoming series,
and resale proceeds after commission. Asking prices alone do not prove demand or realized value.
Read market analytics and current inventory/balance before trading. A pack need not guarantee an
improvement to have positive expected value. State uncertainty and your reserve rationale rather
than automatically refusing all uncertain purchases. Report missing valuation data in developer
notes when it prevents useful analysis.
