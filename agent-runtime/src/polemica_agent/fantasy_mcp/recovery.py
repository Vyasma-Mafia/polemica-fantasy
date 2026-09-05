"""Read-only reconciliation for every registered Fantasy operation; never resends a write."""
from __future__ import annotations

from typing import Any

from polemica_agent.common.operations import IntentState, ReadBackResolution
from .service import _mapping, _list, _find, _resolution


def _unknown(reason: str) -> ReadBackResolution:
    return ReadBackResolution(IntentState.UNKNOWN, {"reason": reason}, {
        "readBackCompleted": False, "matchesExpectedState": False, "reason": reason,
        "retryWriteAllowed": False,
    })


def _card_signature(card: Any) -> tuple:
    card = _mapping(card)
    perks = tuple(sorted((p.get("perkId"), p.get("bonusPoints")) for p in _list(card.get("perks"))))
    return (card.get("fantasyPlayerId"), card.get("rarity"), card.get("skinCode"), perks)


def _receipt_cards_match(service: Any, receipt_cards: list, before_ids: set) -> bool:
    if not receipt_cards or any(type(c.get("id")) is not int for c in receipt_cards):
        return False
    ids = [c["id"] for c in receipt_cards]
    if len(set(ids)) != len(ids) or set(ids) & before_ids:
        return False
    current = {c.get("id"): c for c in _list(service.get_my_cards().data)}
    return all(c["id"] in current and _card_signature(c) == _card_signature(current[c["id"]]) for c in receipt_cards)


