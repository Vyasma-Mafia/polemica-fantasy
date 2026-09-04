from __future__ import annotations

import dataclasses
import datetime as dt
import threading
import uuid
from pathlib import Path

import pytest

from polemica_agent.common.storage import AuditStore, FailClosedError
from polemica_agent.compute_mcp.audit import ComputationConflictError, ComputeAudit
from polemica_agent.memory_mcp.authorization import PersistentActAuthorizer
from polemica_agent.memory_mcp.evidence import DurableResearchSnapshotJournal
from polemica_agent.research_mcp.cache import RawPayloadCache
from polemica_agent.research_mcp.snapshots import SnapshotCoordinator
from polemica_agent.research_mcp.types import PartialError


UTC = dt.timezone.utc
NOW = dt.datetime(2026, 9, 5, 12, 0, tzinfo=UTC)


def _run(store: AuditStore) -> str:
    run_id = str(uuid.uuid4())
    store.start_run(run_id=run_id, model="test", prompt_hash="p", tools_hash="t", config_hash="c")
    return run_id


def _sealed(
    store: AuditStore,
    tmp_path: Path,
    run_id: str,
    *,
    object_id: str = "7",
    complete: bool = True,
) -> tuple[int, dict]:
    journal = DurableResearchSnapshotJournal(store.database_path, clock=lambda: NOW)
    coordinator = SnapshotCoordinator(journal, clock=lambda: NOW)
    collecting = coordinator.begin(run_id)
    record = RawPayloadCache(tmp_path / f"cache-{object_id}", clock=lambda: NOW).store(
        source="match", object_id=object_id, source_version=1,
        payload={"id": int(object_id), "points": 3.5},
    )
    coordinator.attach(str(collecting.snapshot_id), record)
    if not complete:
        journal.observe_result(
            str(collecting.snapshot_id), complete=False, sample_size=1,
            errors=[PartialError("fixture", "PARTIAL", "fixture")],
        )
    sealed = coordinator.seal(str(collecting.snapshot_id))
    journal.close()
    return int(sealed.snapshot_id), dataclasses.asdict(record)


def _plan(audit: ComputeAudit, run_id: str, snapshot_id: int, record: dict, **overrides):
    values = {
        "computation_id": str(uuid.uuid4()),
        "run_id": run_id,
        "source_snapshot_id": snapshot_id,
        "tool_name": "compute_player_summary",
        "engine_version": "engine-1",
        "dataset_schema_version": "polemica-game-1",
        "request": {"operation": "mean", "field": "points"},
        "input_records": [{key: record[key] for key in (
            "source", "object_id", "source_version", "payload_hash"
        )}],
        "input_payload": [{"id": 7, "points": 3.5}],
    }
    values.update(overrides)
    return audit.plan(**values), values


def test_validate_snapshot_rejects_cross_run_partial_and_untrusted(tmp_path: Path) -> None:
    store = AuditStore((tmp_path / "state" / "agent.sqlite3").resolve())
    try:
        run_id = _run(store)
        other_run = _run(store)
        snapshot_id, _ = _sealed(store, tmp_path, run_id)
        partial_id, _ = _sealed(store, tmp_path, run_id, object_id="8", complete=False)
        generic = store.create_snapshot(
            run_id=run_id, kind="NOTE", as_of=NOW, generated_at=NOW,
            source="model", payload={"not": "evidence"},
        )
        audit = ComputeAudit(store)
        assert audit.validate_snapshot(run_id, snapshot_id).snapshot_id == snapshot_id
        with pytest.raises(FailClosedError, match="RUNNING run snapshot"):
            audit.validate_snapshot(other_run, snapshot_id)
        with pytest.raises(FailClosedError, match="COMPLETE"):
            audit.validate_snapshot(run_id, partial_id)
        with pytest.raises(FailClosedError, match="RUNNING run snapshot"):
            audit.validate_snapshot(run_id, generic)
    finally:
        store.close()


