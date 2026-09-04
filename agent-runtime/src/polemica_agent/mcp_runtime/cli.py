from __future__ import annotations

import argparse
import os
from pathlib import Path
from typing import Any

from .config import MCPConfigError, ServerBinding, required_https_origin
from .memory_tools import MemoryTools
from .registry import FANTASY_WRITE_TOOLS, ToolPolicy, WriteAuthorizer, build_server


def _required(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise MCPConfigError(f"required environment variable is absent: {name}")
    return value


def _handler(kind: str) -> tuple[Any, Any | None, WriteAuthorizer | None]:
    if kind == "memory":
        from polemica_agent.memory_mcp.service import MemoryService
        service = MemoryService(Path(_required("POLEMICA_AGENT_DATABASE")))
        return MemoryTools(service), service, None
    if kind == "research":
        from polemica_agent.research_mcp.cache import RawPayloadCache
        from polemica_agent.research_mcp.client import HttpClientConfig, HttpPolemicaClient
        from polemica_agent.research_mcp.service import ResearchService
        from polemica_agent.research_mcp.snapshots import SnapshotCoordinator
        from polemica_agent.research_mcp.tools import ResearchTools
        from polemica_agent.memory_mcp.evidence import DurableResearchSnapshotJournal
        journal = DurableResearchSnapshotJournal(Path(_required("POLEMICA_AGENT_DATABASE")))
        client = HttpPolemicaClient(HttpClientConfig(
            api_base_url=required_https_origin("POLEMICA_API_BASE_URL"),
            profile_base_url=required_https_origin("POLEMICA_PROFILE_BASE_URL"),
            username=_required("POLEMICA_USERNAME"), password=_required("POLEMICA_PASSWORD"),
        ))
        service = ResearchService(
            client, RawPayloadCache(Path(_required("POLEMICA_RESEARCH_CACHE"))),
            SnapshotCoordinator(journal),
        )
        return ResearchTools(service), journal, None
    if kind == "fantasy":
        from polemica_agent.common.storage import AuditStore
        from polemica_agent.fantasy_mcp.client import FantasyHttpClient
        from polemica_agent.fantasy_mcp.registry import build_tool_registry
        from polemica_agent.fantasy_mcp.service import FantasyService
        from polemica_agent.memory_mcp.authorization import PersistentActAuthorizer
        from polemica_agent.mcp_runtime.fantasy_adapter import FantasyRegistryAdapter
        store = AuditStore(Path(_required("POLEMICA_AGENT_DATABASE")))
        client = FantasyHttpClient(
            required_https_origin("FANTASY_API_BASE_URL"), _required("FANTASY_BEARER_TOKEN")
        )
        registry = build_tool_registry(FantasyService(client, store))
        authorizer = PersistentActAuthorizer(
            store,
            series_reader=lambda series_id: registry.call(
                "fantasy_get_series", {"series_id": series_id}
            ),
        )
        return FantasyRegistryAdapter(registry), store, authorizer
    raise MCPConfigError("unknown server kind")


def _fantasy_write_allowlist() -> frozenset[str]:
    raw = os.environ.get("FANTASY_WRITE_ALLOWLIST", "")
    names = frozenset(name.strip() for name in raw.split(",") if name.strip())
    unknown = names.difference(FANTASY_WRITE_TOOLS)
    if unknown:
        raise MCPConfigError(f"unknown Fantasy write tool in allowlist: {sorted(unknown)}")
    return names


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run a fixed Polemica MCP server")
    parser.add_argument("kind", choices=("fantasy", "research", "memory"))
    args = parser.parse_args(argv)
    binding = ServerBinding.from_env()
    handler, closeable, authorizer = _handler(args.kind)
    write_enabled = os.environ.get("WRITE_ENABLED", "false").lower() == "true"
    server = build_server(
        args.kind, handler,
        policy=ToolPolicy(
            write_enabled=write_enabled,
            authorizer=authorizer,
            allowed_write_tools=_fantasy_write_allowlist() if args.kind == "fantasy" else frozenset(),
        ),
    )
    try:
        server.run(
            transport="streamable-http", host=binding.host, port=binding.port,
            streamable_http_path="/mcp", json_response=True, stateless_http=True,
        )
    finally:
        if closeable is not None:
            closeable.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
