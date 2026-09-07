from __future__ import annotations

import dataclasses
import json
from pathlib import Path

import pytest
from mcp.server.mcpserver.exceptions import ToolError

from polemica_agent.common.storage import AuditStore, FailClosedError
from polemica_agent.compute_mcp.dataset import DatasetError, load_player_game_rows
from polemica_agent.fantasy_mcp.service import FantasyService
from polemica_agent.fantasy_mcp.registry import build_tool_registry
from polemica_agent.memory_mcp.evidence import DurableResearchSnapshotJournal
from polemica_agent.memory_mcp.service import MemoryService
from polemica_agent.mcp_runtime.memory_tools import MemoryTools
from polemica_agent.research_mcp.snapshots import SnapshotCoordinator


class EvidenceClient:
    def __init__(self):
        self.calls = []
        self.bad_path = None
        self.bad_value = None

    def _get(self, path, query=None):
        self.calls.append((path, query))
        if path == self.bad_path:
            if isinstance(self.bad_value, Exception):
                raise self.bad_value
            return self.bad_value
        if path == "/api/v1/periodic-ratings/current":
            return None
        if path.endswith(("/cards", "/fantasy-teams", "/packs", "/leagues", "/series-open-for-team")):
            return [{"id": 1}]
        return {"id": 1, "secretToken": "must-not-persist"}


@pytest.fixture
def context(tmp_path):
    store = AuditStore(tmp_path / "agent.sqlite3")
    store.start_run(run_id="run", model="test", prompt_hash="p", tools_hash="t", config_hash="c")
    journal = DurableResearchSnapshotJournal(store.database_path)
    coordinator = SnapshotCoordinator(journal)
    collection = "55555555-5555-4555-8555-555555555555"
    journal.create_collecting("run", collection, "2026-09-07T00:00:00+00:00")
    client = EvidenceClient()
    service = FantasyService(client, store)
    yield store, journal, coordinator, collection, client, service
    journal.close()
    store.close()


def test_fantasy_only_evidence_seals_and_authorizes_real_memory_decision(context):
    store, journal, coordinator, collection, client, service = context
    result = service.collect_evidence(run_id="run", collection_id=collection,
                                      achievement_codes=["first_card"], series_ids=[12])
    assert result.data["sourceCount"] == 12
    assert result.data["nextAction"] == "SEAL"
    assert ("/api/v1/me/cards", {"seriesId": 12}) in client.calls
    assert journal.get(collection).state == "COLLECTING"
    assert all(record.source == "fantasy-user-api" for record in journal.get(collection).records)
    assert len(journal.get(collection).records) == 12  # Equal lists from distinct endpoints do not collide.
    assert "must-not-persist" not in str(result.data)
    for record in journal.get(collection).records:
        assert "must-not-persist" not in Path(record.blob_path).read_text()
        persisted = json.loads(Path(record.blob_path).read_text())
        observation = next(item for item in result.data["observations"] if item["objectId"] == record.object_id)
        assert persisted["observedAt"] == observation["observedAt"]
    snapshot = coordinator.seal(collection)
    memory = MemoryService(store.database_path)
    try:
        decision = MemoryTools(memory).record_decision(
            run_id="run", decision_type="CLAIM", subject_type="achievement", subject_id="first_card",
            snapshot_ids=[snapshot.snapshot_id], alternatives=[], choice={"code": "first_card"}, rationale="fresh state",
        )
        assert decision > 0
    finally:
        memory.close()


@pytest.mark.parametrize("bad", [None, "invalid", [1], TimeoutError("secret network data")])
def test_failure_does_not_attach_subset_and_cannot_authorize(context, bad):
    _, journal, coordinator, collection, client, service = context
    client.bad_path = "/api/v1/store/packs"
    client.bad_value = bad
    with pytest.raises(ToolError, match="FANTASY_EVIDENCE_FAILED") as error:
        service.collect_evidence(run_id="run", collection_id=collection)
    assert "secret network data" not in str(error.value)
    snapshot = journal.get(collection)
    assert snapshot.records == ()
    assert snapshot.completeness == "PARTIAL"
    with pytest.raises(FailClosedError):
        coordinator.seal(collection)


@pytest.mark.parametrize("arguments", [
    {"achievement_codes": ["../me"]}, {"achievement_codes": ["x/y"]},
    {"achievement_codes": ["x"] * 21}, {"achievement_codes": [1]},
    {"series_ids": [True]}, {"series_ids": [-1]}, {"series_ids": list(range(1, 12))},
    {"series_ids": [1, 1]}, {"achievement_codes": "x"}, {"series_ids": "1"},
])
def test_selectors_are_bounded_before_any_network(context, arguments):
    _, journal, _, collection, client, service = context
    with pytest.raises(ValueError):
        service.collect_evidence(run_id="run", collection_id=collection, **arguments)
    assert not client.calls
    assert journal.get(collection).completeness == "COMPLETE"


@pytest.mark.parametrize("state", ["cross-run", "sealed", "terminal"])
def test_cannot_collect_for_wrong_run_or_closed_collection(context, state):
    store, _, coordinator, collection, client, service = context
    run = "run"
    if state == "cross-run":
        store.start_run(run_id="other", model="test", prompt_hash="p", tools_hash="t", config_hash="c")
        run = "other"
    elif state == "sealed":
        service.collect_evidence(run_id=run, collection_id=collection)
        coordinator.seal(collection)
    else:
        store.finish_run(run, "FAILED")
    client.calls.clear()
    with pytest.raises(Exception):
        service.collect_evidence(run_id=run, collection_id=collection)
    assert not client.calls


def test_registry_cannot_accept_forged_payload_or_url(context):
    _, journal, coordinator, collection, client, service = context
    registry = build_tool_registry(service)
    for key in ("payload", "url", "records"):
        with pytest.raises(ValueError, match="unknown"):
            registry.call("fantasy_collect_evidence", {"run_id": "run", "collection_id": collection, key: {}})
    with pytest.raises(FailClosedError):
        coordinator.seal(collection)
    assert not client.calls


def test_hash_tampering_is_detected_at_seal(context):
    _, journal, coordinator, collection, _, service = context
    service.collect_evidence(run_id="run", collection_id=collection)
    Path(journal.get(collection).records[0].blob_path).write_text("{}")
    with pytest.raises(FailClosedError, match="hash mismatch"):
        coordinator.seal(collection)


def test_compute_does_not_treat_fantasy_as_player_games(context, tmp_path):
    _, journal, _, collection, _, service = context
    service.collect_evidence(run_id="run", collection_id=collection)
    records = [dataclasses.asdict(record) for record in journal.get(collection).records]
    with pytest.raises(DatasetError, match="COMPUTE_PLAYER_DATA_MISSING"):
        load_player_game_rows(records, research_cache=tmp_path, player_ids=[1])
