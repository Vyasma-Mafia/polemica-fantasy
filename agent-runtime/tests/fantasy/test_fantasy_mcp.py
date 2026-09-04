from __future__ import annotations

import copy
import datetime as dt
import asyncio
import urllib.parse
from pathlib import Path
from typing import Any, Mapping

import pytest

from polemica_agent.common.storage import AuditStore, FailClosedError, IntentConflictError
from polemica_agent.fantasy_mcp import FantasyHttpClient, FantasyService, HttpResponse, build_tool_registry
from polemica_agent.mcp_runtime.fantasy_adapter import FantasyRegistryAdapter
from polemica_agent.mcp_runtime.registry import ToolPolicy, build_server


class FakeFantasyTransport:
    def __init__(self) -> None:
        self.calls: list[dict[str, Any]] = []
        self.profile = {"id": 1, "telegramId": 777, "fantiki": 1000}
        self.cards = [
            {"id": i, "rarity": "EPIC", "usesRemaining": 2, "timesRenewed": 0, "perks": []}
            for i in range(1, 8)
        ]
        self.packs = [{"id": 9, "packOpensUsed": 0, "pendingChoice": None}]
        self.team: dict[str, Any] | None = None
        self.listings: list[dict[str, Any]] = [
            {"listingId": 10, "price": 70, "card": {"userCardId": 99}}
        ]
        self.transactions: dict[int, dict[str, Any]] = {}
        self.achievement_state = "COMPLETED"
        self.reward = {
            "id": 20,
            "status": "PENDING",
            "version": 0,
            "selection": {},
            "issuedAt": None,
        }
        self.next_card = 100
        self.next_listing = 11
        self.team_timeout: str | None = None
        self.pack_timeout_before_commit = False
        self.recycle_timeout_after_commit = False
        self.cancel_sold_after_timeout = False
        self.choice_timeout_after_commit = False
        self.team_http_status: int | None = None

    def request(
        self,
        *,
        method: str,
        url: str,
        headers: Mapping[str, str],
        json_body: Any | None,
        timeout_seconds: float,
    ) -> HttpResponse:
        parsed = urllib.parse.urlsplit(url)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)
        self.calls.append(
            {"method": method, "path": path, "query": query, "headers": dict(headers), "body": copy.deepcopy(json_body)}
        )
        assert headers["Authorization"] == "Bearer secret-token"
        assert timeout_seconds == 5

        if method == "GET":
            return self._get(path)

        if path.endswith("/fantasy-team"):
            if self.team_http_status is not None:
                return HttpResponse(self.team_http_status, {"message": "ambiguous proxy response"})
            if self.team_timeout == "before":
                raise TimeoutError("before commit")
            self.team = {
                "seriesId": int(path.split("/")[4]),
                "leagueCode": path.split("/")[6],
                "slots": [{"slot": slot, "userCardId": card} for slot, card in enumerate(json_body["userCardIds"], 1)],
            }
            if self.team_timeout == "after":
                raise TimeoutError("after commit")
            return HttpResponse(200, self.team)
        if method == "POST" and path == "/api/v1/store/packs/9/buy":
            if self.pack_timeout_before_commit:
                raise TimeoutError("pack not committed")
            self.packs[0]["packOpensUsed"] += 1
            self.cards.append(self._new_card())
            return HttpResponse(200, {"opened": True})
        if method == "POST" and path == "/api/v1/store/pack-choices/50/select":
            self.packs[0]["pendingChoice"] = None
            self.cards.append(self._new_card())
            return HttpResponse(200, {"selected": True})
        if method == "POST" and path == "/api/v1/marketplace/listings":
            listing = {"listingId": self.next_listing, "price": json_body["price"], "card": {"userCardId": json_body["userCardId"]}}
            self.next_listing += 1
            self.listings.append(listing)
            return HttpResponse(201, listing)
        if method == "PATCH" and path.startswith("/api/v1/marketplace/listings/"):
            listing = self._listing(int(path.rsplit("/", 1)[1]))
            listing["price"] = json_body["price"]
            return HttpResponse(200, listing)
        if method == "DELETE" and path.startswith("/api/v1/marketplace/listings/"):
            listing_id = int(path.rsplit("/", 1)[1])
            self.listings = [item for item in self.listings if item["listingId"] != listing_id]
            if self.cancel_sold_after_timeout:
                self.transactions[listing_id] = {"listingId": listing_id, "buyer": {"telegramId": 999}}
                raise TimeoutError("sale raced with cancel")
            return HttpResponse(204)
        if method == "POST" and path.endswith("/buy") and "/marketplace/listings/" in path:
            listing_id = int(path.split("/")[-2])
            self.transactions[listing_id] = {"listingId": listing_id, "buyer": {"telegramId": 777}}
            self.listings = [item for item in self.listings if item["listingId"] != listing_id]
            return HttpResponse(200, self.transactions[listing_id])
        if method == "POST" and path.endswith("/renew"):
            card = self._card(int(path.split("/")[-2]))
            card["usesRemaining"] += 5
            card["timesRenewed"] += 1
            return HttpResponse(200, card)
        if method == "POST" and path.endswith("/recycle"):
            card_id = int(path.split("/")[-2])
            self.cards = [card for card in self.cards if card["id"] != card_id]
            self.profile["fantiki"] += 25
            if self.recycle_timeout_after_commit:
                raise TimeoutError("recycle committed but acknowledgement lost")
            return HttpResponse(200, {"fantikiEarned": 25, "newBalance": self.profile["fantiki"]})
        if method == "POST" and path == "/api/v1/cards/merge/preview":
            return HttpResponse(200, {"previewId": 80})
        if method == "POST" and path == "/api/v1/cards/merge/confirm":
            inputs = set(json_body["inputUserCardIds"])
            self.cards = [card for card in self.cards if card["id"] not in inputs]
            result = self._new_card()
            self.cards.append(result)
            return HttpResponse(200, {"card": result})
        if method == "POST" and path == "/api/v1/legendary-upgrade":
            card = self._card(json_body["userCardId"])
            card["rarity"] = "LEGENDARY"
            card["perks"].append({"perkId": json_body["perkId"]})
            return HttpResponse(200, {"card": card})
        if method == "POST" and path == "/api/v1/achievements/ace/claim":
            self.achievement_state = "CLAIMED"
            return HttpResponse(200, {"claimed": True})
        if method == "POST" and path == "/api/v1/achievements/ace/choices/30/select":
            self.achievement_state = "CLAIMED"
            if self.choice_timeout_after_commit:
                raise TimeoutError("choice committed but acknowledgement lost")
            return HttpResponse(200, {"selected": True})
        if method == "PUT" and path == "/api/v1/periodic-ratings/rewards/20/draft":
            self.reward.update(status="DRAFT", version=json_body["version"] + 1, selection={key: value for key, value in json_body.items() if key != "version"})
            return HttpResponse(200, self.reward)
        if method == "POST" and path == "/api/v1/periodic-ratings/rewards/20/submit":
            self.reward.update(status="FULFILLED", issuedAt="2026-09-04T00:00:00Z", version=json_body["version"] + 1)
            return HttpResponse(200, self.reward)
        raise AssertionError(f"unexpected request: {method} {path}")

    def _get(self, path: str) -> HttpResponse:
        fixed = {
            "/api/v1/me": self.profile,
            "/api/v1/me/cards": self.cards,
            "/api/v1/me/fantasy-teams": [] if self.team is None else [self.team],
            "/api/v1/tournaments/series-open-for-team": [{"id": 12}],
            "/api/v1/series/12": {"id": 12},
            "/api/v1/series/12/leagues": [{"code": "MAIN"}],
            "/api/v1/store/packs": self.packs,
            "/api/v1/marketplace/listings": {"content": self.listings},
            "/api/v1/marketplace/analytics/summary": [{"fantasyPlayerId": 1}],
            "/api/v1/marketplace/analytics/detail": {"fantasyPlayerId": 1, "rarity": "EPIC"},
            "/api/v1/marketplace/my-listings": self.listings,
            "/api/v1/me/economy-info": {"renewalCost": 10},
            "/api/v1/card-value/info": {"enabled": True},
            "/api/v1/cards/merge/options": {"operations": []},
            "/api/v1/legendary-upgrade/info": {"cards": []},
            "/api/v1/achievements": {"categories": [{"achievements": [{"code": "ace", "state": self.achievement_state}]}]},
            "/api/v1/periodic-ratings/current": {"id": 15},
            "/api/v1/periodic-ratings/periods/15/me": {"rank": 1},
            "/api/v1/periodic-ratings/rewards": [self.reward],
            "/api/v1/periodic-ratings/rewards/20": self.reward,
            "/api/v1/periodic-ratings/rewards/20/players": {"content": []},
        }
        if path.startswith("/api/v1/me/fantasy-teams/"):
            return HttpResponse(404, {"message": "not found"}) if self.team is None else HttpResponse(200, self.team)
        if path.startswith("/api/v1/marketplace/transactions/"):
            listing_id = int(path.rsplit("/", 1)[1])
            return HttpResponse(404, {"message": "not found"}) if listing_id not in self.transactions else HttpResponse(200, self.transactions[listing_id])
        if path in fixed:
            return HttpResponse(200, copy.deepcopy(fixed[path]))
        raise AssertionError(f"unexpected GET {path}")

    def _new_card(self) -> dict[str, Any]:
        card = {"id": self.next_card, "rarity": "COMMON", "usesRemaining": 2, "timesRenewed": 0, "perks": []}
        self.next_card += 1
        return card

    def _card(self, card_id: int) -> dict[str, Any]:
        return next(card for card in self.cards if card["id"] == card_id)

    def _listing(self, listing_id: int) -> dict[str, Any]:
        return next(item for item in self.listings if item["listingId"] == listing_id)


