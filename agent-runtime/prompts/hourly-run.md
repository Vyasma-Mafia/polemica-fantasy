Perform one bounded hourly session with multiple sequential decisions and actions.

The hourly schedule is a wake-up interval, NOT a one-action quota. Repeat steps 2-7
after each verified success while useful authorized work remains. In this session,
attempt at most 12 new Fantasy operations (previews, claims and selections also count).
This is a ceiling, not a target: never spend or churn teams just to fill it. Stop starting
new operations after 20 minutes elapsed from the first tool observation; reserve time
for mandatory read-back and final reporting, within the existing runner timeout.

Prioritize nearest deadlines and complete useful dependency chains in this same run:
claim reward -> select pending cards; open pack -> select option; buy/renew a card ->
validate and update MAIN/BUDGET; preview merge -> confirm only if still justified.
Collect all straightforward claimable currency rewards when worthwhile, not one per hour.
Do not postpone a safe, evidenced next step merely because one action already succeeded.
Before opening another pack, finish an existing useful pending choice when possible.

Every operation still needs its own fresh decision_id and operation_id, exact arguments,
and verified read-back. Never issue dependent or economic writes in parallel. After each
action refresh affected inventory, balance, pending choices, uses/reservations and deadlines,
then COLLECT and SEAL a new evidence revision for the next decision. Do not support a later
decision with new facts attached only to an old sealed snapshot. Keep research bounded and
relevant to the next action; do not re-analyze unrelated full histories for every reward.

Stop on any SENT/UNKNOWN intent, denied write, missing required evidence or technical safety
failure; do not turn another action into a workaround. Otherwise stop only when no useful
legal action remains or a session bound is reached. Record a specific stopReason, all
actions and verified outcomes, actionCount, remaining tasks and their blockers/deadlines.
"Already acted this hour" and "continue next turn" alone are not valid stop reasons.
Before an economic no-op, complete the acquisition assessment below, or record the specific
missing tool/data, unaffordability, or urgent deadline/session bound preventing it. Incomplete
assessment means "not assessed", not "no useful opportunity"; carry the exact next check forward.

1. Read open operation intents. If any exist, switch immediately to reconciliation-only behavior.
2. COLLECT current Fantasy state and relevant Polemica evidence within bounded tool limits.
   BEGIN a fresh collection with begin_research_snapshot(run_id), then explicitly call
   fantasy_collect_evidence(run_id, collection_id, achievement_codes=[relevant codes],
   series_ids=[relevant series], fantasy_player_ids=[relevant Fantasy IDs],
   marketplace_analytics=[{"fantasy_player_id":123,"rarity":"EPIC"}]) with only relevant selectors.
   For a proposed marketplace purchase also pass marketplace_searches=[{"fantasy_player_id":123,
   "rarity":"EPIC","page":0}]. This saves exact listings, prices, card/perks and canBuy through
   the existing user API, not just aggregate analytics. It returns up to100 listings per page,
   max5 searches per call. Select a listing actually present and buyable in the sealed page;
   inspect totalPages and collect the needed page if the target is not returned. Do not infer
   global absence from one page. If the price/card/availability changed, reassess before deciding;
   a snapshot is an observation, not a reservation or a server-enforced price guarantee.
   Player mappings are capped at 20 and market pairs at 10. This broker attaches real Fantasy observations;
   ordinary fantasy_get_* calls alone do NOT populate a collection. Use the returned observations.
   For currency claims and other decisions based only on Fantasy state, this batch is sufficient
   evidence to SEAL; do not fetch unrelated Polemica games just to satisfy the evidence gate.
   For player performance comparisons, add the relevant completed-game Research to the same
   collection before SEAL. Fantasy evidence does not establish player form or perk rates.
   Start with fantasy_get_periodic_rating_current and fantasy_get_periodic_rating_me(period_id).
   Read prior decisions/outcomes to keep a plan across runs. Record the period's dates, status,
   league, entry.rank/totalScore/seriesCount, and contributions. A null entry means unranked,
   not a failed tool. If there is no actionable OPEN period (absent, SETTLING, or FINALIZED),
   do not invent dates or force spending: prepare for the next period using confirmed opportunities.
   Identify upcoming MAIN submissions/improvements that may contribute to the current period;
   final inclusion depends on finalization and the last actual game date, not submission date.
   Mark uncertain boundary-series inclusion as uncertain rather than guaranteeing credit.
   Also inspect open BUDGET leagues as funding opportunities, not periodic-point contributions.
   For each relevant open series, assess existing MAIN and BUDGET teams, affordable available cards,
   and deadlines. Once urgent MAIN participation is secured, actively seek a BUDGET submission,
   including a three-COMMON lineup; compare expected Fantiki rewards with acquisition/use costs
   and specific future MAIN conflicts. Do not end at "MAIN is full" without considering BUDGET.
   Read the achievement catalog for claimable rewards or inexpensive useful milestones and assess
   surplus-card sale opportunities after commission. Execute only supported, allowlisted writes;
   note a blocked reward capability without treating it as a blocker for league participation.
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
3. SEAL the evidence. On EMPTY_EVIDENCE collect real broker observations into that still-collecting
   collection and then SEAL; repeating SEAL alone cannot fix an empty collection. If a collection
   became PARTIAL after a failed fetch, start a new collection and recollect the required facts;
   do not mask the failed required source with a successful unrelated read.
   Use only SEAL's numeric snapshotId for the decision; the collectionId is not
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
   If BUDGET is left empty, record its funding comparison and a concrete reason, not simply
   "does not count in the rating" or a generic desire to preserve all card uses.
