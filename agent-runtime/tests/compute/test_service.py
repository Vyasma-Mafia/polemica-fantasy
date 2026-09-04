from __future__ import annotations

import asyncio
import dataclasses
import datetime as dt
import uuid
from pathlib import Path
from typing import Any, Mapping

import pytest
from mcp.server.mcpserver.exceptions import ToolError

from polemica_agent.common.storage import AuditStore
from polemica_agent.compute_mcp.engine import execute
from polemica_agent.compute_mcp.service import ComputeService
from polemica_agent.compute_mcp.tools import ComputeTools
from polemica_agent.memory_mcp.evidence import DurableResearchSnapshotJournal
from polemica_agent.mcp_runtime.registry import MCP_SERVERS, build_server
from polemica_agent.research_mcp.cache import RawPayloadCache
from polemica_agent.research_mcp.snapshots import SnapshotCoordinator


NOW = dt.datetime(2026, 9, 5, 12, 0, tzinfo=dt.timezone.utc)


class InProcessWorker:
    def __init__(self) -> None:
        self.calls = 0

    def run(self, operation: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        self.calls += 1
        assert all(set(row) == {"playerId", "points", "mmr", "win", "roleCode"}
                   for row in payload["rows"])
        return execute(operation, payload)


def fixture_service(tmp_path: Path) -> tuple[ComputeService, ComputeTools, str, int, InProcessWorker]:
    database = (tmp_path / "state" / "agent.sqlite3").resolve()
    store = AuditStore(database)
    run_id = str(uuid.uuid4())
    store.start_run(run_id=run_id, model="test", prompt_hash="p", tools_hash="t", config_hash="c")
    cache = RawPayloadCache(tmp_path / "research-cache", clock=lambda: NOW)
    journal = DurableResearchSnapshotJournal(database, clock=lambda: NOW)
    coordinator = SnapshotCoordinator(journal, clock=lambda: NOW)
    collecting = coordinator.begin(run_id)
    rows = {
        7: [(101, 1.5, 1200.0, "win", "civilian"), (100, -0.5, 1190.0, "fail", "mafia")],
        8: [(201, 2.0, 1250.0, "win", "sheriff"), (200, 0.5, 1220.0, "win", "civilian")],
    }
    for player_id, games in rows.items():
        record = cache.store(
            source="profile-games-page", object_id=f"{player_id}:1:100",
            payload={
                "rows": [
                    {
                        "id": game_id, "points": points, "mmr": {"value": mmr},
                        "result": {"code": result}, "role": {"type": role},
                    }
                    for game_id, points, mmr, result, role in games
                ],
                "totalCount": len(games),
            },
        )
        coordinator.attach(str(collecting.snapshot_id), record)
    sealed = coordinator.seal(str(collecting.snapshot_id))
    journal.close()
    worker = InProcessWorker()
    service = ComputeService(store, cache.root, worker)  # type: ignore[arg-type]
    return service, ComputeTools(service), run_id, int(sealed.snapshot_id), worker


def test_compute_service_executes_persisted_snapshot_rows_and_replays(tmp_path: Path) -> None:
    service, tools, run_id, snapshot_id, worker = fixture_service(tmp_path)
    computation_id = str(uuid.uuid4())
    try:
        first = tools.compute_describe_player_points(
            run_id, snapshot_id, computation_id, [7, 8], [0.5],
        )
        replay = tools.compute_describe_player_points(
            run_id, snapshot_id, computation_id, [7, 8], [0.5],
        )
        assert first == replay
        assert first["state"] == "SUCCEEDED"
        assert first["result"]["players"][0]["mean"] == 0.5
        assert first["verification"]["sourceSnapshotId"] == snapshot_id
        assert worker.calls == 1
        assert "path" not in str(first).lower()
        assert "rows" not in first
    finally:
        service.close()


def test_compute_result_can_be_durably_linked_to_decision(tmp_path: Path) -> None:
    service, tools, run_id, snapshot_id, _ = fixture_service(tmp_path)
    computation_id = str(uuid.uuid4())
    try:
        result = tools.compute_simulate_player_totals(
            run_id, snapshot_id, computation_id, [7, 8], 3, 100, 42,
        )
        decision_id = service.store.record_decision(
            run_id=run_id, decision_type="TEAM", subject_type="series", subject_id="1",
            snapshot_ids=[snapshot_id], computation_ids=[computation_id],
            alternatives=[], choice={}, rationale="fixture",
        )
        linked = service.store.connection.execute(
            "SELECT computation_id FROM decision_computations WHERE decision_id=?", (decision_id,),
        ).fetchone()
        assert result["state"] == "SUCCEEDED"
        assert linked["computation_id"] == computation_id
    finally:
        service.close()


def test_real_compute_mcp_contract_and_sanitized_conflict(tmp_path: Path) -> None:
    service, tools, run_id, snapshot_id, _ = fixture_service(tmp_path)
    server = build_server("compute", tools)
    computation_id = str(uuid.uuid4())
    arguments = {
        "run_id": run_id, "snapshot_id": snapshot_id, "computation_id": computation_id,
        "player_ids": [7, 8], "features": ["points", "win"],
    }
    try:
        listed = asyncio.run(server.list_tools())
        assert {tool.name for tool in listed} == set(MCP_SERVERS["compute"])
        response = asyncio.run(server.call_tool("compute_correlate_player_metrics", arguments))
        assert response.is_error is False
        with pytest.raises(ToolError, match="COMPUTE_ID_CONFLICT"):
            asyncio.run(server.call_tool(
                "compute_correlate_player_metrics",
                {**arguments, "features": ["points", "mmr"]},
            ))
    finally:
        service.close()


def test_compute_rejects_cross_run_and_missing_player_without_leaking_manifest(tmp_path: Path) -> None:
    service, tools, run_id, snapshot_id, _ = fixture_service(tmp_path)
    try:
        with pytest.raises(ToolError, match="COMPUTE_PLAYER_DATA_MISSING") as missing:
            tools.compute_describe_player_points(
                run_id, snapshot_id, str(uuid.uuid4()), [999], [0.5],
            )
        assert "research-cache" not in str(missing.value)
        with pytest.raises(ToolError, match="EVIDENCE_OR_ARGUMENT_INVALID"):
            tools.compute_describe_player_points(
                str(uuid.uuid4()), snapshot_id, str(uuid.uuid4()), [7], [0.5],
            )
    finally:
        service.close()


def test_service_construction_does_not_recover_jobs_without_singleton_lease(tmp_path: Path) -> None:
    service, _, run_id, snapshot_id, worker = fixture_service(tmp_path)
    computation_id = str(uuid.uuid4())
    try:
        snapshot = service.audit.validate_snapshot(run_id, snapshot_id)
        rows_record = next(record for record in snapshot.records if record["source"] == "profile-games-page")
        service.audit.plan(
            computation_id=computation_id,
            run_id=run_id,
            source_snapshot_id=snapshot_id,
            tool_name="compute_describe_player_points",
            engine_version="compute-v1",
            dataset_schema_version="player-game-v1",
            request={"fixture": True},
            input_records=[rows_record],
            input_payload={"rows": [], "playerIds": [7], "quantiles": []},
        )
        assert service.audit.claim(computation_id).acquired

        second_store = AuditStore(service.store.database_path)
        second = ComputeService(second_store, service.research_cache, worker)  # type: ignore[arg-type]
        try:
            assert second.audit.get_result(computation_id)["state"] == "RUNNING"
            assert second.recover_interrupted_after_singleton_lease() == 1
            assert second.audit.get_result(computation_id)["state"] == "INTERRUPTED"
        finally:
            second.close()
    finally:
        service.close()
