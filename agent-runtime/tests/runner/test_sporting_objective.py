from pathlib import Path


def test_periodic_objective_is_persistent_and_has_live_progress_loop():
    prompts = Path(__file__).resolve().parents[2] / "prompts"
    system = (prompts / "system.md").read_text()
    hourly = (prompts / "hourly-run.md").read_text()
    assert "Finish as high as possible in each periodic rating" in system
    assert "BUDGET is secondary" in system
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
