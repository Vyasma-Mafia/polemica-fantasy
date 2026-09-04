from __future__ import annotations

import anyio
import pytest
from mcp.server.mcpserver.exceptions import ToolError

from polemica_agent.fantasy_mcp.registry import build_tool_registry
from polemica_agent.mcp_runtime.fantasy_adapter import FantasyRegistryAdapter
from polemica_agent.mcp_runtime.cli import _handler
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
