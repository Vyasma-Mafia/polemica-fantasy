from __future__ import annotations

import datetime as dt
import threading
import uuid
from pathlib import Path

import anyio
import pytest

from polemica_agent.common.storage import AuditStore, FailClosedError
from polemica_agent.mcp_runtime.memory_tools import MemoryTools
from polemica_agent.mcp_runtime.registry import MCP_SERVERS, build_server
from polemica_agent.memory_mcp.authorization import PersistentActAuthorizer
from polemica_agent.memory_mcp.evidence import DurableResearchSnapshotJournal
from polemica_agent.memory_mcp.service import MemoryService
from polemica_agent.research_mcp.cache import RawPayloadCache
from polemica_agent.research_mcp.errors import SnapshotSealedError
from polemica_agent.research_mcp.snapshots import SnapshotCoordinator


UTC = dt.timezone.utc
NOW = dt.datetime(2026, 9, 4, 12, 0, tzinfo=UTC)


def start_run(database: Path) -> str:
    run_id = str(uuid.uuid4())
    with AuditStore(database) as store:
        store.start_run(
            run_id=run_id, model="test", prompt_hash="p", tools_hash="t", config_hash="c"
        )
    return run_id


def seal_evidence(database: Path, cache_dir: Path, run_id: str) -> int:
    journal = DurableResearchSnapshotJournal(database, clock=lambda: NOW)
    coordinator = SnapshotCoordinator(journal, clock=lambda: NOW)
    collecting = coordinator.begin(run_id)
    record = RawPayloadCache(cache_dir, clock=lambda: NOW).store(
        source="match", object_id="7", source_version=1, payload={"id": 7, "result": "CITY"}
    )
    coordinator.attach(str(collecting.snapshot_id), record)
    journal.close()

    # A fresh process can continue and atomically seal the same durable collection.
    reopened = DurableResearchSnapshotJournal(database, clock=lambda: NOW)
    sealed = SnapshotCoordinator(reopened, clock=lambda: NOW).seal(str(collecting.snapshot_id))
    reopened.close()
    assert isinstance(sealed.snapshot_id, int)
    return sealed.snapshot_id


def test_durable_collection_survives_restart_and_decision_accepts_numeric_seal(tmp_path: Path) -> None:
    database = tmp_path / "state" / "agent.sqlite3"
    run_id = start_run(database)
    snapshot_id = seal_evidence(database, tmp_path / "cache", run_id)
    service = MemoryService(database)
    tools = MemoryTools(service)
    decision_id = tools.record_decision(
        run_id=run_id, decision_type="TEAM", subject_type="series", subject_id="4",
        snapshot_ids=[snapshot_id], alternatives=[],
        choice={"tool": "fantasy_create_team", "arguments": {
            "series_id": 4, "league_code": "MAIN", "user_card_ids": [11]
        }}, rationale="fixture",
    )
    assert decision_id > 0
    trust = service.store.connection.execute(
        "SELECT trust_kind FROM snapshot_trust WHERE snapshot_id=?", (snapshot_id,)
    ).fetchone()
    assert trust["trust_kind"] == "TRUSTED_RESEARCH"
    service.close()


def test_generic_memory_snapshot_is_explicitly_non_evidence(tmp_path: Path) -> None:
    database = tmp_path / "state" / "agent.sqlite3"
    run_id = start_run(database)
    service = MemoryService(database)
    tools = MemoryTools(service)
    snapshot_id = tools.store_snapshot(
        run_id=run_id, kind="NOTE", as_of=NOW.isoformat(), generated_at=NOW.isoformat(),
        source="model", payload={"claim": "fabricated"},
    )
    marker = service.store.connection.execute(
        "SELECT trust_kind FROM snapshot_trust WHERE snapshot_id=?", (snapshot_id,)
    ).fetchone()
    assert marker["trust_kind"] == "NON_EVIDENCE"
    with pytest.raises(FailClosedError, match="untrusted"):
        tools.record_decision(
            run_id=run_id, decision_type="TEAM", subject_type="series", subject_id="4",
            snapshot_ids=[snapshot_id], alternatives=[], choice={}, rationale="bad",
        )
    assert "store_snapshot" not in MCP_SERVERS["memory"]
    service.close()


def test_persistent_authorizer_binds_choice_and_checks_deadline(tmp_path: Path) -> None:
    database = tmp_path / "state" / "agent.sqlite3"
    run_id = start_run(database)
    snapshot_id = seal_evidence(database, tmp_path / "cache", run_id)
    service = MemoryService(database)
    tools = MemoryTools(service)
    business = {"series_id": 4, "league_code": "MAIN", "user_card_ids": [11]}
    decision_id = tools.record_decision(
        run_id=run_id, decision_type="TEAM", subject_type="series", subject_id="4",
        snapshot_ids=[snapshot_id], alternatives=[],
        choice={"tool": "fantasy_create_team", "arguments": business}, rationale="fixture",
    )
    arguments = {
        "run_id": run_id, "operation_id": str(uuid.uuid4()), "decision_id": decision_id,
        **business,
    }
    auth_now = dt.datetime.now(UTC)
    authorizer = PersistentActAuthorizer(
        service.store, clock=lambda: auth_now,
        series_reader=lambda _series_id: {
            "observedAt": auth_now.isoformat(),
            "data": {"teamDeadline": (auth_now + dt.timedelta(minutes=10)).isoformat()},
        },
    )
    authorizer.authorize_write("fantasy_create_team", arguments)
    reservation = service.store.connection.execute(
        "SELECT * FROM act_authorizations WHERE decision_id=?", (decision_id,)
    ).fetchone()
    assert reservation["operation_id"] == arguments["operation_id"]
    with pytest.raises(FailClosedError, match="different parameters"):
        authorizer.authorize_write(
            "fantasy_create_team",
            {**arguments, "operation_id": str(uuid.uuid4())},
        )
    with pytest.raises(FailClosedError, match="arguments do not match"):
        authorizer.authorize_write("fantasy_create_team", {**arguments, "user_card_ids": [99]})
    too_late = PersistentActAuthorizer(
        service.store, clock=lambda: auth_now,
        series_reader=lambda _series_id: {
            "observedAt": auth_now.isoformat(),
            "data": {"teamDeadline": (auth_now + dt.timedelta(minutes=4)).isoformat()},
        },
    )
    with pytest.raises(FailClosedError, match="deadline"):
        too_late.authorize_write("fantasy_create_team", arguments)
    service.close()


