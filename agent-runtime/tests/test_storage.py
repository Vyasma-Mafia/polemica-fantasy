from __future__ import annotations

import datetime as dt
import os
import sqlite3
from pathlib import Path

import pytest

from polemica_agent.common.storage import AuditStore, FailClosedError, IntentConflictError


def make_store(tmp_path: Path) -> AuditStore:
    return AuditStore((tmp_path / "state" / "agent.sqlite3").resolve())


def start(store: AuditStore, run_id: str = "run-1") -> str:
    return store.start_run(
        run_id=run_id,
        model="test",
        prompt_hash="p" * 64,
        tools_hash="t" * 64,
        config_hash="c" * 64,
    )


def decision(store: AuditStore, run_id: str = "run-1") -> int:
    now = dt.datetime.now(dt.timezone.utc)
    snapshot_id = store.create_snapshot(
        run_id=run_id, kind="TEST", as_of=now, generated_at=now, source="test", payload={},
    )
    return store.record_decision(
        run_id=run_id, decision_type="TEST", subject_type="TEST", subject_id="1",
        snapshot_ids=[snapshot_id], alternatives=[], choice={}, rationale="test",
    )


def test_database_path_must_be_absolute(tmp_path: Path) -> None:
    with pytest.raises(ValueError):
        AuditStore(Path("relative.sqlite3"))


def test_initializes_wal_schema_and_private_permissions(tmp_path: Path) -> None:
    with make_store(tmp_path) as store:
        assert store.connection.execute("PRAGMA journal_mode").fetchone()[0] == "wal"
        tables = {
            row[0]
            for row in store.connection.execute("SELECT name FROM sqlite_master WHERE type='table'")
        }
        assert {
            "runs",
            "snapshots",
            "raw_payload_refs",
            "raw_polemica_games",
            "derived_features",
            "strategy_versions",
            "decisions",
            "operation_intents",
            "tool_calls",
            "outcomes",
            "interventions",
        } <= tables
        assert os.stat(store.database_path).st_mode & 0o777 == 0o600
        assert os.stat(store.state_dir).st_mode & 0o777 == 0o700


def test_restart_preserves_run_intent_and_blob(tmp_path: Path) -> None:
    path = (tmp_path / "state" / "agent.sqlite3").resolve()
    with AuditStore(path) as store:
        start(store)
        decision_id = decision(store)
        row = store.plan_intent(
            operation_id="op-1",
            run_id="run-1",
            kind="TEAM_WRITE",
            target_id="1:MAIN",
            request={"cards": [1, 2]},
            is_economic=False,
            decision_id=decision_id,
        )
        request_hash, request_path = row["request_hash"], row["request_path"]
    with AuditStore(path) as restarted:
        row = restarted.get_intent("op-1")
        assert row is not None and row["state"] == "PLANNED"
        assert restarted.load_blob(request_hash, request_path) == {"cards": [1, 2]}


def test_changed_applied_migration_fails_closed(tmp_path: Path) -> None:
    migration_dir = tmp_path / "migrations"
    migration_dir.mkdir()
    migration = migration_dir / "001_test.sql"
    migration.write_text("CREATE TABLE sample(id INTEGER PRIMARY KEY);", encoding="utf-8")
    path = (tmp_path / "state" / "agent.sqlite3").resolve()
    with AuditStore(path, migration_dir):
        pass
    migration.write_text("CREATE TABLE changed(id INTEGER PRIMARY KEY);", encoding="utf-8")
    with pytest.raises(FailClosedError, match="checksum changed"):
        AuditStore(path, migration_dir)


def test_duplicate_operation_same_payload_is_stable_but_mismatch_rejected(tmp_path: Path) -> None:
    with make_store(tmp_path) as store:
        start(store)
        decision_id = decision(store)
        kwargs = dict(
            operation_id="op-1",
            run_id="run-1",
            kind="BUY_PACK",
            target_id="3",
            request={"packId": 3},
            is_economic=True,
            decision_id=decision_id,
        )
        first = store.plan_intent(**kwargs)
        second = store.plan_intent(**kwargs)
        assert first["request_hash"] == second["request_hash"]
        with pytest.raises(IntentConflictError):
            store.plan_intent(**{**kwargs, "request": {"packId": 4}})


