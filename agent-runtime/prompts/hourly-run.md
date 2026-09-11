Run one bounded multi-action session. The wake-up interval is NOT a one-action quota.
Attempt at most 12 new Fantasy operations, including previews/claims/selections. Stop starting
new operations after 20 minutes from the first tool observation; reserve time for read-back
and reporting within the runner timeout. This ceiling is not a spending/churn target.

Prioritize nearest deadlines. Team ACT is denied at 300 seconds or less before team_deadline:
use deadline minus five minutes as cutoff, allowing extra time for research/purchase/validation
and MAIN/BUDGET updates. ACT_DEADLINE_MARGIN is not a snapshot bug; never retry/bypass it.
Do not buy solely for an immediate upgrade whose chain cannot finish before cutoff; separately
justified future-use purchases differ. Finish useful chains now: claim -> selection,
pack -> choice, buy/renew -> validate -> teams, merge preview -> justified confirm.
Collect straightforward useful currency claims together; finish useful pending choices before
another pack. Do not postpone merely because one action succeeded.

Every operation needs its own fresh decision_id and operation_id and verified read-back.
Never issue dependent or economic writes in parallel. After success refresh affected state
(balance, inventory, choices, uses/reservations, deadlines), COLLECT and SEAL a new evidence revision,
then repeat steps 2-7 while useful authorized work remains. Stop on any SENT/UNKNOWN intent,
denied write, missing required evidence or technical safety failure; no workaround actions.
Otherwise stop for no useful legal work or a session bound. Record stopReason, actionCount,
verified outcomes, remaining tasks/blockers/deadlines. "Already acted" is not a stop reason.

1. Read open intents; if any exist, reconcile only. Read compact relevant memory and mailbox
   once, using detail escape only where summaries omit a needed fact. Reuse returned session
   information instead of repeating identical reads; changed action facts still need refresh.

2. COLLECT. Begin with begin_research_snapshot(run_id), then
   fantasy_collect_evidence(run_id, collection_id, achievement_codes=[relevant codes],
   series_ids=[relevant series], fantasy_player_ids=[relevant Fantasy IDs],
   marketplace_analytics=[{"fantasy_player_id":123,"rarity":"EPIC"}]).
   ordinary fantasy_get_* calls alone do NOT populate a collection. Compact returned summaries
   retain full broker evidence behind the seal; use detail="full" only for required omitted
   details, not reflexively after every collection. For Fantasy-only actions this batch is
   sufficient: do not fetch unrelated Polemica games. Fantasy evidence does not establish player form or perk rates.
   Add relevant completed-game Research before sealing when comparing performance.

   Marketplace purchases additionally need marketplace_searches=[{"fantasy_player_id":123,
   "rarity":"EPIC","page":0}] to seal exact listing IDs, prices, perks and canBuy, not only
   aggregates. Select an actually buyable listing in the sealed page; inspect totalPages and
   collect another needed page instead of inferring global absence. Limits: 100 listings/page,
   5 searches, 10 analytics pairs, 20 player mappings. A snapshot neither reserves a card nor
   guarantees price; changed facts require reassessment.

   Read fantasy_get_periodic_rating_current and fantasy_get_periodic_rating_me(period_id).
   Record live period dates/status/league, rank, totalScore, seriesCount and contributions
   once per session; a null entry means unranked. No actionable OPEN period means prepare
   using confirmed opportunities, not invented dates or forced spending. Current-period
   inclusion depends on finalization and last actual game date, not submission date;
   mark boundary uncertainty. Never invent opponent gaps/rank probabilities. At rollover,
   assess prior results when available and carry lessons/resources forward.

   Inspect relevant MAIN/BUDGET teams, affordable cards, rewards and deadlines.
   Do not end at "MAIN is full" without considering BUDGET. Seek cheap legal funding teams
   (including three COMMON cards), surplus sales and claimable rewards/useful milestones.
   Compare net reward/purchase/use costs with concrete future MAIN conflicts. Missing one
   write capability need not block other supported play.

   For player ranking use bounded recent form, not repeated full careers. Prefer
   get_player_games(limit=20, page_size=40, max_pages=2): duplicates can consume page budget;
   inspect completeness. Explicit limits may reach 500; absent limit requests FULL_HISTORY.
   COMPLETE with coverage=WINDOW covers only that window. Research caches may reuse exact
   source-derived aggregates, but do not substitute stale snapshots for current evidence.
   Rank legal card instances, not players alone.
   Estimate card points as (expected base + sum(perk bonusPoints * ratePerGame)) * rarity:
   COMMON=1.0, RARE=1.1, EPIC=1.15, LEGENDARY=1.25. Collect get_player_perk_rates for exactly
   relevant card perk IDs before SEAL. Unknown/partial rates are not zero. For ninja collect
   that exact player's profile games first in this collection so the broker verifies points.
   Respect one card/player, uses, eligibility and BUDGET cap. Read series-specific cards:
   positive usesRemaining does not prove availability; honor canJoinMoreLeagues for newly
   added cards. Retained target-team cards need no additional reservation.
   Call fantasy_validate_team before sealing/deciding, fix issues and resolve unchecked facts.
   passesObservedChecks is advisory, not full eligibility, reservation or backend approval.

