from __future__ import annotations

import datetime as dt
from pathlib import Path

import pytest

from polemica_agent.mcp_runtime.registry import MCP_SERVERS
from polemica_agent.runner.codex import build_command, scrubbed_environment
from polemica_agent.runner.policy import GateError, OrchestrationGuard, Phase


def test_command_is_fresh_read_only_and_has_only_required_mcps(tmp_path: Path) -> None:
    urls = {name: f"http://127.0.0.1:88{index}/mcp" for index, name in enumerate(MCP_SERVERS, 11)}
    command = build_command(binary="codex", model="test-model", workspace=tmp_path, mcp_urls=urls)
    joined = " ".join(command)
    assert command[:2] == ["codex", "exec"]
    assert "--ignore-user-config" in command
    assert "--ephemeral" in command
    assert "--sandbox read-only" in joined
    assert "dangerously-bypass" not in joined
    for kind in MCP_SERVERS:
        assert f"mcp_servers.{kind}.required=true" in command
        assert f'mcp_servers.{kind}.url="{urls[kind]}"' in command


def test_environment_drops_upstream_secrets() -> None:
    result = scrubbed_environment({
        "PATH": "/bin", "CODEX_HOME": "/safe-auth", "POLEMICA_PASSWORD": "bad",
        "FANTASY_BEARER_TOKEN": "bad", "TELEGRAM_BOT_TOKEN": "bad",
    })
    assert result == {"PATH": "/bin", "CODEX_HOME": "/safe-auth"}


def test_collect_seal_decide_act_gate() -> None:
    guard = OrchestrationGuard(write_enabled=True)
    guard.seal("snapshot-1")
    guard.decide(7, "snapshot-1")
    now = dt.datetime.now(dt.timezone.utc)
    guard.authorize_act(
        snapshot_id="snapshot-1", decision_id=7, now=now, trusted_now=now,
        deadline=now + dt.timedelta(minutes=10), open_intents=[],
    )
    assert guard.phase is Phase.ACT


@pytest.mark.parametrize("write_enabled,open_intents", [(False, []), (True, [{"state": "UNKNOWN"}])])
def test_act_fails_closed(write_enabled: bool, open_intents: list[dict]) -> None:
    guard = OrchestrationGuard(write_enabled=write_enabled)
    guard.seal("snapshot-1")
    guard.decide(7, "snapshot-1")
    now = dt.datetime.now(dt.timezone.utc)
    with pytest.raises(GateError):
        guard.authorize_act(
            snapshot_id="snapshot-1", decision_id=7, now=now, trusted_now=now,
            deadline=now + dt.timedelta(minutes=10), open_intents=open_intents,
        )