def test_duplicate_operation_binds_complete_identity_and_decision_lineage(tmp_path: Path) -> None:
    with make_store(tmp_path) as store:
        start(store, "run-1")
        start(store, "run-2")
        first_decision = decision(store, "run-1")
        second_decision = decision(store, "run-1")
        foreign_decision = decision(store, "run-2")
        identity = {
            "operation_id": "bound-op",
            "run_id": "run-1",
            "decision_id": first_decision,
            "kind": "BUY_PACK",
            "target_id": "3",
            "request": {"packId": 3},
            "is_economic": True,
        }
        store.plan_intent(**identity)
        mismatches = (
            {"run_id": "run-2"},
            {"decision_id": second_decision},
            {"kind": "TEAM_WRITE"},
            {"target_id": "4"},
            {"is_economic": False},
            {"request": {"packId": 4}},
        )
        for mismatch in mismatches:
            with pytest.raises(IntentConflictError, match="different identity"):
                store.plan_intent(**{**identity, **mismatch})
        with pytest.raises(FailClosedError, match="same run"):
            store.plan_intent(**{
                **identity,
                "operation_id": "foreign-decision",
                "decision_id": foreign_decision,
            })
        with pytest.raises(FailClosedError, match="same run"):
            store.plan_intent(**{
                **identity,
                "operation_id": "missing-decision",
                "decision_id": 999999,
            })


def test_unresolved_economic_intent_blocks_next_economic_write(tmp_path: Path) -> None:
    with make_store(tmp_path) as store:
        start(store)
        decision_id = decision(store)
        for operation_id in ("op-1", "op-2"):
            store.plan_intent(
                operation_id=operation_id,
                run_id="run-1",
                kind="BUY_PACK",
                target_id=operation_id,
                request={"id": operation_id},
                is_economic=True,
                decision_id=decision_id,
            )
        store.mark_intent_sent("op-1")
        store.resolve_intent("op-1", "UNKNOWN", {"timeout": True})
        with pytest.raises(FailClosedError, match="blocked"):
            store.mark_intent_sent("op-2")


def test_non_economic_intent_is_not_blocked_by_unknown_economic_write(tmp_path: Path) -> None:
    with make_store(tmp_path) as store:
        start(store)
        decision_id = decision(store)
        store.plan_intent(
            operation_id="economic",
            run_id="run-1",
            kind="BUY_PACK",
            target_id="1",
            request={},
            is_economic=True,
            decision_id=decision_id,
        )
        store.mark_intent_sent("economic")
        store.resolve_intent("economic", "UNKNOWN", {"timeout": True})
        store.plan_intent(
            operation_id="team",
            run_id="run-1",
            kind="TEAM_WRITE",
            target_id="2:MAIN",
            request={},
            is_economic=False,
            decision_id=decision_id,
        )
        assert store.mark_intent_sent("team")["state"] == "SENT"


def test_decision_requires_same_run_sealed_snapshots(tmp_path: Path) -> None:
    now = dt.datetime.now(dt.timezone.utc)
    with make_store(tmp_path) as store:
        start(store, "run-1")
        start(store, "run-2")
        snapshot = store.create_snapshot(
            run_id="run-1",
            kind="STATE",
            as_of=now,
            generated_at=now,
            source="fake",
            payload={"value": 1},
        )
        with pytest.raises(FailClosedError, match="same run"):
            store.record_decision(
                run_id="run-2",
                decision_type="TEAM",
                subject_type="SERIES",
                subject_id="1",
                snapshot_ids=[snapshot],
                alternatives=[],
                choice={},
                rationale="test",
            )