def test_real_memory_mcp_adapter_handles_concurrent_reads(tmp_path: Path) -> None:
    database = tmp_path / "state" / "agent.sqlite3"
    start_run(database)
    service = MemoryService(database)
    server = build_server("memory", MemoryTools(service))

    async def exercise() -> None:
        results = []
        async with anyio.create_task_group() as group:
            async def call() -> None:
                results.append(await server.call_tool("get_open_intents", {}))
            for _ in range(12):
                group.start_soon(call)
        assert len(results) == 12
        assert all(not item.is_error for item in results)

    anyio.run(exercise)
    service.close()


def test_open_intent_blocks_new_persistent_act(tmp_path: Path) -> None:
    database = tmp_path / "state" / "agent.sqlite3"
    run_id = start_run(database)
    snapshot_id = seal_evidence(database, tmp_path / "cache", run_id)
    service = MemoryService(database)
    tools = MemoryTools(service)
    decision_id = tools.record_decision(
        run_id=run_id, decision_type="PACK", subject_type="pack", subject_id="3",
        snapshot_ids=[snapshot_id], alternatives=[],
        choice={"tool": "fantasy_buy_pack", "arguments": {"pack_id": 3}}, rationale="fixture",
    )
    service.store.plan_intent(
        operation_id="older-unknown", run_id=run_id, kind="marketplace", target_id="8",
        request={"listing": 8}, is_economic=True, decision_id=decision_id,
    )
    service.store.mark_intent_sent("older-unknown")
    authorizer = PersistentActAuthorizer(
        service.store, series_reader=lambda _series: {},
        clock=lambda: dt.datetime.now(UTC),
    )
    with pytest.raises(FailClosedError, match="open intents"):
        authorizer.authorize_write("fantasy_buy_pack", {
            "run_id": run_id, "operation_id": "new-pack", "decision_id": decision_id,
            "pack_id": 3,
        })
    assert service.store.connection.execute(
        "SELECT COUNT(*) FROM interventions WHERE reason='ACT_DENIED'"
    ).fetchone()[0] == 1
    service.close()


def test_seal_freezes_records_atomically_against_concurrent_attach(tmp_path: Path) -> None:
    database = tmp_path / "state" / "agent.sqlite3"
    run_id = start_run(database)
    journal = DurableResearchSnapshotJournal(database, clock=lambda: NOW)
    coordinator = SnapshotCoordinator(journal, clock=lambda: NOW)
    collection = coordinator.begin(run_id)
    cache = RawPayloadCache(tmp_path / "cache", clock=lambda: NOW)
    first = cache.store(source="match", object_id="1", payload={"id": 1})
    late = cache.store(source="match", object_id="2", payload={"id": 2})
    collection_id = str(collection.snapshot_id)
    coordinator.attach(collection_id, first)

    freeze_reached = threading.Event()
    allow_seal = threading.Event()
    attach_started = threading.Event()
    attach_done = threading.Event()
    seal_result = []
    attach_errors = []
    original_store_blob = journal.store.store_blob

    def blocking_store_blob(value):
        freeze_reached.set()
        assert allow_seal.wait(5)
        return original_store_blob(value)

    journal.store.store_blob = blocking_store_blob

    def seal() -> None:
        seal_result.append(coordinator.seal(collection_id))

    def attach() -> None:
        attach_started.set()
        try:
            coordinator.attach(collection_id, late)
        except Exception as exc:
            attach_errors.append(exc)
        finally:
            attach_done.set()

    seal_thread = threading.Thread(target=seal)
    attach_thread = threading.Thread(target=attach)
    seal_thread.start()
    assert freeze_reached.wait(5)
    attach_thread.start()
    assert attach_started.wait(5)
    assert not attach_done.wait(0.1), "attach must wait behind the seal transaction"
    allow_seal.set()
    seal_thread.join(5)
    attach_thread.join(5)

    assert not seal_thread.is_alive() and not attach_thread.is_alive()
    assert isinstance(seal_result[0].snapshot_id, int)
    assert len(attach_errors) == 1
    assert isinstance(attach_errors[0], SnapshotSealedError)
    snapshot_row = journal.store.connection.execute(
        "SELECT payload_hash, payload_path FROM snapshots WHERE id=?",
        (seal_result[0].snapshot_id,),
    ).fetchone()
    manifest = journal.store.load_blob(snapshot_row["payload_hash"], snapshot_row["payload_path"])
    assert [item["payload_hash"] for item in manifest["records"]] == [first.payload_hash]
    assert late.payload_hash not in {item["payload_hash"] for item in manifest["records"]}
    journal.close()