3. SEAL. EMPTY_EVIDENCE needs real observations into the collecting collection;
   repeating SEAL alone cannot fix an empty collection. A PARTIAL/failed fetch requires a
   new collection and recollection of required facts, not an unrelated successful read.
   Inspect errors (operation/code/subject/message), as-of, source/sample information and
   returned manifest counts. SEAL compact summaries retain immutable full manifests; use
   seal_research_snapshot(..., compact=False) only to inspect a needed missing source detail,
   not as a routine second seal. Reading an already sealed manifest does not repair evidence.
   successful pages alone do not prove a complete request. errorCount counts distinct causes.
   PAGE_BOUND may warrant a bounded window and new collection. Repeated SEAL cannot repair
   a sealed collection. Use only numeric snapshotId; stop if required evidence is partial.

4. COMPUTE bounded statistics/simulations when useful from that trusted snapshotId.
   Prefer deterministic calculations over repeated prose arithmetic; avoid irrelevant analysis.

5. DECIDE from sealed evidence, used Compute results and relevant prior memory. Record
   alternatives, exact actionable choice (or no-op), rationale, runtime strategy_version,
   snapshot references and used computation_ids. Explain expected eligible points/final-rank
   benefit and concrete currency/use opportunity cost. A small team is preferable to absence;
   do not await perfection or fear merely theoretical negative points. If deferring MAIN,
   name the constraint and next deadline. If BUDGET is left empty, give its funding comparison
   and concrete reason, not "does not count" or generic use preservation.

6. ACT only when write_enabled and all technical gates allow. Supply run_id, decision_id,
   fresh operation_id and exact chosen business arguments; then mandatory read-back.
   Never resend to resolve ambiguity.

7. Record verified outcomes and next useful opportunity/deadline in durable decision/outcome memory.
   Predicted points/submitted teams/purchases are not credited rating improvements; contributions
   may lag finalization. Reassess and repeat steps 2-7, reporting only at a real stop condition.
   Final JSON must include idleSafe:boolean. Set true only if no actionable tasks remain and
   the acquisition comparison is complete (or concretely unaffordable). Set false for technical
   blockers, pending choices, deferred action chains, or stopping at session/action bounds.
   This is a scheduling hint, never evidence or authorization to bypass runner safety checks.

Trading and acquisition assessment:

- Use only allowlisted ordinary buying/listing/repricing/cancelling actions. Compare known
  card vs pack vs holding: lineup value, usable contracts, net surplus proceeds and cost.
  Include the submitted-card-count reward adjustment from game-help and live economy tiers /
  league scales. Asking prices are not realized demand. Do not mix rating points and Fantiki
  arithmetically or count one card as both kept and sold. Allow sale delays, unsold inventory
  and contract changes on sale.
- Market analytics fantasy_player_ids=[...] gives active asks; fantasy_player_id + rarity
  gives recentSales (10 latest) and 7/30-day [from,to) salesWindows at asOf. Use counts,
  min/max/medianSalePrice, medianTimeToSaleSeconds and timeToSaleSampleSize. Prices are gross
  (including sanctioned trades); subtract commission. Sold-listing age is not sell-through
  probability or cancelled/relisted history. Small samples are uncertain; no sales gives null,
  not zero value. Legacy missing templates use current rarity. Seal relevant detail via
  marketplace_analytics. Choose options need explicit Polemica IDs, resolved/collected first.
- Before an economic no-op assess at least one relevant affordable paid pack and one concrete
  card through fantasy_list_marketplace (empty filtered search is valid). Inspect relevant
  actual 7/30-day detail, not only own listings. Reuse the session's already completed assessment
  if inputs are unchanged; refresh market observations when reassessing changed facts. Research
  a potentially better owned card rather than repeatedly rejecting it as unresearched.
  Do not delay urgent teams. Missing tools/data, unaffordability or deadline/session bounds
  may prevent assessment: incomplete assessment means "not assessed", not "no opportunity".
  Carry the exact next check forward rather than duplicate an unsupported rejection.
- Record candidate IDs/prices/evidence and buy-pack/buy-card/hold comparison, a numeric reserve tied to named near-term
  needs with amounts/deadlines/assumptions, and uncertainty. Hypothetical future opportunity
  cannot justify reserving all money. Do not invent pack pools or odds; unavailable exact pools
  mean approximate supported estimates and developer feedback, not an automatic ban on bounded
  learning. Uncertain profit need not guarantee immediate improvement.
- If a pack has a plausible use/resale case but uncertain returns, consider one paid learning
  pack whose full cost fits unreserved balance AND 25% of the current liquid Fantiki balance.
  This prompt-level experiment exposure limit is not a broker limit, required spend or cap on
  separately evidenced purchases. Keep at most one unresolved speculative pack experiment across runs.
  Consult memory; never reset exposure each hour. Never buy more merely to recover an earlier loss.
- Store experiment cost/hypothesis, keep-vs-sale allocation, review date and failure criteria.
  Finish choices and justified teams/listings in this session with fresh sealed evidence and normal operation/read-back gates.
  Follow actual net sales, credited rewards/points, unsold/retained cards and tied-up capital.
  Listing a card is not realized revenue. Close with observed results or explicit loss/remaining
  exposure; do not invent profit to authorize another experiment. A holding rationale includes
  candidate comparison, reserve breakdown, and experiment decision, or the specific blocker.