def service_with_run(tmp_path: Path) -> tuple[FantasyService, FakeFantasyTransport, AuditStore]:
    store = AuditStore((tmp_path / "state.sqlite3").resolve())
    store.start_run(run_id="run", model="test", prompt_hash="p", tools_hash="t", config_hash="c")
    now = dt.datetime.now(dt.timezone.utc)
    snapshot_id = store.create_snapshot(
        run_id="run", kind="TEST", as_of=now, generated_at=now, source="test", payload={},
    )
    store.record_decision(
        run_id="run", decision_type="TEST", subject_type="TEST", subject_id="1",
        snapshot_ids=[snapshot_id], alternatives=[], choice={}, rationale="test",
    )
    transport = FakeFantasyTransport()
    client = FantasyHttpClient(
        "https://fantasy.example",
        "secret-token",
        transport=transport,
        timeout_seconds=5,
    )
    return FantasyService(client, store), transport, store


def test_all_read_route_families_are_typed_and_own_scope(tmp_path: Path) -> None:
    service, transport, store = service_with_run(tmp_path)
    transport.team = {"seriesId": 12, "leagueCode": "MAIN", "slots": [{"slot": 1, "userCardId": 1}]}
    try:
        calls = [
            lambda: service.get_my_profile(),
            lambda: service.get_my_cards(tournament_id=2, series_id=12, rarity="EPIC", perk_ids=["x"]),
            lambda: service.get_my_teams(),
            lambda: service.list_open_series(),
            lambda: service.get_series(12),
            lambda: service.list_series_leagues(12),
            lambda: service.get_my_team(12),
            lambda: service.list_store_packs(),
            lambda: service.list_marketplace(fantasy_player_id=1),
            lambda: service.get_marketplace_analytics(fantasy_player_ids=[1]),
            lambda: service.get_marketplace_analytics(fantasy_player_id=1, rarity="EPIC"),
            lambda: service.get_my_listings(),
            lambda: service.get_economy_info(),
            lambda: service.get_card_value_info(),
            lambda: service.get_merge_options(),
            lambda: service.get_legendary_upgrade_info(),
            lambda: service.get_achievement_catalog(),
            lambda: service.get_periodic_rating_current(),
            lambda: service.get_periodic_rating_me(15),
            lambda: service.get_periodic_rating_rewards(),
            lambda: service.get_periodic_rating_rewards(20),
            lambda: service.search_periodic_reward_players(20, query="player", page=0, size=10),
        ]
        for call in calls:
            assert call().observed_at
        paths = [call["path"] for call in transport.calls]
        assert "/api/v1/me/cards" in paths
        cards_call = next(call for call in transport.calls if call["path"] == "/api/v1/me/cards")
        assert cards_call["query"] == {"tournamentId": ["2"], "seriesId": ["12"], "rarity": ["EPIC"], "perkIds": ["x"]}
        assert not any("/users/" in path or "/admin/" in path or "/telegram/" in path for path in paths)
    finally:
        store.close()