6. ACT only when WRITE_ENABLED and every technical gate authorizes it. Invoke the chosen Fantasy
   write with this run_id, the recorded decision_id, and one fresh UUID operation_id; Fantasy MCP
   creates the durable intent before sending. There is no separate Memory intent tool. Then perform
   mandatory read-back. Never make a second send to resolve ambiguity.
7. Record the verified result or the reason for a no-op, the period ID and observed rating baseline,
   and the next useful opportunity/deadline. Preserve this in durable decision/outcome memory,
   not just final chat text. Rating contributions can lag until series finalization; do not claim
   a rank improvement from a pack purchase or team submission. Then reassess and repeat steps 2-7
   under the session rules above; emit the final summary only when a stop condition is reached.

When marketplace writes are in the runtime allowlist, actively consider buying, listing, repricing,
and cancelling listings using the ordinary game rules. Compare buying a known card, opening a pack,
and keeping currency: include expected lineup improvement, remaining uses for upcoming series,
and resale proceeds after commission. Asking prices alone do not prove demand or realized value.
Include the submitted-card-count reward adjustment from the game-help rule: one/two/three cards
receive about one third/two thirds/full placement Fantiki before league scaling. Filling an empty
slot may improve both points and currency reward; do not equate securing participation with having
finished improving a lineup. Read fantasy_get_economy_info and the relevant series league rules
for current reward tiers and effective scale when using this benefit in a valuation.
Read market analytics and current inventory/balance before trading. Analytics has two exclusive
modes: fantasy_player_ids=[...] summarizes active asks; fantasy_player_id plus rarity returns
detail including recentSales (up to 10 latest sales) and salesWindows for all completed sales
in 7/30-day [from,to) windows at asOf. Use completedSalesCount, min/max/medianSalePrice and
medianTimeToSaleSeconds with timeToSaleSampleSize for resale candidates. Prices are gross
before fees and include sanctioned trades; apply economy fees to estimate net proceeds.
Time-to-sale covers sold listings since creation only, not cancelled/relisted history or
sell-through probability. Small samples are uncertain; no sales means null prices/duration,
not a zero-valued card. Legacy sales without a saved template use current rarity.
Persist relevant detail through fantasy_collect_evidence marketplace_analytics before SEAL.
For choose-pack options lacking polemicaUserId, call fantasy_get_player(fantasy_player_id),
collect the mapping with fantasy_player_ids, then use its explicit Polemica ID for Research.
A pack need not guarantee an
improvement to have positive expected value. State uncertainty and your reserve rationale rather
than automatically refusing all uncertain purchases. Report missing valuation data in developer
notes when it prevents useful analysis.

Acquisition assessment and bounded learning:

- Before deciding against spending, assess at least one relevant affordable paid pack and one
  concrete-card alternative through fantasy_list_marketplace (an empty filtered search is a
  valid result). Read actual 7/30-day detail for relevant player/rarity candidates; reading only
  your own listings is not a market search. Prioritize current/upcoming rosters and plausible
  resale demand. Record candidate IDs, prices, evidence, and why buy-pack, buy-card, or hold wins.
  If a potentially better owned card lacks perk/form evidence, fetch bounded relevant Research
  rather than repeatedly rejecting it because you did not research it. Do not delay urgent teams.
- Compare expected lineup improvement, usable future contracts, expected surplus proceeds after
  commission, and pack cost. Do not add rating points directly to Fantiki or double-count a card
  as both kept and immediately sold. Allow for sale delay, unsold cards, and contract changes on
  sale. Do not invent pack pools, selection probabilities, or equal odds from names/rarity layout.
  If the exact pool is unavailable, label the estimate approximate, use supported comparisons,
  and report the missing API information; that alone does not forbid a bounded learning purchase.
- State a numeric reserve tied to named near-term participation, acquisition, or renewal needs,
  with amounts and deadlines/assumptions. A hypothetical better future opportunity is not a reason
  to reserve the entire balance. Uncertain game profit does not require guaranteed improvement.
- When returns remain uncertain but the pack has a plausible use/resale case, consider one paid
  pack as a learning experiment. At purchase, its full price must fit both the unreserved balance
  and 25% of the current liquid Fantiki balance. This is a prompt-level experimental exposure
  limit, not a broker limit, mandatory spending quota, or cap on separately evidenced purchases.
  Keep at most one unresolved speculative pack experiment across runs; consult durable memory
  instead of resetting this limit each hour. Never buy more merely to recover an earlier loss.
- Record the experiment's pack/cost, hypothesis, expected use versus sale allocation, review date,
  and failure criteria in decision/outcome memory. Finish pending selection, assess the obtained
  cards, and pursue justified lineup updates or surplus listings in the same session when possible,
  each with fresh sealed evidence and normal operation/read-back gates. Follow up across runs on
  actual net sales, credited rewards/points, retained/unsold cards and tied-up capital. Listing a
  card is not realized revenue. Close the experiment with observed results or an explicit loss/
  remaining-exposure assessment; do not call it profitable just to permit another experiment.
- Apply evidence standards to holding currency too. A no-op rationale must include the pack and
  card comparison, reserve breakdown, and experiment decision (or the concrete assessment blocker).
  Refresh market observations when reassessing; do not copy an old unsupported rejection.
