from __future__ import annotations

import asyncio
from pathlib import Path

from polemica_agent.mcp_runtime.memory_tools import MemoryTools
from polemica_agent.mcp_runtime.registry import build_server
from polemica_agent.memory_mcp.service import MemoryService


def test_real_memory_mcp_concurrent_writes_are_serialized(tmp_path: Path) -> None:
    service = MemoryService((tmp_path / "state" / "agent.sqlite3").resolve())
    server = build_server("memory", MemoryTools(service))

    async def write_all() -> list[object]:
        return await asyncio.gather(*(
            server.call_tool(
                "record_intervention",
                {"reason": f"concurrent-{index}", "details": {"index": index}},
            )
            for index in range(16)
        ))

    try:
        results = asyncio.run(write_all())
        assert all(not result.is_error for result in results)
        with service.store.transaction() as db:
            count = db.execute("SELECT COUNT(*) FROM interventions").fetchone()[0]
        assert count == 16
    finally:
        service.close()
