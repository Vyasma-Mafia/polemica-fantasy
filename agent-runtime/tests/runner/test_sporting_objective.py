from pathlib import Path


def test_periodic_objective_is_persistent_and_has_live_progress_loop():
    prompts = Path(__file__).resolve().parents[2] / "prompts"
    system = (prompts / "system.md").read_text()
    hourly = (prompts / "hourly-run.md").read_text()
    assert "Finish as high as possible in each periodic rating" in system
    assert "BUDGET is an important funding channel for MAIN" in system
    assert "three COMMON" in system
    assert "available achievement rewards" in system
    assert 'Do not end at "MAIN is full" without considering BUDGET' in hourly
    assert "If BUDGET is left empty" in hourly
    assert "Expected additional eligible points" in system
    assert "This objective never overrides the rules below" in system
    assert "fantasy_get_periodic_rating_current" in hourly
    assert "fantasy_get_periodic_rating_me(period_id)" in hourly
    assert "null entry means unranked" in hourly
    assert "durable decision/outcome memory" in hourly
    assert "almost any submitted player/team earns positive points" in system
    assert "possibility of negative points is not a reason to skip" in system
    assert "A small team is preferable to absence" in hourly
    assert "rosterReward=ceil(B*n/3)" in system
    assert "floor(rosterReward * effectiveLeagueRewardScalePercent / 100)" in system
    assert "NOT Fantasy points or periodic-rating" in system
    assert "submitted-card-count reward adjustment" in hourly


def test_acquisition_policy_requires_research_and_bounded_learning_without_relaxing_gates():
    prompts = Path(__file__).resolve().parents[2] / "prompts"
    system = (prompts / "system.md").read_text()
    hourly = (prompts / "hourly-run.md").read_text()
    assert "testable prior, not a guaranteed return" in system
    for requirement in (
        'assessment means "not assessed"',
        "at least one relevant affordable paid pack",
        "fantasy_list_marketplace",
        "numeric reserve tied to named near-term",
        "25% of the current liquid Fantiki balance",
        "one unresolved speculative pack experiment across runs",
        "Listing a",
        "reserve breakdown, and experiment decision",
        "Do not invent pack pools",
        "Never buy more merely to recover an earlier loss",
    ):
        assert requirement in hourly
    assert "Stop on any SENT/UNKNOWN intent" in hourly
    assert "fresh sealed evidence and normal operation/read-back gates" in hourly
    assert "missing required evidence or technical safety" in hourly


def test_compact_prompt_preserves_detail_escape_and_fail_open_scheduling():
    prompts = Path(__file__).resolve().parents[2] / "prompts"
    system = (prompts / "system.md").read_text()
    hourly = (prompts / "hourly-run.md").read_text()
    assert len((system + hourly).split()) < 2700
    assert "Memory compact=False" in system
    assert 'detail="full"' in hourly
    assert "once at session start" in system
    assert "normally <=150 words of rationale" in system
    assert "full evidence storage, freshness, sealing or read-back" in system
    assert "idleSafe:boolean" in hourly
    assert "Set false for technical" in hourly
    assert "pending choices, deferred action chains" in hourly
    assert "session/action bounds" in hourly
    assert "300 seconds or less" in hourly
    assert "ACT_DEADLINE_MARGIN" in hourly
    assert "do not substitute stale snapshots" in hourly