def test_all_write_route_families_are_one_attempt_and_exactly_read_back(tmp_path: Path) -> None:
    service, transport, store = service_with_run(tmp_path)
    try:
        assert service.create_team(run_id="run", decision_id=1, operation_id="team-create", series_id=12, league_code="MAIN", user_card_ids=[1, 2]).outcome == "SUCCEEDED"
        assert service.update_team(run_id="run", decision_id=1, operation_id="team-update", series_id=12, league_code="MAIN", user_card_ids=[2, 1]).outcome == "SUCCEEDED"
        assert service.buy_pack(run_id="run", decision_id=1, operation_id="pack-buy", pack_id=9).outcome == "SUCCEEDED"
        pack_write = next(call for call in transport.calls if call["path"] == "/api/v1/store/packs/9/buy")
        assert pack_write["headers"]["Idempotency-Key"] == "pack-buy"

        transport.packs[0]["pendingChoice"] = {"id": 50, "requiredCount": 1}
        assert service.select_pack_choice(run_id="run", decision_id=1, operation_id="pack-choice", choice_id=50, option_id="a").outcome == "SUCCEEDED"
        assert service.create_marketplace_listing(run_id="run", decision_id=1, operation_id="listing-create", user_card_id=1, price=80).outcome == "SUCCEEDED"
        assert service.update_marketplace_listing_price(run_id="run", decision_id=1, operation_id="listing-price", listing_id=11, price=90).outcome == "SUCCEEDED"
        assert service.cancel_marketplace_listing(run_id="run", decision_id=1, operation_id="listing-cancel", listing_id=11).outcome == "SUCCEEDED"
        assert service.buy_marketplace_listing(run_id="run", decision_id=1, operation_id="listing-buy", listing_id=10).outcome == "SUCCEEDED"
        assert service.renew_card(run_id="run", decision_id=1, operation_id="renew", user_card_id=2).outcome == "SUCCEEDED"
        assert service.recycle_card(run_id="run", decision_id=1, operation_id="recycle", user_card_id=3).outcome == "SUCCEEDED"

        preview = service.merge_cards_preview(run_id="run", decision_id=1, operation_id="merge-preview", operation="RARITY_UPGRADE", input_user_card_ids=[4, 5])
        assert preview.outcome == "UNKNOWN"
        assert preview.verification["readBackCompleted"] is False
        repeated_preview = service.merge_cards_preview(run_id="run", decision_id=1, operation_id="merge-preview", operation="RARITY_UPGRADE", input_user_card_ids=[4, 5])
        assert repeated_preview.outcome == "UNKNOWN"
        assert repeated_preview.write_attempted is False
        assert service.merge_cards_confirm(run_id="run", decision_id=1, operation_id="merge-confirm", operation="RARITY_UPGRADE", input_user_card_ids=[4, 5], preview_id=80).outcome == "SUCCEEDED"
        assert service.legendary_upgrade(run_id="run", decision_id=1, operation_id="legendary", user_card_id=6, perk_id="ninja").outcome == "SUCCEEDED"
        assert service.claim_achievement(run_id="run", decision_id=1, operation_id="achievement", code="ace").outcome == "SUCCEEDED"
        transport.achievement_state = "COMPLETED"
        assert service.select_achievement_reward(run_id="run", decision_id=1, operation_id="achievement-choice", code="ace", reward_id=30, option_ids=["x"]).outcome == "SUCCEEDED"
        assert service.save_periodic_reward_draft(run_id="run", decision_id=1, operation_id="reward-draft", reward_id=20, player_id=1, perk_ids=["ninja"], skin_code="BASE", version=0).outcome == "SUCCEEDED"
        assert service.submit_periodic_reward(run_id="run", decision_id=1, operation_id="reward-submit", reward_id=20, version=1).outcome == "SUCCEEDED"

        writes = [call for call in transport.calls if call["method"] != "GET"]
        assert len(writes) == 17
        duplicate = service.buy_pack(run_id="run", decision_id=1, operation_id="pack-buy", pack_id=9)
        assert duplicate.outcome == "SUCCEEDED"
        assert duplicate.write_attempted is False
        assert len([call for call in transport.calls if call["path"] == "/api/v1/store/packs/9/buy"]) == 1
    finally:
        store.close()


