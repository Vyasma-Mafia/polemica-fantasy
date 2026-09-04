from __future__ import annotations

from collections.abc import Callable, Mapping, Sequence
from typing import Any

from polemica_agent.common.operations import IntentState, OperationCoordinator, ReadBackResolution
from polemica_agent.common.storage import AuditStore

from .client import FantasyApiError, FantasyHttpClient
from .types import OperationEnvelope, ReadEnvelope, observed_now


class FantasyService:
    """Closed, typed façade over the existing Fantasy user API."""

    def __init__(self, client: FantasyHttpClient, store: AuditStore) -> None:
        self.client = client
        self.store = store
        self.operations = OperationCoordinator(store)

    def _read(self, path: str, query: Mapping[str, Any] | None = None) -> ReadEnvelope:
        return ReadEnvelope(observed_now(), path, self.client._get(path, query))

    def get_my_profile(self) -> ReadEnvelope:
        return self._read("/api/v1/me")

    def get_my_cards(
        self,
        *,
        tournament_id: int | None = None,
        series_id: int | None = None,
        rarity: str | None = None,
        perk_ids: Sequence[str] | None = None,
    ) -> ReadEnvelope:
        return self._read(
            "/api/v1/me/cards",
            {"tournamentId": tournament_id, "seriesId": series_id, "rarity": rarity, "perkIds": perk_ids},
        )

    def get_my_teams(self) -> ReadEnvelope:
        return self._read("/api/v1/me/fantasy-teams")

    def list_open_series(self) -> ReadEnvelope:
        return self._read("/api/v1/tournaments/series-open-for-team")

    def get_series(self, series_id: int) -> ReadEnvelope:
        return self._read(f"/api/v1/series/{_positive_id(series_id, 'series_id')}")

    def list_series_leagues(self, series_id: int) -> ReadEnvelope:
        return self._read(f"/api/v1/series/{_positive_id(series_id, 'series_id')}/leagues")

    def get_my_team(self, series_id: int, league_code: str = "MAIN") -> ReadEnvelope:
        path = f"/api/v1/me/fantasy-teams/{_positive_id(series_id, 'series_id')}"
        return self._read(path, {"leagueCode": _league(league_code)})

    def list_store_packs(self) -> ReadEnvelope:
        return self._read("/api/v1/store/packs")

    def list_marketplace(
        self,
        *,
        fantasy_player_id: int | None = None,
        tournament_id: int | None = None,
        series_id: int | None = None,
        rarity: str | None = None,
        min_price: int | None = None,
        max_price: int | None = None,
        perk_ids: Sequence[str] | None = None,
        sort_by: str | None = None,
        page: int = 0,
        size: int = 20,
    ) -> ReadEnvelope:
        if page < 0 or not 1 <= size <= 100:
            raise ValueError("marketplace page must be >= 0 and size in [1, 100]")
        return self._read(
            "/api/v1/marketplace/listings",
            {
                "fantasyPlayerId": fantasy_player_id,
                "tournamentId": tournament_id,
                "seriesId": series_id,
                "rarity": rarity,
                "minPrice": min_price,
                "maxPrice": max_price,
                "perkIds": perk_ids,
                "sortBy": sort_by,
                "page": page,
                "size": size,
            },
        )

    def get_marketplace_analytics(
        self,
        *,
        fantasy_player_ids: Sequence[int] | None = None,
        fantasy_player_id: int | None = None,
        rarity: str | None = None,
    ) -> ReadEnvelope:
        if fantasy_player_ids is not None:
            if not fantasy_player_ids or len(fantasy_player_ids) > 100:
                raise ValueError("fantasy_player_ids must contain 1..100 ids")
            return self._read(
                "/api/v1/marketplace/analytics/summary",
                {"fantasyPlayerIds": list(fantasy_player_ids)},
            )
        if fantasy_player_id is None or rarity is None:
            raise ValueError("detail analytics requires fantasy_player_id and rarity")
        return self._read(
            "/api/v1/marketplace/analytics/detail",
            {"fantasyPlayerId": fantasy_player_id, "rarity": rarity},
        )

    def get_my_listings(self) -> ReadEnvelope:
        return self._read("/api/v1/marketplace/my-listings")

    def get_economy_info(self) -> ReadEnvelope:
        return self._read("/api/v1/me/economy-info")

    def get_card_value_info(self) -> ReadEnvelope:
        return self._read("/api/v1/card-value/info")

    def get_merge_options(self) -> ReadEnvelope:
        return self._read("/api/v1/cards/merge/options")

    def get_legendary_upgrade_info(self) -> ReadEnvelope:
        return self._read("/api/v1/legendary-upgrade/info")

    def get_achievement_catalog(self) -> ReadEnvelope:
        return self._read("/api/v1/achievements")

    def get_periodic_rating_current(self) -> ReadEnvelope:
        return self._read("/api/v1/periodic-ratings/current")

    def get_periodic_rating_me(self, period_id: int) -> ReadEnvelope:
        return self._read(f"/api/v1/periodic-ratings/periods/{_positive_id(period_id, 'period_id')}/me")

    def get_periodic_rating_rewards(self, reward_id: int | None = None) -> ReadEnvelope:
        path = "/api/v1/periodic-ratings/rewards"
        if reward_id is not None:
            path += f"/{_positive_id(reward_id, 'reward_id')}"
        return self._read(path)

    def search_periodic_reward_players(
        self,
        reward_id: int,
        *,
        query: str | None = None,
        page: int = 0,
        size: int = 20,
    ) -> ReadEnvelope:
        if page < 0 or not 1 <= size <= 100:
            raise ValueError("periodic reward player page must be >= 0 and size in [1, 100]")
        if query is not None and len(query) > 128:
            raise ValueError("query must contain at most 128 characters")
        return self._read(
            f"/api/v1/periodic-ratings/rewards/{_positive_id(reward_id, 'reward_id')}/players",
            {"q": query, "page": page, "size": size},
        )

    def create_team(self, *, run_id: str, decision_id: int, operation_id: str, series_id: int, league_code: str, user_card_ids: Sequence[int]) -> OperationEnvelope:
        return self._team_write("POST", run_id, decision_id, operation_id, series_id, league_code, user_card_ids)

    def update_team(self, *, run_id: str, decision_id: int, operation_id: str, series_id: int, league_code: str, user_card_ids: Sequence[int]) -> OperationEnvelope:
        return self._team_write("PUT", run_id, decision_id, operation_id, series_id, league_code, user_card_ids)

    def _team_write(self, method: str, run_id: str, decision_id: int, operation_id: str, series_id: int, league_code: str, user_card_ids: Sequence[int]) -> OperationEnvelope:
        sid, league = _positive_id(series_id, "series_id"), _league(league_code)
        cards = [_positive_id(card, "user_card_id") for card in user_card_ids]
        if not 1 <= len(cards) <= 3 or len(set(cards)) != len(cards):
            raise ValueError("user_card_ids must contain 1..3 unique ids")
        path = f"/api/v1/series/{sid}/leagues/{league}/fantasy-team"
        body = {"userCardIds": cards}
        send = (lambda: self.client._post(path, body)) if method == "POST" else (lambda: self.client._put(path, body))
        return self._execute(
            run_id, decision_id, operation_id, "TEAM_WRITE", f"{sid}:{league}", body, False, send,
            lambda: self._team_readback(sid, league, cards),
        )

    def buy_pack(self, *, run_id: str, decision_id: int, operation_id: str, pack_id: int) -> OperationEnvelope:
        pack_id = _positive_id(pack_id, "pack_id")
        before_packs = _list(self.list_store_packs().data)
        before_pack = _find(before_packs, "id", pack_id)
        before_cards = _list(self.get_my_cards().data)
        path = f"/api/v1/store/packs/{pack_id}/buy"
        return self._execute(
            run_id, decision_id, operation_id, "BUY_PACK", str(pack_id), {"packId": pack_id}, True,
            lambda: self.client._post(path, idempotency_key=operation_id),
            lambda: self._pack_readback(pack_id, before_pack, before_cards),
        )

    def select_pack_choice(self, *, run_id: str, decision_id: int, operation_id: str, choice_id: int, option_id: str) -> OperationEnvelope:
        cid, option = _positive_id(choice_id, "choice_id"), _nonempty(option_id, "option_id")
        before_cards = _list(self.get_my_cards().data)
        packs = _list(self.list_store_packs().data)
        containing_pack = next((p for p in packs if _mapping(p.get("pendingChoice")).get("id") == cid), None)
        body = {"optionId": option}
        return self._execute(
            run_id, decision_id, operation_id, "SELECT_PACK_CHOICE", str(cid), body, True,
            lambda: self.client._post(f"/api/v1/store/pack-choices/{cid}/select", body),
            lambda: self._choice_readback(cid, containing_pack, before_cards),
        )

    def create_marketplace_listing(self, *, run_id: str, decision_id: int, operation_id: str, user_card_id: int, price: int) -> OperationEnvelope:
        card, price = _positive_id(user_card_id, "user_card_id"), _positive_id(price, "price")
        body = {"userCardId": card, "price": price}
        return self._execute(
            run_id, decision_id, operation_id, "CREATE_LISTING", str(card), body, True,
            lambda: self.client._post("/api/v1/marketplace/listings", body),
            lambda: self._listing_readback(user_card_id=card, expected_price=price),
        )

    def update_marketplace_listing_price(self, *, run_id: str, decision_id: int, operation_id: str, listing_id: int, price: int) -> OperationEnvelope:
        listing, price = _positive_id(listing_id, "listing_id"), _positive_id(price, "price")
        body = {"price": price}
        return self._execute(
            run_id, decision_id, operation_id, "REPRICE_LISTING", str(listing), body, True,
            lambda: self.client._patch(f"/api/v1/marketplace/listings/{listing}", body),
            lambda: self._listing_readback(listing_id=listing, expected_price=price),
        )

    def cancel_marketplace_listing(self, *, run_id: str, decision_id: int, operation_id: str, listing_id: int) -> OperationEnvelope:
        listing = _positive_id(listing_id, "listing_id")
        before = _find(_list(self.get_my_listings().data), "listingId", listing)
        if before is None:
            raise ValueError("owned active listing must exist before cancellation")
        acknowledged = {"value": False}

        def send() -> Any:
            result = self.client._delete(f"/api/v1/marketplace/listings/{listing}")
            acknowledged["value"] = True
            return result

        return self._execute(
            run_id, decision_id, operation_id, "CANCEL_LISTING", str(listing), {"listingId": listing}, True,
            send,
            lambda: self._listing_absent_readback(listing, before, acknowledged["value"]),
        )

    def buy_marketplace_listing(self, *, run_id: str, decision_id: int, operation_id: str, listing_id: int) -> OperationEnvelope:
        listing = _positive_id(listing_id, "listing_id")
        profile = _mapping(self.get_my_profile().data)
        telegram_id = profile.get("telegramId")
        return self._execute(
            run_id, decision_id, operation_id, "BUY_LISTING", str(listing), {"listingId": listing}, True,
            lambda: self.client._post(f"/api/v1/marketplace/listings/{listing}/buy"),
            lambda: self._transaction_readback(listing, telegram_id),
        )

    def renew_card(self, *, run_id: str, decision_id: int, operation_id: str, user_card_id: int) -> OperationEnvelope:
        card = _positive_id(user_card_id, "user_card_id")
        before = _find(_list(self.get_my_cards().data), "id", card)
        if before is None:
            raise ValueError("owned card must exist before recycling")
        return self._execute(
            run_id, decision_id, operation_id, "RENEW_CARD", str(card), {"userCardId": card}, True,
            lambda: self.client._post(f"/api/v1/me/cards/{card}/renew"),
            lambda: self._card_change_readback(card, before, mode="renew"),
        )

    def recycle_card(self, *, run_id: str, decision_id: int, operation_id: str, user_card_id: int) -> OperationEnvelope:
        card = _positive_id(user_card_id, "user_card_id")
        before = _find(_list(self.get_my_cards().data), "id", card)
        before_profile = _mapping(self.get_my_profile().data)
        acknowledged: dict[str, Any] = {"response": None}

        def send() -> Any:
            result = self.client._post(f"/api/v1/me/cards/{card}/recycle")
            acknowledged["response"] = result
            return result

        return self._execute(
            run_id, decision_id, operation_id, "RECYCLE_CARD", str(card), {"userCardId": card}, True,
            send,
            lambda: self._recycle_readback(card, before, before_profile, acknowledged["response"]),
        )

    def merge_cards_preview(self, *, run_id: str, decision_id: int, operation_id: str, operation: str, input_user_card_ids: Sequence[int], selected_skin_source_user_card_id: int | None = None) -> OperationEnvelope:
        body = _merge_body(operation, input_user_card_ids, selected_skin_source_user_card_id)
        return self._execute(
            run_id, decision_id, operation_id, "MERGE_PREVIEW", operation, body, False,
            lambda: self.client._post("/api/v1/cards/merge/preview", body),
            lambda: ReadBackResolution(IntentState.UNKNOWN, {"reason": "NO_PREVIEW_READ_ENDPOINT"}, {"readBackCompleted": False}),
        )

    def merge_cards_confirm(self, *, run_id: str, decision_id: int, operation_id: str, operation: str, input_user_card_ids: Sequence[int], preview_id: int, selected_perk_ids: Sequence[str] = (), selected_skin_source_user_card_id: int | None = None) -> OperationEnvelope:
        inputs = [_positive_id(value, "input_user_card_id") for value in input_user_card_ids]
        before_ids = {card.get("id") for card in _list(self.get_my_cards().data)}
        body = _merge_body(operation, inputs, selected_skin_source_user_card_id)
        body.update({"previewId": _positive_id(preview_id, "preview_id"), "selectedPerkIds": list(selected_perk_ids)})
        return self._execute(
            run_id, decision_id, operation_id, "MERGE_CONFIRM", str(preview_id), body, True,
            lambda: self.client._post("/api/v1/cards/merge/confirm", body),
            lambda: self._merge_confirm_readback(inputs, before_ids),
        )

    def legendary_upgrade(self, *, run_id: str, decision_id: int, operation_id: str, user_card_id: int, perk_id: str) -> OperationEnvelope:
        card, perk = _positive_id(user_card_id, "user_card_id"), _nonempty(perk_id, "perk_id")
        body = {"userCardId": card, "perkId": perk}
        return self._execute(
            run_id, decision_id, operation_id, "LEGENDARY_UPGRADE", str(card), body, True,
            lambda: self.client._post("/api/v1/legendary-upgrade", body),
            lambda: self._legendary_readback(card, perk),
        )

    def claim_achievement(self, *, run_id: str, decision_id: int, operation_id: str, code: str) -> OperationEnvelope:
        code = _path_label(code, "code")
        before_state = self._achievement_state(code)
        return self._execute(
            run_id, decision_id, operation_id, "CLAIM_ACHIEVEMENT", code, {"code": code}, True,
            lambda: self.client._post(f"/api/v1/achievements/{code}/claim"),
            lambda: self._achievement_readback(code, before_state=before_state),
        )

    def select_achievement_reward(self, *, run_id: str, decision_id: int, operation_id: str, code: str, reward_id: int, option_ids: Sequence[str]) -> OperationEnvelope:
        code, reward = _path_label(code, "code"), _positive_id(reward_id, "reward_id")
        before_state = self._achievement_state(code)
        if before_state is None or before_state == "CLAIMED":
            raise ValueError("achievement must have an unresolved reward before selection")
        body = {"optionIds": [_nonempty(item, "option_id") for item in option_ids]}
        acknowledged = {"value": False}

        def send() -> Any:
            result = self.client._post(f"/api/v1/achievements/{code}/choices/{reward}/select", body)
            acknowledged["value"] = True
            return result

        return self._execute(
            run_id, decision_id, operation_id, "SELECT_ACHIEVEMENT_REWARD", f"{code}:{reward}", body, True,
            send,
            lambda: self._achievement_readback(
                code,
                before_state=before_state,
                acknowledgement_required=True,
                acknowledged=acknowledged["value"],
            ),
        )

    def save_periodic_reward_draft(self, *, run_id: str, decision_id: int, operation_id: str, reward_id: int, player_id: int, perk_ids: Sequence[str], skin_code: str, version: int) -> OperationEnvelope:
        reward = _positive_id(reward_id, "reward_id")
        body = {"playerId": _positive_id(player_id, "player_id"), "perkIds": list(perk_ids), "skinCode": _nonempty(skin_code, "skin_code"), "version": _nonnegative(version, "version")}
        return self._execute(
            run_id, decision_id, operation_id, "PERIODIC_REWARD_DRAFT", str(reward), body, False,
            lambda: self.client._put(f"/api/v1/periodic-ratings/rewards/{reward}/draft", body),
            lambda: self._periodic_draft_readback(reward, body),
        )

    def submit_periodic_reward(self, *, run_id: str, decision_id: int, operation_id: str, reward_id: int, version: int) -> OperationEnvelope:
        reward = _positive_id(reward_id, "reward_id")
        body = {"version": _nonnegative(version, "version")}
        return self._execute(
            run_id, decision_id, operation_id, "PERIODIC_REWARD_SUBMIT", str(reward), body, True,
            lambda: self.client._post(f"/api/v1/periodic-ratings/rewards/{reward}/submit", body),
            lambda: self._periodic_submit_readback(reward),
        )

    def _execute(self, run_id: str, decision_id: int, operation_id: str, kind: str, target: str, request: Any, economic: bool, send: Callable[[], Any], read_back: Callable[[], ReadBackResolution]) -> OperationEnvelope:
        result = self.operations.execute(
            operation_id=_operation_id(operation_id), run_id=_nonempty(run_id, "run_id"), kind=kind,
            target_id=target, request=request, is_economic=economic, send=send, read_back=read_back,
            decision_id=_positive_id(decision_id, "decision_id"),
        )
        return OperationEnvelope.from_result(result)

    def _team_readback(self, series_id: int, league: str, expected: list[int]) -> ReadBackResolution:
        team = self._get_or_none(f"/api/v1/me/fantasy-teams/{series_id}", {"leagueCode": league})
        actual = [slot.get("userCardId") for slot in _list(_mapping(team).get("slots"))]
        exact = actual == expected
        return _resolution(exact, {"team": team}, "EXACT_TEAM" if exact else "TEAM_MISMATCH")

    def _pack_readback(self, pack_id: int, before_pack: Mapping[str, Any] | None, before_cards: list[Mapping[str, Any]]) -> ReadBackResolution:
        current = _find(_list(self.list_store_packs().data), "id", pack_id)
        new_cards = _new_cards(before_cards, _list(self.get_my_cards().data))
        before_count = None if before_pack is None else before_pack.get("packOpensUsed")
        after_count = None if current is None else current.get("packOpensUsed")
        exact = isinstance(before_count, int) and after_count == before_count + 1
        return _resolution(exact, {"pack": current, "newCards": new_cards}, "PACK_OPEN_COUNT_ADVANCED" if exact else "PACK_RESULT_UNKNOWN")

    def _choice_readback(self, choice_id: int, containing_pack: Mapping[str, Any] | None, before_cards: list[Mapping[str, Any]]) -> ReadBackResolution:
        packs = _list(self.list_store_packs().data)
        still_pending = any(_mapping(pack.get("pendingChoice")).get("id") == choice_id for pack in packs)
        new_cards = _new_cards(before_cards, _list(self.get_my_cards().data))
        expected_count = _mapping(_mapping(containing_pack).get("pendingChoice")).get("requiredCount")
        exact = not still_pending and isinstance(expected_count, int) and len(new_cards) == expected_count
        return _resolution(exact, {"pending": still_pending, "newCards": new_cards}, "CHOICE_MATERIALIZED" if exact else "CHOICE_RESULT_UNKNOWN")

    def _listing_readback(self, *, expected_price: int, listing_id: int | None = None, user_card_id: int | None = None) -> ReadBackResolution:
        listings = _list(self.get_my_listings().data)
        match = next((item for item in listings if (listing_id is None or item.get("listingId") == listing_id) and (user_card_id is None or _mapping(item.get("card")).get("userCardId") == user_card_id)), None)
        exact = match is not None and match.get("price") == expected_price
        return _resolution(exact, {"listing": match}, "EXACT_LISTING" if exact else "LISTING_NOT_VERIFIED")

    def _listing_absent_readback(self, listing_id: int, before: Mapping[str, Any] | None, acknowledged: bool) -> ReadBackResolution:
        absent = _find(_list(self.get_my_listings().data), "listingId", listing_id) is None
        transaction = self._get_or_none(f"/api/v1/marketplace/transactions/{listing_id}") if absent else None
        exact = before is not None and absent and transaction is None and acknowledged
        reason = "CANCEL_ACKNOWLEDGED_AND_LISTING_ABSENT" if exact else "LISTING_CANCEL_NOT_PROVED"
        if transaction is not None:
            reason = "LISTING_WAS_SOLD"
        return _resolution(exact, {"listingId": listing_id, "absent": absent, "transaction": transaction}, reason)

    def _transaction_readback(self, listing_id: int, telegram_id: Any) -> ReadBackResolution:
        tx = self._get_or_none(f"/api/v1/marketplace/transactions/{listing_id}")
        exact = tx is not None and _mapping(_mapping(tx).get("buyer")).get("telegramId") == telegram_id
        return _resolution(exact, {"transaction": tx}, "BUYER_MATCH" if exact else "TRANSACTION_NOT_VERIFIED")

    def _card_change_readback(self, card_id: int, before: Mapping[str, Any] | None, *, mode: str) -> ReadBackResolution:
        current = _find(_list(self.get_my_cards().data), "id", card_id)
        exact = current is not None and before is not None and current.get("usesRemaining", -1) > before.get("usesRemaining", -1) and current.get("timesRenewed", -1) == before.get("timesRenewed", -1) + 1
        return _resolution(exact, {"card": current}, "CARD_STATE_MATCH" if exact else "CARD_STATE_UNKNOWN")

    def _recycle_readback(self, card_id: int, before: Mapping[str, Any] | None, before_profile: Mapping[str, Any], acknowledged: Any) -> ReadBackResolution:
        current = _find(_list(self.get_my_cards().data), "id", card_id)
        profile = _mapping(self.get_my_profile().data)
        response = _mapping(acknowledged)
        earned, new_balance = response.get("fantikiEarned"), response.get("newBalance")
        exact = (
            before is not None
            and current is None
            and isinstance(earned, int)
            and isinstance(new_balance, int)
            and profile.get("fantiki") == new_balance
            and isinstance(before_profile.get("fantiki"), int)
            and new_balance == before_profile["fantiki"] + earned
        )
        return _resolution(
            exact,
            {"card": current, "balance": profile.get("fantiki"), "acknowledged": bool(response)},
            "RECYCLE_ACK_AND_BALANCE_MATCH" if exact else "RECYCLE_NOT_PROVED",
        )

    def _merge_confirm_readback(self, inputs: list[int], before_ids: set[Any]) -> ReadBackResolution:
        cards = _list(self.get_my_cards().data)
        current_ids = {card.get("id") for card in cards}
        new_cards = [card for card in cards if card.get("id") not in before_ids]
        exact = all(card_id not in current_ids for card_id in inputs) and len(new_cards) == 1
        return _resolution(exact, {"newCards": new_cards, "inputsAbsent": all(card_id not in current_ids for card_id in inputs)}, "MERGE_MATERIALIZED" if exact else "MERGE_RESULT_UNKNOWN")

    def _legendary_readback(self, card_id: int, perk_id: str) -> ReadBackResolution:
        card = _find(_list(self.get_my_cards().data), "id", card_id)
        perks = {perk.get("perkId") for perk in _list(_mapping(card).get("perks"))}
        exact = card is not None and card.get("rarity") == "LEGENDARY" and perk_id in perks
        return _resolution(exact, {"card": card}, "LEGENDARY_CARD_MATCH" if exact else "UPGRADE_NOT_VERIFIED")

    def _achievement_state(self, code: str) -> Any:
        catalog = _mapping(self.get_achievement_catalog().data)
        achievement = next((item for category in _list(catalog.get("categories")) for item in _list(category.get("achievements")) if item.get("code") == code), None)
        return None if achievement is None else achievement.get("state")

    def _achievement_readback(
        self,
        code: str,
        *,
        before_state: Any = None,
        acknowledgement_required: bool = False,
        acknowledged: bool = False,
    ) -> ReadBackResolution:
        catalog = _mapping(self.get_achievement_catalog().data)
        achievement = next((item for category in _list(catalog.get("categories")) for item in _list(category.get("achievements")) if item.get("code") == code), None)
        claimed = achievement is not None and achievement.get("state") == "CLAIMED"
        exact = claimed and before_state != "CLAIMED" and (acknowledged or not acknowledgement_required)
        return _resolution(exact, {"achievement": achievement, "beforeState": before_state}, "ACHIEVEMENT_TRANSITIONED_TO_CLAIMED" if exact else "PENDING_CHOICE_OR_UNKNOWN")

    def _periodic_draft_readback(self, reward_id: int, expected: Mapping[str, Any]) -> ReadBackResolution:
        reward = _mapping(self.get_periodic_rating_rewards(reward_id).data)
        selection = _mapping(reward.get("selection"))
        exact = reward.get("status") == "DRAFT" and reward.get("version") == expected["version"] + 1 and selection.get("playerId") == expected["playerId"] and selection.get("perkIds", []) == expected["perkIds"] and selection.get("skinCode") == expected["skinCode"]
        return _resolution(exact, {"reward": reward}, "EXACT_REWARD_DRAFT" if exact else "REWARD_DRAFT_MISMATCH")

    def _periodic_submit_readback(self, reward_id: int) -> ReadBackResolution:
        reward = _mapping(self.get_periodic_rating_rewards(reward_id).data)
        exact = reward.get("status") == "FULFILLED" and reward.get("issuedAt") is not None
        return _resolution(exact, {"reward": reward}, "REWARD_FULFILLED" if exact else "REWARD_NOT_FULFILLED")

    def _get_or_none(self, path: str, query: Mapping[str, Any] | None = None) -> Any | None:
        try:
            return self.client._get(path, query)
        except Exception as exc:
            if getattr(exc, "code", None) == "HTTP_404":
                return None
            raise


