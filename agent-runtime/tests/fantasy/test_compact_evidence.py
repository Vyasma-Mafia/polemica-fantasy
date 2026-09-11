from __future__ import annotations

import copy
import json
from pathlib import Path

import pytest

from polemica_agent.fantasy_mcp.compact import compact_observations
from polemica_agent.memory_mcp.authorization import PersistentActAuthorizer
from polemica_agent.memory_mcp.service import MemoryService
from polemica_agent.mcp_runtime.memory_tools import MemoryTools
from test_fantasy_evidence import context  # noqa: F401


def representative_observations():
    image = "https://cdn.example/cards/" + "versioned-photo-path/" * 8 + "photo.webp"
    cards = [{"id": i, "fantasyPlayerId": i, "rarity": "COMMON", "playerNickname": f"Player {i}",
        "imageUrl": image, "playerPhotoUrl": image, "description": "Card illustration " * 8,
        "skinCode": "GRAND_SLAM", "usesRemaining": i % 4, "timesRenewed": 2,
        "value": 100, "minListingPrice": 40, "canJoinMoreLeagues": False,
        "activeMarketplaceListing": None, "perks": [{"id": "ninja", "bonusPoints": 2,
        "description": "Winning while neither checked nor shot"}]} for i in range(104)]
    achievements = [{"code": f"milestone_{i}", "state": "CLAIMED" if i < 70 else "IN_PROGRESS",
        "conditionType": "SUBMIT_TEAM", "title": "Participate in leagues", "description": "Rules " * 20,
        "progressValue": i, "targetValue": 100, "imageUrl": image,
        "rewards": [{"id": i, "type": "FANTIKI", "amount": 50}],
        "pendingChoices": [{"rewardId": i, "options": [{"optionId": "x", "polemicaUserId": 12}]}]}
        for i in range(92)]
    return [{"objectId": "/api/v1/me/cards", "data": cards, "payloadHash": "cards"},
        {"objectId": "/api/v1/achievements", "payloadHash": "achievements",
         "data": {"categories": [{"code": "play", "achievements": achievements}],
                  "summary": {"claimed": 70, "totalVisible": 92}}}]


def test_compact_reduction_preserves_all_cards_and_milestones():
    original = representative_observations()
    untouched = copy.deepcopy(original)
    compact = compact_observations(original)
    assert original == untouched
    before, after = (len(json.dumps(value)) for value in (original, compact))
    assert after < before * .60, (before, after)
    assert len(compact[0]["data"]) == 104
    for full, shown in zip(original[0]["data"], compact[0]["data"], strict=True):
        assert shown == {k: v for k, v in full.items() if k not in {"imageUrl", "playerPhotoUrl", "description"}}
    milestones = compact[1]["data"]["categories"][0]["achievements"]
    assert len(milestones) == 92
    assert milestones[0]["pendingChoices"] and milestones[0]["rewards"]
    assert milestones[-1]["description"] == original[1]["data"]["categories"][0]["achievements"][-1]["description"]


def test_market_choices_and_unknown_economy_fields_survive():
    data = {"listingId": 7, "price": 150, "canBuy": False, "newEconomicRule": {"limit": 2},
        "card": {"userCardId": 9, "fantasyPlayerId": 12, "rarity": "EPIC", "skinCode": "TROPHY",
        "perks": [{"perkId": "ninja", "bonusPoints": 2, "description": "Rules"}]}}
    assert compact_observations([{"objectId": "/api/v1/marketplace/listings", "data": data}])[0]["data"] == data


@pytest.mark.parametrize("detail", ["compact", "full"])
def test_presentation_never_changes_sealed_payload_and_authorizes_act(context, monkeypatch, detail):
    store, journal, coordinator, collection, client, service = context
    original_get = client._get
    card_data = representative_observations()[0]["data"]
    monkeypatch.setattr(client, "_get", lambda path, query=None: card_data if path == "/api/v1/me/cards" else original_get(path, query))
    result = service.collect_evidence(run_id="run", collection_id=collection, detail=detail)
    record = next(record for record in journal.get(collection).records if record.object_id == "/api/v1/me/cards")
    persisted = json.loads(Path(record.blob_path).read_text())
    assert persisted["data"] == card_data
    shown = next(item for item in result.data["observations"] if item["objectId"] == record.object_id)
    assert shown["payloadHash"] == record.payload_hash
    assert ("imageUrl" in shown["data"][0]) == (detail == "full")
    snapshot = coordinator.seal(collection)
    memory = MemoryService(store.database_path)
    try:
        arguments = {"achievement_code": "first_card"}
        decision = MemoryTools(memory).record_decision(run_id="run", decision_type="CLAIM",
            subject_type="achievement", subject_id="first_card", snapshot_ids=[snapshot.snapshot_id],
            alternatives=[], choice={"tool": "fantasy_claim_achievement", "arguments": arguments}, rationale="test")
        PersistentActAuthorizer(memory.store, series_reader=lambda _: {}).authorize_write("fantasy_claim_achievement",
            {"run_id": "run", "operation_id": "12345678-1234-4234-8234-123456789012", "decision_id": decision, **arguments})
    finally:
        memory.close()


def test_invalid_presentation_does_not_fetch(context):
    _, _, _, collection, client, service = context
    with pytest.raises(ValueError, match="detail"):
        service.collect_evidence(run_id="run", collection_id=collection, detail="summary")
    assert client.calls == []


def test_full_and_compact_have_identical_immutable_bytes_and_hashes(context, monkeypatch):
    _, journal, coordinator, collection, client, service = context
    monkeypatch.setattr("polemica_agent.fantasy_mcp.service.observed_now", lambda: "2026-09-11T10:00:00+00:00")
    original_get = client._get
    cards = representative_observations()[0]["data"]
    monkeypatch.setattr(client, "_get", lambda path, query=None: cards if path == "/api/v1/me/cards" else original_get(path, query))
    service.collect_evidence(run_id="run", collection_id=collection)
    second = "66666666-6666-4666-8666-666666666666"
    journal.create_collecting("run", second, "2026-09-07T00:00:00+00:00")
    service.collect_evidence(run_id="run", collection_id=second, detail="full")
    first_records, second_records = journal.get(collection).records, journal.get(second).records
    assert len(first_records) == len(second_records) == 8
    for left, right in zip(first_records, second_records, strict=True):
        assert left.payload_hash == right.payload_hash
        assert Path(left.blob_path).read_bytes() == Path(right.blob_path).read_bytes()
    assert coordinator.seal(collection).snapshot_id > 0
    assert coordinator.seal(second).snapshot_id > 0
