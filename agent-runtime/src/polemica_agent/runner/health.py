from __future__ import annotations

import anyio
from mcp import Client

from polemica_agent.mcp_runtime.registry import MCP_SERVERS


class MCPHealthError(RuntimeError):
    pass


async def _probe_one(kind: str, url: str) -> None:
    try:
        with anyio.fail_after(10):
            async with Client(url, read_timeout_seconds=8) as client:
                result = await client.list_tools(cache_mode="refresh")
    except Exception as exc:
        raise MCPHealthError(f"required {kind} MCP is unavailable") from exc
    actual = {tool.name for tool in result.tools}
    expected = set(MCP_SERVERS[kind])
    if actual != expected:
        raise MCPHealthError(
            f"{kind} MCP registry mismatch: missing={sorted(expected-actual)}, extra={sorted(actual-expected)}"
        )


def probe_required_servers(urls: dict[str, str]) -> None:
    if set(urls) != set(MCP_SERVERS):
        raise MCPHealthError("all required MCP URLs must be configured")
    async def run() -> None:
        async with anyio.create_task_group() as group:
            for kind, url in urls.items():
                group.start_soon(_probe_one, kind, url)
    anyio.run(run)