def _resolution(exact: bool, result: Any, reason: str) -> ReadBackResolution:
    return ReadBackResolution(IntentState.SUCCEEDED if exact else IntentState.UNKNOWN, result, {"readBackCompleted": True, "matchesExpectedState": exact, "reason": reason})


def _mapping(value: Any) -> Mapping[str, Any]:
    return value if isinstance(value, Mapping) else {}


def _list(value: Any) -> list[Mapping[str, Any]]:
    return [item for item in value if isinstance(item, Mapping)] if isinstance(value, list) else []


def _find(items: list[Mapping[str, Any]], key: str, value: Any) -> Mapping[str, Any] | None:
    return next((item for item in items if item.get(key) == value), None)


def _new_cards(before: list[Mapping[str, Any]], after: list[Mapping[str, Any]]) -> list[Mapping[str, Any]]:
    before_ids = {card.get("id") for card in before}
    return [card for card in after if card.get("id") not in before_ids]


def _positive_id(value: int, name: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{name} must be a positive integer")
    return value


def _nonnegative(value: int, name: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError(f"{name} must be a non-negative integer")
    return value


def _nonempty(value: str, name: str) -> str:
    value = value.strip()
    if not value or len(value) > 128:
        raise ValueError(f"{name} must contain 1..128 characters")
    return value


def _path_label(value: str, name: str) -> str:
    value = _nonempty(value, name)
    if not all(char.isalnum() or char in "_-" for char in value):
        raise ValueError(f"{name} contains invalid path characters")
    return value


def _league(value: str) -> str:
    return _path_label(value.upper(), "league_code")


def _operation_id(value: str) -> str:
    value = _nonempty(value, "operation_id")
    if any(char.isspace() for char in value):
        raise ValueError("operation_id cannot contain whitespace")
    return value


def _merge_body(operation: str, input_ids: Sequence[int], skin_id: int | None) -> dict[str, Any]:
    ids = [_positive_id(value, "input_user_card_id") for value in input_ids]
    if not ids or len(set(ids)) != len(ids):
        raise ValueError("input_user_card_ids must be non-empty and unique")
    return {"operation": _nonempty(operation, "operation"), "inputUserCardIds": ids, "selectedSkinSourceUserCardId": None if skin_id is None else _positive_id(skin_id, "selected_skin_source_user_card_id")}