def read_back_operation(service: Any, operation_id: str) -> ReadBackResolution:
    intent = service.store.get_intent(operation_id)
    if intent is None:
        return _unknown("OPERATION_NOT_FOUND")
    kind, target = intent["kind"], str(intent["target_id"])
    request = _mapping(service.store.load_blob(intent["request_hash"], intent["request_path"]))
    evidence = service.store.get_operation_evidence(operation_id)
    context = _mapping(evidence.get("context"))
    receipt = _mapping(evidence.get("receipt"))
    acknowledged = evidence.get("receiptRecorded") is True
    if not acknowledged and intent["result_path"]:
        legacy = _mapping(service.store.load_blob(intent["result_hash"], intent["result_path"]))
        if "upstreamResponse" in legacy:
            receipt, acknowledged = _mapping(legacy["upstreamResponse"]), True

    if kind == "TEAM_WRITE":
        sid, league = target.split(":", 1)
        return service._team_readback(int(sid), league, list(request["userCardIds"]))
    if kind == "CLAIM_ACHIEVEMENT":
        state = _mapping(service.get_achievement_claim_state(target).data)
        if state.get("achievementCode") != target:
            return _unknown("ACHIEVEMENT_IDENTITY_MISMATCH")
        choices = _list(state.get("pendingChoices"))
        pending = bool(state.get("completedAt")) and bool(choices) and len({c.get("rewardId") for c in choices}) == len(choices) and all(
            type(c.get("rewardId")) is int and c["rewardId"] > 0 and type(c.get("requiredCount")) is int
            and 0 < c["requiredCount"] <= len(_list(c.get("options")))
            and len({o.get("optionId") for o in _list(c.get("options"))}) == len(c["options"])
            and all(isinstance(o.get("optionId"), str) and o["optionId"] for o in c["options"])
            for c in choices
        )
        claimed = state.get("claimedAt") is not None
        exact = claimed or pending
        return _resolution(exact, {"claimState": state, "nextAction": "SELECT_REWARD" if pending else None},
                           "ACHIEVEMENT_PENDING_SELECTION" if pending else "ACHIEVEMENT_CLAIMED" if claimed else "CLAIM_NOT_PROVED")
    if kind == "SELECT_ACHIEVEMENT_REWARD":
        code, reward_id = target.rsplit(":", 1)
        state = _mapping(service.get_achievement_claim_state(code).data)
        selected = _find(_list(state.get("selectedChoices")), "rewardId", int(reward_id))
        expected = request.get("optionIds", [])
        issued = _mapping(selected).get("selectedUserCardIds")
        exact = (state.get("achievementCode") == code and selected is not None
                 and selected.get("claimedAt") is not None and bool(expected)
                 and len(expected) == len(set(expected)) == selected.get("requiredCount")
                 and sorted(selected.get("selectedOptionIds", [])) == sorted(expected)
                 and isinstance(issued, list) and len(issued) == len(expected)
                 and all(type(card_id) is int and card_id > 0 for card_id in issued)
                 and len(set(issued)) == len(expected))
        return _resolution(exact, {"claimState": state, "selection": selected},
                           "EXACT_ACHIEVEMENT_SELECTION" if exact else "ACHIEVEMENT_SELECTION_NOT_PROVED")
    if kind == "BUY_PACK":
        if context.get("beforePack") is None:
            return _unknown("PACK_BASELINE_UNAVAILABLE")
        return service._pack_readback(int(target), context["beforePack"], _list(context.get("beforeCards")))
    if kind == "SELECT_PACK_CHOICE":
        selected = _mapping(context.get("selectedOption"))
        cards = _list(receipt.get("cards"))
        current_packs = _list(service.list_store_packs().data)
        pending = any(_mapping(p.get("pendingChoice")).get("id") == int(target) for p in current_packs)
        wanted = _list(selected.get("cards"))
        exact = (acknowledged and not pending and selected.get("optionId") == request.get("optionId")
                 and bool(wanted) and len(cards) == len(wanted)
                 and sorted(map(_card_signature, cards), key=repr) == sorted(map(_card_signature, wanted), key=repr)
                 and _receipt_cards_match(service, cards, {c.get("id") for c in _list(context.get("beforeCards"))}))
        return _resolution(exact, {"cards": cards, "pending": pending}, "EXACT_PACK_SELECTION" if exact else "PACK_SELECTION_RECEIPT_REQUIRED")
    if kind == "CREATE_LISTING":
        return service._listing_readback(user_card_id=int(target), expected_price=request["price"])
    if kind == "REPRICE_LISTING":
        return service._listing_readback(listing_id=int(target), expected_price=request["price"])
    if kind == "CANCEL_LISTING":
        if not context.get("before"):
            return _unknown("CANCEL_BASELINE_UNAVAILABLE")
        return service._listing_absent_readback(int(target), context["before"], acknowledged)
    if kind == "BUY_LISTING":
        # The authenticated identity is stable and can be safely re-read after restart.
        telegram_id = _mapping(service.get_my_profile().data).get("telegramId")
        if telegram_id is None:
            return _unknown("BUYER_IDENTITY_UNAVAILABLE")
        return service._transaction_readback(int(target), telegram_id)
    if kind == "RENEW_CARD":
        if not context.get("before"):
            return _unknown("RENEW_BASELINE_UNAVAILABLE")
        return service._card_change_readback(int(target), context["before"], mode="renew")
    if kind == "RECYCLE_CARD":
        if not context.get("before") or not acknowledged:
            return _unknown("RECYCLE_RECEIPT_OR_BASELINE_UNAVAILABLE")
        return service._recycle_readback(int(target), context["before"], _mapping(context.get("beforeProfile")), receipt)
    if kind == "MERGE_PREVIEW":
        # Preview only prepares a server-side roll, with no spend/consumption. Its acknowledged
        # typed receipt is the result; fresh materials establish it refers to owned input cards.
        ids = request.get("inputUserCardIds", [])
        current = {c.get("id") for c in _list(service.get_my_cards().data)}
        exact = (acknowledged and type(receipt.get("previewId")) is int and receipt["previewId"] > 0
                 and receipt.get("operation") == request.get("operation") and bool(receipt.get("expiresAt"))
                 and bool(_mapping(receipt.get("result"))) and bool(ids) and set(ids).issubset(current))
        return _resolution(exact, {"preview": receipt}, "PREVIEW_ACKNOWLEDGED_MATERIALS_PRESENT" if exact else "PREVIEW_RECEIPT_REQUIRED")
    if kind == "MERGE_CONFIRM":
        card = _mapping(receipt.get("card"))
        ids = request.get("inputUserCardIds", [])
        current = {c.get("id") for c in _list(service.get_my_cards().data)}
        exact = (acknowledged and "beforeIds" in context and bool(ids) and not set(ids) & current
                 and _receipt_cards_match(service, [card], set(context.get("beforeIds", []))))
        return _resolution(exact, {"card": card, "inputsAbsent": not set(ids) & current},
                           "MERGE_RECEIPT_AND_CARD_MATCH" if exact else "MERGE_RECEIPT_REQUIRED")
    if kind == "LEGENDARY_UPGRADE":
        return service._legendary_readback(int(target), request["perkId"])
    if kind == "PERIODIC_REWARD_DRAFT":
        return service._periodic_draft_readback(int(target), request)
    if kind == "PERIODIC_REWARD_SUBMIT":
        return service._periodic_submit_readback(int(target))
    return _unknown("UNSUPPORTED_OPERATION_KIND")
