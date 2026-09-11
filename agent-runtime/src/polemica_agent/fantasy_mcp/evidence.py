"""Broker-owned Fantasy observations for the existing COLLECT/SEAL boundary."""
from __future__ import annotations

import re
from urllib.parse import urlencode
from typing import TYPE_CHECKING, Any

from mcp.server.mcpserver.exceptions import ToolError

from polemica_agent.common.canonical import redact
from polemica_agent.memory_mcp.evidence import DurableResearchSnapshotJournal
from polemica_agent.research_mcp.cache import RawPayloadCache, canonical_json_bytes

from .types import ReadEnvelope, observed_now

if TYPE_CHECKING:
    from .service import FantasyService


def collect_evidence(
    service: FantasyService, *, run_id: str, collection_id: str,
    achievement_codes: list[str] | None = None, series_ids: list[int] | None = None,
    fantasy_player_ids: list[int] | None = None,
    marketplace_analytics: list[dict[str, Any]] | None = None,
    marketplace_searches: list[dict[str, Any]] | None = None,
    detail: str = "compact",
) -> ReadEnvelope:
    """Fetch a closed set of authenticated reads; never accept model-authored evidence."""
    if detail not in ("compact", "full"):
        raise ValueError("detail must be compact or full")
    for label, value in (("run_id", run_id), ("collection_id", collection_id)):
        if not isinstance(value, str) or not 1 <= len(value) <= 128:
            raise ValueError(f"{label} must contain 1..128 characters")
    codes = _bounded_list(achievement_codes, 20, "achievement_codes")
    ids = _bounded_list(series_ids, 10, "series_ids")
    players = _bounded_list(fantasy_player_ids, 20, "fantasy_player_ids")
    analytics = _bounded_list(marketplace_analytics, 10, "marketplace_analytics")
    searches = _bounded_list(marketplace_searches, 5, "marketplace_searches")
    search_keys = set()
    for selector in searches:
        if (not isinstance(selector, dict)
                or not {"fantasy_player_id", "rarity"} <= set(selector)
                or set(selector) - {"fantasy_player_id", "rarity", "page"}
                or type(selector["fantasy_player_id"]) is not int or selector["fantasy_player_id"] < 1
                or selector["rarity"] not in ("COMMON", "RARE", "EPIC", "LEGENDARY")
                or type(selector.get("page", 0)) is not int or not 0 <= selector.get("page", 0) <= 100):
            raise ValueError("marketplace_searches requires positive fantasy_player_id, rarity, optional page 0..100")
        key = (selector["fantasy_player_id"], selector["rarity"], selector.get("page", 0))
        if key in search_keys:
            raise ValueError("marketplace_searches must be unique")
        search_keys.add(key)
    if any(not isinstance(code, str) or re.fullmatch(r"[A-Za-z0-9_-]{1,128}", code) is None for code in codes):
        raise ValueError("achievement_codes must be safe path segments")
    if any(type(series_id) is not int or series_id < 1 for series_id in ids):
        raise ValueError("series_ids must be positive integers")
    if len(set(codes)) != len(codes) or len(set(ids)) != len(ids):
        raise ValueError("evidence selectors must be unique")
    if any(type(player_id) is not int or player_id < 1 for player_id in players):
        raise ValueError("fantasy_player_ids must be positive integers")
    if len(set(players)) != len(players):
        raise ValueError("fantasy_player_ids must be unique")
    for selector in analytics:
        if (not isinstance(selector, dict) or set(selector) != {"fantasy_player_id", "rarity"}
                or type(selector["fantasy_player_id"]) is not int or selector["fantasy_player_id"] < 1
                or selector["rarity"] not in ("COMMON", "RARE", "EPIC", "LEGENDARY")):
            raise ValueError("marketplace_analytics requires positive fantasy_player_id and valid rarity only")
    if len({(item["fantasy_player_id"], item["rarity"]) for item in analytics}) != len(analytics):
        raise ValueError("marketplace_analytics must be unique")

    journal = DurableResearchSnapshotJournal(service.store.database_path)
    try:
        journal.assert_collecting_run(collection_id, run_id)
        requests = [
            ("/api/v1/me", None, dict),
            ("/api/v1/me/cards", None, list),
            ("/api/v1/me/fantasy-teams", None, list),
            ("/api/v1/store/packs", None, list),
            ("/api/v1/achievements", None, dict),
            ("/api/v1/me/economy-info", None, dict),
            ("/api/v1/tournaments/series-open-for-team", None, list),
            ("/api/v1/periodic-ratings/current", None, (dict, type(None))),
        ]
        requests.extend((f"/api/v1/achievements/{code}/claim-state", None, dict) for code in codes)
        requests.extend((f"/api/v1/players/{player_id}", None, dict) for player_id in players)
        requests.extend(("/api/v1/marketplace/analytics/detail",
                         {"fantasyPlayerId": item["fantasy_player_id"], "rarity": item["rarity"]}, dict)
                        for item in analytics)
        requests.extend(("/api/v1/marketplace/listings",
                         {"fantasyPlayerId": item["fantasy_player_id"], "rarity": item["rarity"],
                          "page": item.get("page", 0), "size": 100, "sortBy": "price_asc"}, dict)
                        for item in searches)
        for series_id in ids:
            requests.extend([
                (f"/api/v1/series/{series_id}", None, dict),
                (f"/api/v1/series/{series_id}/leagues", None, list),
                ("/api/v1/me/cards", {"seriesId": series_id}, list),
            ])
        observations: list[dict[str, Any]] = []
        try:
            for path, query, expected_type in requests:
                response = service._read(path, query)
                if not isinstance(response.data, expected_type):
                    raise ValueError("Fantasy evidence response has an unexpected shape")
                if path == "/api/v1/marketplace/listings":
                    _validate_market_page(response.data, query)
                if isinstance(response.data, list) and any(not isinstance(item, dict) for item in response.data):
                    raise ValueError("Fantasy evidence list contains an invalid item")
                data = redact(response.data)
                canonical_json_bytes(data)  # Validate the entire batch before persisting any records.
                object_id = path + ("?" + urlencode(sorted(query.items())) if query else "")
                observations.append({"source": "fantasy-user-api", "objectId": object_id,
                                     "observedAt": response.observed_at, "data": data})
            cache = RawPayloadCache(service.store.state_dir / "fantasy-evidence", parser_version="fantasy-evidence-v1")
            records = [cache.store(source="fantasy-user-api", object_id=item["objectId"],
                                   payload=item) for item in observations]
            journal.attach_many(collection_id, run_id, records)
        except Exception:
            journal.fail_collection(collection_id, run_id)
            raise ToolError(
                "FANTASY_EVIDENCE_FAILED: collection is PARTIAL; no batch records were attached. "
                "Begin a new collection and retry fantasy_collect_evidence. Do not use this collection for ACT."
            ) from None
        for item, record in zip(observations, records, strict=True):
            item["payloadHash"] = record.payload_hash
        from .compact import compact_observations
        return ReadEnvelope(observed_now(), "fantasy-user-api", {
            "collectionId": collection_id, "sourceCount": len(records),
            "observations": compact_observations(observations) if detail == "compact" else observations,
            "presentation": detail,
            "presentationNote": "All sources and cards retained; payloadHash identifies full immutable evidence, "
                "not this presentation. Compact omits image URLs, card flavour text and claimed achievement "
                "display text. Existing read tools return full details; detail=full returns full collection.",
            "nextAction": "SEAL",
        })
    finally:
        journal.close()