@pytest.mark.parametrize("commit_mode,expected", [("before", "UNKNOWN"), ("after", "SUCCEEDED")])
def test_timeout_is_reconciled_and_duplicate_never_resends(tmp_path: Path, commit_mode: str, expected: str) -> None:
    service, transport, store = service_with_run(tmp_path)
    transport.team_timeout = commit_mode
    try:
        first = service.create_team(run_id="run", decision_id=1, operation_id="timeout-team", series_id=12, league_code="MAIN", user_card_ids=[1])
        assert first.outcome == expected
        duplicate = service.create_team(run_id="run", decision_id=1, operation_id="timeout-team", series_id=12, league_code="MAIN", user_card_ids=[1])
        assert duplicate.outcome == expected
        assert len([call for call in transport.calls if call["method"] == "POST" and call["path"].endswith("/fantasy-team")]) == 1
    finally:
        store.close()


def test_unknown_economic_write_blocks_following_economic_send(tmp_path: Path) -> None:
    service, transport, store = service_with_run(tmp_path)
    transport.pack_timeout_before_commit = True
    try:
        assert service.buy_pack(run_id="run", decision_id=1, operation_id="unknown-pack", pack_id=9).outcome == "UNKNOWN"
        with pytest.raises(FailClosedError, match="unknown-pack"):
            service.create_marketplace_listing(run_id="run", decision_id=1, operation_id="blocked-listing", user_card_id=1, price=80)
        assert not any(call["method"] == "POST" and call["path"] == "/api/v1/marketplace/listings" for call in transport.calls)
    finally:
        store.close()


