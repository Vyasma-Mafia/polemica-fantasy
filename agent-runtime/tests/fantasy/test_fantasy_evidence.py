from __future__ import annotations

import dataclasses
import json
from pathlib import Path
from urllib.parse import urlsplit

import pytest
from mcp.server.mcpserver.exceptions import ToolError

from polemica_agent.common.storage import AuditStore, FailClosedError
from polemica_agent.compute_mcp.dataset import DatasetError, load_player_game_rows
from polemica_agent.fantasy_mcp.service import FantasyService
from polemica_agent.fantasy_mcp.client import FantasyHttpClient, HttpResponse
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
    {"fantasy_player_ids": [True]}, {"fantasy_player_ids": [0]},
    {"fantasy_player_ids": [1, 1]}, {"fantasy_player_ids": list(range(1, 22))},
    {"marketplace_analytics": [{"fantasy_player_id": 1}]},
    {"marketplace_analytics": [{"fantasy_player_id": True, "rarity": "EPIC"}]},
    {"marketplace_analytics": [{"fantasy_player_id": 1, "rarity": "invalid"}]},
    {"marketplace_analytics": [{"fantasy_player_id": 1, "rarity": "EPIC", "url": "/admin"}]},
    {"marketplace_analytics": [{"fantasy_player_id": 1, "rarity": "EPIC"}] * 2},
    {"marketplace_analytics": [{"fantasy_player_id": n, "rarity": "EPIC"} for n in range(1, 12)]},
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


def test_player_and_market_evidence_is_bound_to_selectors_and_sealable(context):
    _, journal, coordinator, collection, client, service = context
    result = build_tool_registry(service).call("fantasy_collect_evidence", {
        "run_id": "run", "collection_id": collection, "fantasy_player_ids": [12],
        "marketplace_analytics": [{"fantasy_player_id": 12, "rarity": "EPIC"},
                                  {"fantasy_player_id": 12, "rarity": "COMMON"}],
    })
    assert result["data"]["sourceCount"] == 11
    assert ("/api/v1/players/12", None) in client.calls
    objects = {record.object_id for record in journal.get(collection).records}
    assert "/api/v1/marketplace/analytics/detail?fantasyPlayerId=12&rarity=EPIC" in objects
    assert "/api/v1/marketplace/analytics/detail?fantasyPlayerId=12&rarity=COMMON" in objects
    assert coordinator.seal(collection).snapshot_id > 0


@pytest.mark.parametrize("path,selectors", [
    ("/api/v1/players/12", {"fantasy_player_ids": [12]}),
    ("/api/v1/marketplace/analytics/detail", {"marketplace_analytics": [{"fantasy_player_id": 12, "rarity": "EPIC"}]}),
])
def test_optional_read_failure_does_not_attach_base_batch(context, path, selectors):
    _, journal, _, collection, client, service = context
    client.bad_path, client.bad_value = path, TimeoutError("secret")
    with pytest.raises(ToolError, match="FANTASY_EVIDENCE_FAILED"):
        service.collect_evidence(run_id="run", collection_id=collection, **selectors)
    assert journal.get(collection).records == ()
    assert journal.get(collection).completeness == "PARTIAL"


def test_real_http_client_allows_identity_service_and_sealed_evidence(context):
    store, _, coordinator, collection, fixture, _ = context
    class Transport:
        def request(self, *, method, url, **_kwargs):
            assert method == "GET"
            path = urlsplit(url).path
            if path == "/api/v1/players/12":
                return HttpResponse(200, {"fantasyPlayerId": 12, "polemicaUserId": 456,
                                          "playerNickname": "Player", "playerPhotoUrl": None})
            return HttpResponse(200, fixture._get(path))
    service = FantasyService(FantasyHttpClient("https://fantasy.example", "test", transport=Transport()), store)
    assert service.get_player(12).data["polemicaUserId"] == 456
    result = service.collect_evidence(run_id="run", collection_id=collection, fantasy_player_ids=[12])
    identity = next(item for item in result.data["observations"] if item["objectId"] == "/api/v1/players/12")
    assert identity["data"]["polemicaUserId"] == 456
    assert coordinator.seal(collection).snapshot_id > 0