def _bounded_list(value: Any, maximum: int, label: str) -> list[Any]:
    if value is None:
        return []
    if not isinstance(value, list) or len(value) > maximum:
        raise ValueError(f"{label} must be an array of at most {maximum} items")
    return value


def _validate_market_page(data: dict[str, Any], query: dict[str, Any]) -> None:
    content = data.get("content")
    if (not isinstance(content, list) or len(content) > 100
            or any(type(data.get(key)) is not int or data[key] < 0
                   for key in ("totalElements", "totalPages", "page", "size"))
            or data["page"] != query["page"] or data["size"] != query["size"]):
        raise ValueError("Invalid marketplace evidence page")
    ids = set()
    for item in content:
        if (not isinstance(item, dict) or type(item.get("listingId")) is not int or item["listingId"] < 1
                or item["listingId"] in ids or type(item.get("price")) is not int or item["price"] < 1
                or type(item.get("canBuy")) is not bool or not isinstance(item.get("card"), dict)):
            raise ValueError("Invalid marketplace evidence listing")
        card = item["card"]
        if (type(card.get("userCardId")) is not int or card["userCardId"] < 1
                or card.get("fantasyPlayerId") != query["fantasyPlayerId"]
                or card.get("rarity") != query["rarity"] or not isinstance(card.get("perks"), list)):
            raise ValueError("Invalid marketplace evidence card")
        ids.add(item["listingId"])