def test_operation_id_payload_mismatch_is_rejected_without_resend(tmp_path: Path) -> None:
    service, transport, store = service_with_run(tmp_path)
    try:
        assert service.create_team(run_id="run", decision_id=1, operation_id="same-op", series_id=12, league_code="MAIN", user_card_ids=[1]).outcome == "SUCCEEDED"
        with pytest.raises(IntentConflictError):
            service.create_team(run_id="run", decision_id=1, operation_id="same-op", series_id=12, league_code="MAIN", user_card_ids=[2])
        assert len([call for call in transport.calls if call["method"] == "POST" and call["path"].endswith("/fantasy-team")]) == 1
    finally:
        store.close()


def test_registry_is_closed_typed_and_has_no_generic_or_foreign_tools(tmp_path: Path) -> None:
    service, _, store = service_with_run(tmp_path)
    try:
        assert not any(hasattr(service.client, name) for name in ("get", "post", "put", "patch", "delete", "request"))
        registry = build_tool_registry(service)
        names = registry.names()
        assert len(names) == 37
        assert len(names) == len(set(names))
        assert all(name.startswith("fantasy_") for name in names)
        forbidden = ("request", "http", "url", "admin", "telegram", "foreign", "other_user")
        assert not any(part in name for name in names for part in forbidden)
        descriptions = registry.list_tools()
        assert all(item["inputSchema"]["additionalProperties"] is False for item in descriptions)
        assert registry.call("fantasy_get_my_profile")["data"]["telegramId"] == 777
        with pytest.raises(KeyError):
            registry.call("fantasy_http_request", {"url": "https://example.test"})
    finally:
        store.close()


@pytest.mark.parametrize(
    "path",
    [
        "/api/v1/admin/users",
        "/api/v1/telegram/send",
        "/api/v1/series/12/users/777/fantasy-team",
        "/api/v1/not-allowlisted",
        "https://evil.example/api/v1/me",
    ],
)
def test_client_rejects_non_allowlisted_paths_before_transport(tmp_path: Path, path: str) -> None:
    _, transport, store = service_with_run(tmp_path)
    client = FantasyHttpClient("https://fantasy.example", "secret-token", transport=transport, timeout_seconds=5)
    try:
        with pytest.raises(ValueError):
            client._get(path)
        assert transport.calls == []
    finally:
        store.close()


def test_errors_are_bounded_and_never_echo_bearer(tmp_path: Path) -> None:
    class ErrorTransport:
        def request(self, **_: Any) -> HttpResponse:
            return HttpResponse(400, {"message": "x" * 2000})

    _, _, store = service_with_run(tmp_path)
    client = FantasyHttpClient("https://fantasy.example", "secret-token", transport=ErrorTransport(), timeout_seconds=5)
    try:
        with pytest.raises(Exception) as captured:
            client._get("/api/v1/me")
        assert len(str(captured.value)) <= 500
        assert "secret-token" not in str(captured.value)
    finally:
        store.close()


def test_success_payload_is_redacted_before_it_reaches_a_tool(tmp_path: Path) -> None:
    class SecretResponseTransport:
        def request(self, **_: Any) -> HttpResponse:
            return HttpResponse(200, {"displayName": "Agent", "accessToken": "must-not-leak"})

    _, _, store = service_with_run(tmp_path)
    client = FantasyHttpClient("https://fantasy.example", "secret-token", transport=SecretResponseTransport(), timeout_seconds=5)
    try:
        result = client._get("/api/v1/me")
        assert result == {"displayName": "Agent", "accessToken": "[REDACTED]"}
    finally:
        store.close()


