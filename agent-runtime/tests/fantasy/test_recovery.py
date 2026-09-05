from pathlib import Path

import pytest

from polemica_agent.common.storage import AuditStore, IntentConflictError
from polemica_agent.fantasy_mcp import FantasyService
from test_fantasy_mcp import service_with_run


OPERATIONS = [
    ("create_team", {"series_id": 12, "league_code": "MAIN", "user_card_ids": [1]}),
    ("update_team", {"series_id": 12, "league_code": "MAIN", "user_card_ids": [1]}),
    ("buy_pack", {"pack_id": 9}),
    ("select_pack_choice", {"choice_id": 50, "option_id": "a"}),
    ("create_marketplace_listing", {"user_card_id": 1, "price": 80}),
    ("update_marketplace_listing_price", {"listing_id": 10, "price": 80}),
    ("cancel_marketplace_listing", {"listing_id": 10}),
    ("buy_marketplace_listing", {"listing_id": 10}),
    ("renew_card", {"user_card_id": 2}),
    ("recycle_card", {"user_card_id": 3}),
    ("merge_cards_preview", {"operation": "RARITY_UPGRADE", "input_user_card_ids": [4, 5]}),
    ("merge_cards_confirm", {"operation": "RARITY_UPGRADE", "input_user_card_ids": [4, 5], "preview_id": 80}),
    ("legendary_upgrade", {"user_card_id": 6, "perk_id": "ninja"}),
    ("claim_achievement", {"code": "ace"}),
    ("select_achievement_reward", {"code": "ace", "reward_id": 30, "option_ids": ["x"]}),
    ("save_periodic_reward_draft", {"reward_id": 20, "player_id": 1, "perk_ids": ["ninja"], "skin_code": "BASE", "version": 0}),
    ("submit_periodic_reward", {"reward_id": 20, "version": 0}),
]


def invoke(service, method, params):
    return getattr(service, method)(run_id="run", decision_id=1, operation_id="recover", **params)


def fail_readback(*args):
    raise TimeoutError("readback unavailable")


@pytest.mark.parametrize("method,params", OPERATIONS)
def test_every_write_recovers_after_process_restart_without_resend(tmp_path: Path, method, params, monkeypatch):
    service, transport, store = service_with_run(tmp_path)
    if method == "select_pack_choice":
        transport.packs[0]["pendingChoice"] = transport.pack_choice()
    monkeypatch.setattr(service, "_operation_readback", fail_readback)
    first = invoke(service, method, params)
    assert first.outcome == "UNKNOWN"
    assert first.write_attempted is True
    writes = len([c for c in transport.calls if c["method"] != "GET"])
    assert store.get_operation_evidence("recover")["receiptRecorded"] is True
    store.close()
    with AuditStore((tmp_path / "state.sqlite3").resolve()) as restarted:
        recovered_service = FantasyService(service.client, restarted)
        recovered = recovered_service.reconcile_operation(operation_id="recover")
        assert recovered.outcome == "SUCCEEDED", recovered
        assert recovered.write_attempted is False
        repeated = recovered_service.reconcile_operation(operation_id="recover")
        assert repeated.outcome == "SUCCEEDED"
        assert len([c for c in transport.calls if c["method"] != "GET"]) == writes == 1


def test_pending_claim_success_is_not_final_reward_completion(tmp_path: Path):
    service, transport, store = service_with_run(tmp_path)
    try:
        result = invoke(service, "claim_achievement", {"code": "ace"})
        assert result.outcome == "SUCCEEDED"
        assert result.verification["reason"] == "ACHIEVEMENT_PENDING_SELECTION"
        assert transport.achievement_state == "COMPLETED_UNCLAIMED"
        assert transport.selected_choices == []
        assert store.unresolved_intents() == []
    finally:
        store.close()


def test_one_selected_reward_succeeds_while_other_choices_remain(tmp_path: Path):
    service, transport, store = service_with_run(tmp_path)
    transport.pending_choices.append(transport.achievement_choice(31))
    try:
        result = invoke(service, "select_achievement_reward", {"code": "ace", "reward_id": 30, "option_ids": ["x"]})
        assert result.outcome == "SUCCEEDED"
        assert transport.achievement_state == "COMPLETED_UNCLAIMED"
        assert [c["rewardId"] for c in transport.pending_choices] == [31]
    finally:
        store.close()


