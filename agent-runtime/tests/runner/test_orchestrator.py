from __future__ import annotations

import json
from pathlib import Path

from polemica_agent.runner.orchestrator import build_prompt, run_once
from polemica_agent.runner.settings import RuntimeSettings


PROMPTS = Path(__file__).resolve().parents[2] / "prompts"


def settings(tmp_path: Path) -> RuntimeSettings:
    return RuntimeSettings(
        workspace=tmp_path / "workspace", log_dir=tmp_path / "logs",
        lock_path=tmp_path / "run.lock",
        prompt_dir=PROMPTS, model="test-model", strategy_version="test-strategy-v1",
        timeout_seconds=30, write_enabled=False,
        mcp_urls={
            "fantasy": "http://127.0.0.1:8811/mcp",
            "research": "http://127.0.0.1:8812/mcp",
            "compute": "http://127.0.0.1:8814/mcp",
            "memory": "http://127.0.0.1:8813/mcp",
        }, codex_binary="codex",
    )


def test_open_intents_force_reconcile_only(tmp_path: Path) -> None:
    prompt = build_prompt(settings(tmp_path), "run", [{"operationId": "op", "state": "UNKNOWN"}])
    assert "RECONCILE_ONLY" in prompt
    assert "Do not create a team" in prompt
    assert '"strategy_version":"test-strategy-v1"' in prompt
    assert "There is no separate operation-intent tool" in prompt
    assert "Fantasy `tournamentId` is an internal Fantasy identifier" in prompt
    assert '"fantasy_write_allowlist":[]' in prompt


def test_run_once_probes_and_mocks_codex_invocation(tmp_path: Path) -> None:
    seen: dict[str, object] = {}
    def probe(urls: dict[str, str]) -> None:
        seen["urls"] = urls
    def invoke(command, prompt, log, **kwargs):
        seen["command"] = command
        seen["prompt"] = prompt
        seen["environment"] = kwargs["environment"]
        log.write_line(json.dumps({"type": "test", "Authorization": "Bearer secret"}))
    class Memory:
        def get_open_intents(self): return []
        def start_run(self, **metadata): return metadata["run_id"]
        def finish_run(self, run_id, status, summary, *, require_decision=False):
            seen["status"] = status
            seen["require_decision"] = require_decision
        def close(self): pass
    run_id = run_once(
        settings(tmp_path), probe=probe, invoker=invoke,
        memory_factory=lambda _url: Memory(),
    )
    assert seen["urls"] == settings(tmp_path).mcp_urls
    assert "--ignore-user-config" in seen["command"]
    assert "POLEMICA_PASSWORD" not in seen["environment"]
    log_text = (tmp_path / "logs" / f"{run_id}.jsonl").read_text()
    assert "secret" not in log_text
    assert "[REDACTED]" in log_text
    assert seen["status"] == "SUCCEEDED"
    assert seen["require_decision"] is True


def test_reconciliation_run_does_not_require_a_new_decision(tmp_path: Path) -> None:
    seen: dict[str, object] = {}

    class Memory:
        def get_open_intents(self):
            return [{"operationId": "op", "state": "UNKNOWN"}]
        def start_run(self, **metadata):
            return metadata["run_id"]
        def finish_run(self, run_id, status, summary, *, require_decision=False):
            seen["status"] = status
            seen["require_decision"] = require_decision
        def close(self):
            pass

    def invoke(_command, _prompt, _log, **_kwargs):
        return None

    run_once(
        settings(tmp_path), probe=lambda _urls: None, invoker=invoke,
        memory_factory=lambda _url: Memory(),
    )
    assert seen["status"] == "SUCCEEDED"
    assert seen["require_decision"] is False
