from __future__ import annotations

from typing import Any, Mapping, Protocol

import anyio
from mcp import Client


class MemoryGatewayError(RuntimeError):
    pass


class MemoryGateway(Protocol):
    def get_open_intents(self) -> list[dict[str, Any]]: ...
    def start_run(self, **metadata: str) -> str: ...
    def finish_run(self, run_id: str, status: str, summary: Mapping[str, Any]) -> None: ...
    def close(self) -> None: ...


class MCPMemoryGateway:
    def __init__(self, url: str) -> None:
        self.url = url

    def _call(self, name: str, arguments: dict[str, Any]) -> Any:
        async def invoke() -> Any:
            with anyio.fail_after(15):
                async with Client(self.url, read_timeout_seconds=12) as client:
                    result = await client.call_tool(name, arguments)
            if result.is_error:
                raise MemoryGatewayError(f"Memory MCP rejected {name}")
            structured = result.structured_content
            if isinstance(structured, dict) and set(structured) == {"result"}:
                return structured["result"]
            return structured
        try:
            return anyio.run(invoke)
        except MemoryGatewayError:
            raise
        except Exception as exc:
            raise MemoryGatewayError(f"Memory MCP call failed: {name}") from exc

    def get_open_intents(self) -> list[dict[str, Any]]:
        value = self._call("get_open_intents", {"economic_only": False})
        if not isinstance(value, list) or any(not isinstance(item, dict) for item in value):
            raise MemoryGatewayError("Memory MCP returned invalid open intents")
        return value

    def start_run(self, **metadata: str) -> str:
        value = self._call("start_run", metadata)
        if not isinstance(value, str) or not value:
            raise MemoryGatewayError("Memory MCP returned invalid run id")
        return value

    def finish_run(self, run_id: str, status: str, summary: Mapping[str, Any]) -> None:
        self._call("finish_run", {"run_id": run_id, "status": status, "summary": dict(summary)})

    def close(self) -> None:
        return None