def test_different_selected_option_does_not_prove_requested_selection(tmp_path: Path, monkeypatch):
    service, transport, store = service_with_run(tmp_path)
    original = service._operation_readback
    monkeypatch.setattr(service, "_operation_readback", fail_readback)
    try:
        invoke(service, "select_achievement_reward", {"code": "ace", "reward_id": 30, "option_ids": ["x"]})
        transport.selected_choices[0]["selectedOptionIds"] = ["y"]
        monkeypatch.setattr(service, "_operation_readback", original)
        assert service.reconcile_operation(operation_id="recover").outcome == "UNKNOWN"
        assert len([c for c in transport.calls if c["method"] != "GET"]) == 1
    finally:
        store.close()


@pytest.mark.parametrize("method,params", [
    ("recycle_card", {"user_card_id": 3}),
    ("merge_cards_confirm", {"operation": "RARITY_UPGRADE", "input_user_card_ids": [4, 5], "preview_id": 80}),
    ("select_pack_choice", {"choice_id": 50, "option_id": "a"}),
    ("cancel_marketplace_listing", {"listing_id": 10}),
])
def test_lost_ack_destructive_operations_not_inferred_from_absence(tmp_path: Path, method, params, monkeypatch):
    service, transport, store = service_with_run(tmp_path)
    if method == "select_pack_choice":
        transport.packs[0]["pendingChoice"] = transport.pack_choice()
    request = transport.request
    def lose_ack(**kwargs):
        response = request(**kwargs)
        if kwargs["method"] != "GET":
            raise TimeoutError("commit acknowledgement lost")
        return response
    monkeypatch.setattr(transport, "request", lose_ack)
    try:
        assert invoke(service, method, params).outcome == "UNKNOWN"
        assert service.reconcile_operation(operation_id="recover").outcome == "UNKNOWN"
        assert store.get_operation_evidence("recover")["receiptRecorded"] is False
        assert len([c for c in transport.calls if c["method"] != "GET"]) == 1
    finally:
        store.close()


def test_pack_selection_wrong_materialized_card_stays_unknown(tmp_path: Path, monkeypatch):
    service, transport, store = service_with_run(tmp_path)
    transport.packs[0]["pendingChoice"] = transport.pack_choice()
    transport.packs[0]["pendingChoice"]["options"][0]["cards"][0]["rarity"] = "EPIC"
    try:
        result = invoke(service, "select_pack_choice", {"choice_id": 50, "option_id": "a"})
        assert result.outcome == "UNKNOWN"  # fake issued COMMON, not promised EPIC
    finally:
        store.close()


def test_pending_pack_rejects_new_open_before_writing(tmp_path: Path):
    service, transport, store = service_with_run(tmp_path)
    transport.packs[0]["pendingChoice"] = transport.pack_choice()
    try:
        with pytest.raises(ValueError, match="pending choice"):
            invoke(service, "buy_pack", {"pack_id": 9})
        assert not [c for c in transport.calls if c["method"] != "GET"]
        assert store.get_intent("recover") is None
    finally:
        store.close()


@pytest.mark.parametrize("method,params", [
    ("select_pack_choice", {"choice_id": 50, "option_id": "a"}),
    ("select_achievement_reward", {"code": "ace", "reward_id": 30, "option_ids": ["x"]}),
    ("cancel_marketplace_listing", {"listing_id": 10}),
    ("recycle_card", {"user_card_id": 3}),
])
def test_same_operation_replay_ignores_consumed_preconditions(tmp_path: Path, method, params):
    service, transport, store = service_with_run(tmp_path)
    if method == "select_pack_choice":
        transport.packs[0]["pendingChoice"] = transport.pack_choice()
    try:
        assert invoke(service, method, params).outcome == "SUCCEEDED"
        replay = invoke(service, method, params)
        assert replay.outcome == "SUCCEEDED"
        assert replay.write_attempted is False
        assert len([c for c in transport.calls if c["method"] != "GET"]) == 1
    finally:
        store.close()


def test_same_operation_changed_options_conflicts_before_preconditions(tmp_path: Path):
    service, transport, store = service_with_run(tmp_path)
    try:
        params = {"code": "ace", "reward_id": 30, "option_ids": ["x"]}
        assert invoke(service, "select_achievement_reward", params).outcome == "SUCCEEDED"
        with pytest.raises(IntentConflictError):
            invoke(service, "select_achievement_reward", {**params, "option_ids": ["y"]})
        assert len([c for c in transport.calls if c["method"] != "GET"]) == 1
    finally:
        store.close()