def test_plan_is_immutable_and_replays_only_exact_identity(tmp_path: Path) -> None:
    store = AuditStore((tmp_path / "state" / "agent.sqlite3").resolve())
    try:
        run_id = _run(store)
        snapshot_id, record = _sealed(store, tmp_path, run_id)
        audit = ComputeAudit(store)
        first, arguments = _plan(audit, run_id, snapshot_id, record)
        replay = audit.plan(**arguments)
        assert replay["id"] == first["id"]
        assert replay["state"] == "PLANNED"
        with pytest.raises(ComputationConflictError, match="different identity"):
            audit.plan(**{**arguments, "engine_version": "engine-2"})
        with pytest.raises(FailClosedError, match="not part"):
            audit.plan(**{
                **arguments,
                "computation_id": str(uuid.uuid4()),
                "input_records": [{**arguments["input_records"][0], "payload_hash": "0" * 64}],
            })
    finally:
        store.close()


def test_claim_is_single_winner_and_restart_recovery_is_reclaimable(tmp_path: Path) -> None:
    store = AuditStore((tmp_path / "state" / "agent.sqlite3").resolve())
    try:
        run_id = _run(store)
        snapshot_id, record = _sealed(store, tmp_path, run_id)
        audit = ComputeAudit(store)
        _, arguments = _plan(audit, run_id, snapshot_id, record)
        claims = []

        def claim() -> None:
            claims.append(audit.claim(arguments["computation_id"]))

        threads = [threading.Thread(target=claim) for _ in range(8)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        assert sum(item.acquired for item in claims) == 1
        assert audit._recover_interrupted_after_singleton_lease() == 1
        recovered = audit.get_result(arguments["computation_id"])
        assert recovered["state"] == "INTERRUPTED"
        assert audit.claim(arguments["computation_id"]).acquired is True
        assert audit.claim(arguments["computation_id"]).acquired is False
    finally:
        store.close()


def test_terminal_resolution_and_result_replay_are_hash_bound(tmp_path: Path) -> None:
    store = AuditStore((tmp_path / "state" / "agent.sqlite3").resolve())
    try:
        run_id = _run(store)
        snapshot_id, record = _sealed(store, tmp_path, run_id)
        audit = ComputeAudit(store)
        _, arguments = _plan(audit, run_id, snapshot_id, record)
        assert audit.claim(arguments["computation_id"]).acquired
        resolved = audit.resolve_success(
            arguments["computation_id"], result={"mean": 3.5},
            verification={"bounded": True}, result_count=1, output_bytes=12, duration_ms=4,
        )
        assert resolved["state"] == "SUCCEEDED"
        replay = audit.resolve_success(
            arguments["computation_id"], result={"mean": 3.5},
            verification={"bounded": True}, result_count=1, output_bytes=12, duration_ms=4,
        )
        assert replay["result_hash"] == resolved["result_hash"]
        value = audit.get_result(arguments["computation_id"])
        assert value["result"] == {"mean": 3.5}
        assert value["verification"] == {"bounded": True}
        with pytest.raises(ComputationConflictError, match="different terminal result"):
            audit.resolve_success(
                arguments["computation_id"], result={"mean": 4.0},
                verification={"bounded": True}, result_count=1, output_bytes=12, duration_ms=4,
            )
    finally:
        store.close()


def test_decision_links_only_successful_same_snapshot_computation(tmp_path: Path) -> None:
    store = AuditStore((tmp_path / "state" / "agent.sqlite3").resolve())
    try:
        run_id = _run(store)
        source_id, record = _sealed(store, tmp_path, run_id)
        other_id, _ = _sealed(store, tmp_path, run_id, object_id="9")
        audit = ComputeAudit(store)
        _, arguments = _plan(audit, run_id, source_id, record)
        computation_id = arguments["computation_id"]
        with pytest.raises(FailClosedError, match="successful"):
            store.record_decision(
                run_id=run_id, decision_type="TEAM", subject_type="series", subject_id="1",
                snapshot_ids=[source_id], computation_ids=[computation_id], alternatives=[],
                choice={}, rationale="not finished",
            )
        audit.claim(computation_id)
        audit.resolve_success(
            computation_id, result={"score": 7}, verification={"bounded": True},
            result_count=1, output_bytes=10, duration_ms=2,
        )
        with pytest.raises(FailClosedError, match="successful"):
            store.record_decision(
                run_id=run_id, decision_type="TEAM", subject_type="series", subject_id="1",
                snapshot_ids=[other_id], computation_ids=[computation_id], alternatives=[],
                choice={}, rationale="wrong source",
            )
        decision_id = store.record_decision(
            run_id=run_id, decision_type="TEAM", subject_type="series", subject_id="1",
            snapshot_ids=[source_id], computation_ids=[computation_id], alternatives=[],
            choice={}, rationale="linked",
        )
        linked = store.connection.execute(
            "SELECT computation_id FROM decision_computations WHERE decision_id=?", (decision_id,),
        ).fetchone()
        assert linked["computation_id"] == computation_id
        with store.transaction() as db:
            store._validate_decision_lineage(db, decision_id=decision_id, run_id=run_id)
    finally:
        store.close()


def test_act_authorizer_rechecks_linked_computation_lineage(tmp_path: Path) -> None:
    store = AuditStore((tmp_path / "state" / "agent.sqlite3").resolve())
    try:
        run_id = _run(store)
        source_id, record = _sealed(store, tmp_path, run_id)
        audit = ComputeAudit(store)
        _, arguments = _plan(audit, run_id, source_id, record)
        computation_id = arguments["computation_id"]
        audit.claim(computation_id)
        audit.resolve_success(
            computation_id, result={"score": 7}, verification={"bounded": True},
            result_count=1, output_bytes=10, duration_ms=2,
        )
        decision_id = store.record_decision(
            run_id=run_id, decision_type="PACK", subject_type="pack", subject_id="3",
            snapshot_ids=[source_id], computation_ids=[computation_id], alternatives=[],
            choice={"tool": "fantasy_buy_pack", "arguments": {"pack_id": 3}},
            rationale="linked",
        )
        decision_time = NOW + dt.timedelta(minutes=1)
        # Simulate corruption or an out-of-band mutation after the decision was recorded.
        with store.transaction() as db:
            db.execute(
                "UPDATE decisions SET decided_at=? WHERE id=?",
                (decision_time.isoformat(), decision_id),
            )
            db.execute("UPDATE computations SET state='FAILED' WHERE id=?", (computation_id,))
        authorizer = PersistentActAuthorizer(
            store, series_reader=lambda _series_id: {},
            clock=lambda: decision_time + dt.timedelta(seconds=1),
        )
        with pytest.raises(FailClosedError, match="computation lineage"):
            authorizer.authorize_write("fantasy_buy_pack", {
                "run_id": run_id,
                "operation_id": str(uuid.uuid4()),
                "decision_id": decision_id,
                "pack_id": 3,
            })
    finally:
        store.close()


def test_successful_run_rejects_unlinked_successful_computation(tmp_path: Path) -> None:
    store = AuditStore((tmp_path / "state" / "agent.sqlite3").resolve())
    try:
        strategy = "compute-audit-v1"
        store.record_strategy(strategy, "prompt-hash", {})
        run_id = str(uuid.uuid4())
        store.start_run(
            run_id=run_id, model="test", prompt_hash="p", tools_hash="t",
            config_hash="c", strategy_version=strategy,
        )
        snapshot_id, record = _sealed(store, tmp_path, run_id)
        audit = ComputeAudit(store)
        _, arguments = _plan(audit, run_id, snapshot_id, record)
        computation_id = arguments["computation_id"]
        audit.claim(computation_id)
        audit.resolve_success(
            computation_id, result={"score": 7}, verification={"bounded": True},
            result_count=1, output_bytes=10, duration_ms=2,
        )
        store.record_decision(
            run_id=run_id, decision_type="NOOP", subject_type="run", subject_id=run_id,
            snapshot_ids=[snapshot_id], alternatives=[], choice={}, rationale="not linked",
            strategy_version=strategy,
        )
        with pytest.raises(FailClosedError, match="unlinked computation"):
            store.finish_run(run_id, "SUCCEEDED", require_decision=True)
        store.record_decision(
            run_id=run_id, decision_type="NOOP", subject_type="run", subject_id=run_id,
            snapshot_ids=[snapshot_id], computation_ids=[computation_id], alternatives=[],
            choice={}, rationale="linked", strategy_version=strategy,
        )
        store.finish_run(run_id, "SUCCEEDED", require_decision=True)
    finally:
        store.close()
