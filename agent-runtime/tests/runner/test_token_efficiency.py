import datetime as dt
import json
from dataclasses import replace

import pytest

from polemica_agent.runner.change_gate import ChangeGate, evaluate
from polemica_agent.runner.logging import RedactedJsonlLog, safe_event
from polemica_agent.runner.orchestrator import run_once
from test_orchestrator import settings


def test_usage_whitelist_does_not_weaken_secret_filter():
    event = {"type": "turn.completed", "usage": {
        "input_tokens": 123, "cached_input_tokens": 100, "output_tokens": 20,
        "reasoning_output_tokens": True, "cache_write_input_tokens": "secret",
        "access_token": "secret"}, "nested": {"input_tokens": "secret"}}
    clean = safe_event(event)
    assert clean["usage"]["input_tokens"] == 123
    assert clean["usage"]["cached_input_tokens"] == 100
    assert clean["usage"]["reasoning_output_tokens"] == "[REDACTED]"
    assert "secret" not in json.dumps(clean)
    assert clean["nested"]["input_tokens"] == "[REDACTED]"
    assert safe_event({"type": "item.completed", "usage": {"input_tokens": 4}})["usage"]["input_tokens"] == "[REDACTED]"
    assert safe_event({"type": "turn.completed", "usage": {"input_tokens": -1}})["usage"]["input_tokens"] == "[REDACTED]"


def test_log_totals_and_explicit_idle_safe(tmp_path):
    with RedactedJsonlLog(tmp_path / "test.jsonl") as log:
        log.write_line(json.dumps({"type": "turn.completed", "usage": {"input_tokens": 10}}))
        log.write_line(json.dumps({"type": "turn.completed", "usage": {"input_tokens": 20}}))
        log.write_event({"type": "item.completed", "item": {"type": "agent_message", "text": '{"idleSafe":true}'}})
        assert log.usage == {"input_tokens": 30}
        assert log.idle_safe
        log.write_event({"type": "turn.completed", "usage": None})
        assert log.usage == {"input_tokens": 30}
        log.write_event({"type": "item.completed", "item": {"type": "agent_message", "text": "unfinished"}})
        assert not log.idle_safe


NOW = dt.datetime(2026, 9, 11, tzinfo=dt.timezone.utc)


def baseline(state):
    _, _, signature = evaluate(state, None, "config", NOW, 14400)
    return {"signature": signature, "configHash": "config", "completedAt": NOW.timestamp(), "idleSafe": True}


def test_change_gate_semantics_and_mandatory_exploration():
    state = {"fantasy_list_open_series": [], "balance": 100, "observedAt": "old"}
    saved = baseline(state)
    assert evaluate({**state, "observedAt": "new"}, saved, "config", NOW, 14400)[:2] == (False, "UNCHANGED")
    assert evaluate({**state, "balance": 99}, saved, "config", NOW, 14400)[0]
    assert evaluate(state, saved, "changed", NOW, 14400)[0]
    assert evaluate(state, saved, "config", NOW + dt.timedelta(hours=4), 14400)[0]
    assert evaluate(state, saved, "config", NOW - dt.timedelta(seconds=1), 14400)[0]
    assert evaluate(state, {**saved, "idleSafe": False}, "config", NOW, 14400)[0]
    for invalid in (float("nan"), float("inf"), True, None):
        assert evaluate(state, {**saved, "completedAt": invalid}, "config", NOW, 14400)[0]


@pytest.mark.parametrize("deadline", [None, "broken", "2026-09-11T03:00:00", "2026-09-11T02:00:00Z", "2026-09-10T00:00:00Z"])
def test_unknown_or_near_deadline_never_skips(deadline):
    state = {"fantasy_list_open_series": [{"seriesId": 1, "teamDeadline": deadline}]}
    assert evaluate(state, baseline(state), "config", NOW, 14400)[0]


def test_far_deadline_can_skip_and_roster_changes_wake():
    state = {"fantasy_list_open_series": [{"seriesId": 1, "teamDeadline": "2026-09-11T03:00:00Z"}], "players": [1]}
    saved = baseline(state)
    assert not evaluate(state, saved, "config", NOW, 14400)[0]
    assert evaluate({**state, "players": [2]}, saved, "config", NOW, 14400)[0]


def test_gate_success_skip_force_error_and_failed_run(tmp_path):
    cfg = replace(settings(tmp_path), change_gate_enabled=True)
    seen = []
    class Memory:
        def get_open_intents(self): return []
        def start_run(self, **kw): seen.append("start"); return kw["run_id"]
        def finish_run(self, *a, **kw): seen.append(a[1])
        def close(self): pass
    def invoke(command, prompt, log, **kw):
        assert 'model_reasoning_effort="medium"' in command
        log.write_event({"type": "item.completed", "item": {"type": "agent_message", "text": '{"idleSafe":true}'}})
    kwargs = dict(probe=lambda _: None, memory_factory=lambda _: Memory(), invoker=invoke,
                  state_reader=lambda _: {"fantasy_list_open_series": [], "cards": []})
    run_once(cfg, **kwargs)
    assert run_once(cfg, **kwargs) == "SKIPPED_UNCHANGED"
    assert seen == ["start", "SUCCEEDED"]
    run_once(cfg, **kwargs, force=True)
    assert len(seen) == 4
    def broken(_): raise ValueError("unavailable")
    run_once(cfg, **{**kwargs, "state_reader": broken})
    assert len(seen) == 6
    def failed(*a, **kw): raise RuntimeError("model failed")
    with pytest.raises(RuntimeError):
        run_once(cfg, **{**kwargs, "invoker": failed})
    assert ChangeGate(cfg.log_dir.parent / "wake-baseline.json").load()["idleSafe"] is False


def test_pending_intents_bypass_wake_reads(tmp_path):
    cfg = replace(settings(tmp_path), change_gate_enabled=True)
    class Memory:
        n = 0
        def get_open_intents(self):
            self.n += 1
            return [{"operationId": "op"}] if self.n == 1 else []
        def start_run(self, **kw): return kw["run_id"]
        def finish_run(self, *a, **kw): pass
        def close(self): pass
    def forbidden(_): raise AssertionError("should not read broad state during reconciliation")
    run_once(cfg, probe=lambda _: None, memory_factory=lambda _: Memory(),
             invoker=lambda *a, **kw: None, state_reader=forbidden)