@pytest.mark.parametrize("method,path", [
    (method, "/api/v1/players/12") for method in ("POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
] + [("GET", path) for path in (
    "/api/v1/players", "/api/v1/players/12/profile", "/api/v1/players/12/",
    "/api/v1/players/0", "/api/v1/players/-1", "/api/v1/players/name", "/api/v1/players/12%2fprofile",
)])
def test_real_http_client_keeps_other_player_paths_and_methods_closed(method, path):
    class Transport:
        def request(self, **_kwargs):
            pytest.fail("Rejected player route reached network")
    client = FantasyHttpClient("https://fantasy.example", "test", transport=Transport())
    with pytest.raises(ValueError):
        client._request(method, path)


def market_page(content=None):
    return {"content": content if content is not None else [{"listingId": 50402, "price": 45,
            "canBuy": True, "card": {"userCardId": 72, "fantasyPlayerId": 12, "rarity": "RARE",
            "perks": [{"perkId": "lastHeroGuess", "bonusPoints": 1.3}]}}],
            "page": 0, "size": 100, "totalPages": 1, "totalElements": 1}


@pytest.mark.parametrize("empty", [False, True])
def test_market_listings_real_client_are_sealed_with_exact_details(context, empty):
    store, journal, coordinator, collection, fixture, _ = context
    from urllib.parse import parse_qs
    class Transport:
        def request(self, *, method, url, **_kwargs):
            assert method == "GET"
            parsed = urlsplit(url)
            if parsed.path == "/api/v1/marketplace/listings":
                assert parse_qs(parsed.query) == {"fantasyPlayerId": ["12"], "rarity": ["RARE"],
                    "page": ["0"], "size": ["100"], "sortBy": ["price_asc"]}
                return HttpResponse(200, market_page([] if empty else None))
            return HttpResponse(200, fixture._get(parsed.path))
    service = FantasyService(FantasyHttpClient("https://fantasy.example", "test", transport=Transport()), store)
    result = service.collect_evidence(run_id="run", collection_id=collection,
        marketplace_searches=[{"fantasy_player_id": 12, "rarity": "RARE"}])
    observation = next(i for i in result.data["observations"] if "/marketplace/listings?" in i["objectId"])
    assert observation["data"]["content"] == market_page([] if empty else None)["content"]
    assert "fantasyPlayerId=12&page=0&rarity=RARE&size=100&sortBy=price_asc" in observation["objectId"]
    assert coordinator.seal(collection).snapshot_id > 0
    record = next(r for r in journal.get(collection).records if "/marketplace/listings?" in r.object_id)
    assert json.loads(Path(record.blob_path).read_text())["data"] == observation["data"]


@pytest.mark.parametrize("selector", [
    [{"fantasy_player_id": True, "rarity": "RARE"}], [{"fantasy_player_id": 12, "rarity": "RARE", "page": -1}],
    [{"fantasy_player_id": 12, "rarity": "RARE", "url": "https://evil"}],
    [{"fantasy_player_id": 12, "rarity": "bad"}],
    [{"fantasy_player_id": 12, "rarity": "RARE"}] * 6,
    [{"fantasy_player_id": 12, "rarity": "RARE"}, {"fantasy_player_id": 12, "rarity": "RARE", "page": 0}],
])
def test_market_search_bad_selectors_do_not_fetch(context, selector):
    _, _, _, collection, client, service = context
    with pytest.raises(ValueError):
        service.collect_evidence(run_id="run", collection_id=collection, marketplace_searches=selector)
    assert client.calls == []


@pytest.mark.parametrize("bad", [{}, market_page([{"listingId": 50402}]), TimeoutError("private detail")])
def test_market_search_failure_is_atomic(context, bad):
    _, journal, _, collection, client, service = context
    client.bad_path = "/api/v1/marketplace/listings"
    client.bad_value = bad
    with pytest.raises(ToolError, match="FANTASY_EVIDENCE_FAILED"):
        service.collect_evidence(run_id="run", collection_id=collection,
            marketplace_searches=[{"fantasy_player_id": 12, "rarity": "RARE"}])
    assert journal.get(collection).records == ()
    assert journal.get(collection).completeness == "PARTIAL"