@pytest.mark.parametrize("status", [408, 425, 499])
def test_ambiguous_proxy_status_reconciles_instead_of_failing(tmp_path: Path, status: int) -> None:
    service, transport, store = service_with_run(tmp_path)
    transport.team_http_status = status
    try:
        result = service.create_team(
            run_id="run", decision_id=1, operation_id=f"http-{status}",
            series_id=12, league_code="MAIN", user_card_ids=[1],
        )
        assert result.outcome == "UNKNOWN"
        assert result.write_attempted is True
    finally:
        store.close()


def test_cancel_that_was_actually_sold_is_not_reported_as_cancelled(tmp_path: Path) -> None:
    service, transport, store = service_with_run(tmp_path)
    transport.cancel_sold_after_timeout = True
    try:
        result = service.cancel_marketplace_listing(
            run_id="run", decision_id=1, operation_id="cancel-race", listing_id=10,
        )
        assert result.outcome == "UNKNOWN"
        assert result.verification["reason"] == "LISTING_WAS_SOLD"
    finally:
        store.close()


def test_recycle_absence_after_lost_acknowledgement_remains_unknown(tmp_path: Path) -> None:
    service, transport, store = service_with_run(tmp_path)
    transport.recycle_timeout_after_commit = True
    try:
        result = service.recycle_card(
            run_id="run", decision_id=1, operation_id="recycle-timeout", user_card_id=3,
        )
        assert result.outcome == "UNKNOWN"
        assert result.verification["reason"] == "RECYCLE_NOT_PROVED"
    finally:
        store.close()


def test_achievement_choice_transition_without_ack_remains_unknown(tmp_path: Path) -> None:
    service, transport, store = service_with_run(tmp_path)
    transport.choice_timeout_after_commit = True
    try:
        result = service.select_achievement_reward(
            run_id="run", decision_id=1, operation_id="choice-timeout",
            code="ace", reward_id=30, option_ids=["x"],
        )
        assert result.outcome == "UNKNOWN"
    finally:
        store.close()


@pytest.mark.parametrize(
    "tool,arguments,error",
    [
        ("fantasy_get_my_profile", {"extra": 1}, "unknown fields"),
        ("fantasy_get_series", {"series_id": 0}, "minimum"),
        (
            "fantasy_create_team",
            {"run_id": "run", "decision_id": 1, "operation_id": "x", "series_id": 12, "league_code": "MAIN", "user_card_ids": [1, 1]},
            "unique",
        ),
    ],
)
def test_registry_enforces_json_schema_before_dispatch(tmp_path: Path, tool: str, arguments: dict[str, Any], error: str) -> None:
    service, transport, store = service_with_run(tmp_path)
    registry = build_tool_registry(service)
    try:
        with pytest.raises(ValueError, match=error):
            registry.call(tool, arguments)
        assert transport.calls == []
    finally:
        store.close()


def test_real_mcp_server_concurrent_duplicate_write_uses_worker_threads_safely(tmp_path: Path) -> None:
    service, transport, store = service_with_run(tmp_path)

    class AllowWrites:
        def authorize_write(self, tool_name: str, arguments: Mapping[str, Any]) -> None:
            assert tool_name == "fantasy_create_team"
            assert arguments["decision_id"] == 1

    server = build_server(
        "fantasy",
        FantasyRegistryAdapter(build_tool_registry(service)),
        policy=ToolPolicy(write_enabled=True, authorizer=AllowWrites()),
    )
    arguments = {
        "run_id": "run",
        "decision_id": 1,
        "operation_id": "concurrent-team",
        "series_id": 12,
        "league_code": "MAIN",
        "user_card_ids": [1, 2],
    }

    async def invoke_twice() -> list[Any]:
        return await asyncio.gather(
            server.call_tool("fantasy_create_team", arguments),
            server.call_tool("fantasy_create_team", arguments),
        )

    try:
        results = asyncio.run(invoke_twice())
        assert len(results) == 2
        writes = [call for call in transport.calls if call["method"] == "POST" and call["path"].endswith("/fantasy-team")]
        assert len(writes) == 1
        assert store.get_intent("concurrent-team")["state"] in {"SUCCEEDED", "UNKNOWN"}
    finally:
        store.close()
