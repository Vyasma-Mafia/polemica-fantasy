from __future__ import annotations

import anyio
import pytest
from mcp.server.mcpserver.exceptions import ToolError

from polemica_agent.common.operations import DeterministicUpstreamError
from polemica_agent.fantasy_mcp.client import FantasyApiError
from polemica_agent.fantasy_mcp.registry import build_tool_registry
from polemica_agent.mcp_runtime.fantasy_adapter import FantasyRegistryAdapter
from polemica_agent.mcp_runtime.cli import _fantasy_write_allowlist, _handler
from polemica_agent.mcp_runtime.config import MCPConfigError
from polemica_agent.mcp_runtime.registry import (
    FANTASY_WRITE_TOOLS, MCP_SERVERS, RegistryError, ToolPolicy, WriteDenied, build_server,
)


class Handler:
    pass


def handler_for(names: tuple[str, ...]) -> Handler:
    handler = Handler()
    for name in names:
        def make_method(tool_name: str):
            def method(value: int = 1):
                return {"tool": tool_name, "value": value}
            return method
        method = make_method(name)
        method.__name__ = name
        setattr(handler, name, method)
    return handler


def test_registry_has_only_named_domain_tools() -> None:
    all_names = {name for names in MCP_SERVERS.values() for name in names}
    assert all_names
    assert not ({"shell", "http", "request", "sql", "filesystem"} & all_names)


def test_missing_required_handler_fails_startup() -> None:
    with pytest.raises(RegistryError, match="required research handler"):
        build_server("research", object())


def test_sdk_registers_exact_fixed_surface() -> None:
    server = build_server("research", handler_for(MCP_SERVERS["research"]))
    listed = anyio.run(server.list_tools)
    assert {tool.name for tool in listed} == set(MCP_SERVERS["research"])


def test_fantasy_write_is_disabled_even_if_handler_exists() -> None:
    name = FANTASY_WRITE_TOOLS[0]
    server = build_server("fantasy", handler_for(MCP_SERVERS["fantasy"]), policy=ToolPolicy())
    with pytest.raises(ToolError, match="disabled"):
        anyio.run(server.call_tool, name, {"value": 2})


def test_write_rollout_allowlist_denies_unlisted_tool() -> None:
    class AllowWrites:
        def authorize_write(self, _name, _arguments): return None

    allowed, denied = FANTASY_WRITE_TOOLS[:2]
    policy = ToolPolicy(
        write_enabled=True,
        authorizer=AllowWrites(),
        allowed_write_tools=frozenset({allowed}),
    )
    server = build_server("fantasy", handler_for(MCP_SERVERS["fantasy"]), policy=policy)
    assert anyio.run(server.call_tool, allowed, {"value": 2}).content[0].text
    with pytest.raises(ToolError, match="outside the active rollout stage"):
        anyio.run(server.call_tool, denied, {"value": 2})


def test_write_rollout_allowlist_rejects_unknown_name(monkeypatch) -> None:
    monkeypatch.setenv("FANTASY_WRITE_ALLOWLIST", "fantasy_create_team,arbitrary_http")
    with pytest.raises(MCPConfigError, match="unknown Fantasy write tool"):
        _fantasy_write_allowlist()


def test_official_sdk_adapts_exact_fantasy_domain_registry() -> None:
    class Envelope:
        def as_dict(self): return {"ok": True}
    class Service:
        def __getattr__(self, _name):
            return lambda **_kwargs: Envelope()
    adapter = FantasyRegistryAdapter(build_tool_registry(Service()))
    server = build_server("fantasy", adapter)
    listed = anyio.run(server.list_tools)
    assert {tool.name for tool in listed} == set(MCP_SERVERS["fantasy"])
    create = next(tool for tool in listed if tool.name == "fantasy_create_team")
    assert set(create.input_schema["required"]) == {
        "run_id", "operation_id", "decision_id", "series_id", "league_code", "user_card_ids"
    }


def test_fantasy_adapter_omits_sdk_materialized_optional_nulls() -> None:
    seen = None
    class Registry:
        def list_tools(self):
            return [{
                "name": "fantasy_read",
                "description": "read",
                "inputSchema": {
                    "type": "object",
                    "properties": {"optional_id": {"type": "integer"}},
                    "required": [],
                },
            }]
        def names(self): return ("fantasy_read",)
        def call(self, _name, arguments):
            nonlocal seen
            seen = arguments
            return {"ok": True}

    adapter = FantasyRegistryAdapter(Registry())
    assert adapter.fantasy_read() == {"ok": True}
    assert seen == {}


@pytest.mark.parametrize(
    ("error", "expected"),
    [
        (DeterministicUpstreamError("HTTP_403", "sensitive upstream body"), "HTTP_403"),
        (FantasyApiError(503, "HTTP_503", "sensitive upstream body", uncertain=True), "HTTP_503"),
    ],
)
def test_fantasy_adapter_exposes_only_sanitized_error_code(error, expected) -> None:
    class Registry:
        def list_tools(self):
            return [{
                "name": "fantasy_read",
                "description": "read",
                "inputSchema": {"type": "object", "properties": {}, "required": []},
            }]
        def names(self): return ("fantasy_read",)
        def call(self, _name, _arguments): raise error

    adapter = FantasyRegistryAdapter(Registry())
    with pytest.raises(ToolError, match=expected) as raised:
        adapter.fantasy_read()
    assert "sensitive upstream body" not in str(raised.value)


def test_production_factories_use_persistent_broker_adapters(tmp_path, monkeypatch) -> None:
    database = tmp_path / "state" / "agent.sqlite3"
    monkeypatch.setenv("POLEMICA_AGENT_DATABASE", str(database))
    monkeypatch.setenv("FANTASY_API_BASE_URL", "https://fantasy.invalid")
    monkeypatch.setenv("FANTASY_BEARER_TOKEN", "fixture-token")
    fantasy, fantasy_store, authorizer = _handler("fantasy")
    assert fantasy is not None
    assert fantasy_store is not None
    assert authorizer is not None
    fantasy_store.close()

    monkeypatch.setenv("POLEMICA_API_BASE_URL", "https://api.polemica.invalid")
    monkeypatch.setenv("POLEMICA_PROFILE_BASE_URL", "https://polemica.invalid")
    monkeypatch.setenv("POLEMICA_USERNAME", "fixture")
    monkeypatch.setenv("POLEMICA_PASSWORD", "fixture")
    monkeypatch.setenv("POLEMICA_RESEARCH_CACHE", str(tmp_path / "cache"))
    # Even a legacy environment value cannot downgrade the production factory.
    monkeypatch.setenv("POLEMICA_RESEARCH_ALLOW_VOLATILE_SNAPSHOTS", "true")
    research, journal, no_authorizer = _handler("research")
    assert type(research.service.snapshots.journal).__name__ == "DurableResearchSnapshotJournal"
    assert journal is research.service.snapshots.journal
    assert no_authorizer is None
    journal.close()