def test_recent_decisions_returns_verified_payload_and_latest_outcome(tmp_path: Path) -> None:
    now = dt.datetime.now(dt.timezone.utc)
    with make_store(tmp_path) as store:
        start(store)
        snapshot = store.create_snapshot(
            run_id="run-1",
            kind="STATE",
            as_of=now,
            generated_at=now,
            source="fake",
            payload={"balance": 1000},
        )
        decision = store.record_decision(
            run_id="run-1",
            decision_type="TEAM",
            subject_type="SERIES",
            subject_id="7",
            snapshot_ids=[snapshot],
            alternatives=[{"cards": [1]}],
            choice={"cards": [1]},
            rationale="highest projection",
        )
        store.record_outcome(decision, {"rank": 2}, 12.5)
        memory = store.recent_decisions(subject_type="SERIES", subject_id="7")
        assert len(memory) == 1
        assert memory[0]["choice"] == {"cards": [1]}
        assert memory[0]["outcome"]["payload"] == {"rank": 2}
        assert memory[0]["outcome"]["score"] == 12.5


def test_snapshot_rejects_future_as_of_and_naive_times(tmp_path: Path) -> None:
    now = dt.datetime.now(dt.timezone.utc)
    with make_store(tmp_path) as store:
        start(store)
        with pytest.raises(ValueError, match="after"):
            store.create_snapshot(
                run_id="run-1",
                kind="STATE",
                as_of=now + dt.timedelta(seconds=1),
                generated_at=now,
                source="fake",
                payload={},
            )
        with pytest.raises(ValueError, match="timezone-aware"):
            store.create_snapshot(
                run_id="run-1",
                kind="STATE",
                as_of=dt.datetime.now(),
                generated_at=dt.datetime.now(),
                source="fake",
                payload={},
            )


def test_raw_payload_deduplicates_and_derived_features_are_versioned(tmp_path: Path) -> None:
    now = dt.datetime.now(dt.timezone.utc)
    with make_store(tmp_path) as store:
        first = store.store_raw_payload(
            source="polemica",
            source_key="game:1",
            source_version="2",
            as_of=now,
            payload={"id": 1},
        )
        second = store.store_raw_payload(
            source="polemica",
            source_key="game:1",
            source_version="2",
            as_of=now,
            payload={"id": 1},
        )
        assert first == second
        feature_id = store.store_derived_features(
            subject_type="PLAYER",
            subject_id="10",
            feature_version="v1",
            as_of=now,
            generated_at=now,
            payload={"mean": 1.25},
        )
        assert feature_id > 0


def test_tampered_or_missing_blob_fails_closed(tmp_path: Path) -> None:
    with make_store(tmp_path) as store:
        digest, relative = store.store_blob({"safe": True})
        path = store.blob_dir / relative
        path.write_text("{}", encoding="utf-8")
        with pytest.raises(FailClosedError, match="hash mismatch"):
            store.load_blob(digest, relative)
        path.unlink()
        with pytest.raises(FailClosedError, match="Cannot read"):
            store.load_blob(digest, relative)


def test_transaction_rolls_back_on_error(tmp_path: Path) -> None:
    with make_store(tmp_path) as store:
        with pytest.raises(RuntimeError):
            with store.transaction() as db:
                db.execute(
                    "INSERT INTO runs(id,started_at,status,model,prompt_hash,tools_hash,config_hash) "
                    "VALUES ('x','now','RUNNING','m','p','t','c')",
                )
                raise RuntimeError("crash")
        assert store.connection.execute("SELECT COUNT(*) FROM runs WHERE id='x'").fetchone()[0] == 0


def test_readonly_directory_fails_closed(tmp_path: Path) -> None:
    if os.geteuid() == 0:
        pytest.skip("root bypasses directory write permissions")
    state = tmp_path / "readonly"
    state.mkdir()
    state.chmod(0o500)
    try:
        with pytest.raises(FailClosedError):
            AuditStore((state / "agent.sqlite3").resolve())
    finally:
        state.chmod(0o700)
