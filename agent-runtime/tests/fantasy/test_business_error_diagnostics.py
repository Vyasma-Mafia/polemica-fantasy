from __future__ import annotations

import datetime as dt

import pytest

from polemica_agent.common.operations import (
    DeterministicUpstreamError, IntentState, OperationCoordinator, ReadBackResolution, _safe_exception,
)
from polemica_agent.common.storage import AuditStore
from polemica_agent.fantasy_mcp.client import FantasyApiError, FantasyHttpClient, HttpResponse


class ErrorTransport:
    def __init__(self, message, status=400):
        self.message, self.status, self.calls = message, status, 0

    def request(self, **kwargs):
        self.calls += 1
        return HttpResponse(self.status, {"message": self.message})


def client_error(message, status=400, path="/api/v1/series/274/leagues/BUDGET/fantasy-team"):
    client = FantasyHttpClient("https://fantasy.example", "credential", transport=ErrorTransport(message, status))
    with pytest.raises((DeterministicUpstreamError, FantasyApiError)) as caught:
        client._post(path, {})
    return caught.value


@pytest.mark.parametrize("message,code,details", [
    ("Card 152609 has only 2 uses and is already reserved in 2 active league(s)", "CARD_USES_RESERVED", {"userCardId": 152609, "usesRemaining": 2, "reservedLeagueCount": 2}),
    ("Card 152609 has no remaining uses", "CARD_USES_EXHAUSTED", {"userCardId": 152609}),
    ("Team value 200 exceeds league cap 175", "TEAM_VALUE_CAP_EXCEEDED", {"teamValue": 200, "valueCap": 175}),
    ("Team size must be between 1 and 3 cards", "TEAM_SIZE_INVALID", {"minTeamSize": 1, "maxTeamSize": 3}),
    ("Maximum 1 LEGENDARY card(s) allowed per fantasy team", "TEAM_LEGENDARY_LIMIT_EXCEEDED", {"maxLegendary": 1}),
    ("Unknown user card id 123", "CARD_NOT_FOUND", {"userCardId": 123}),
    ("Card 123 is for a player who is not in this series roster", "CARD_NOT_IN_ROSTER", {"userCardId": 123}),
    ("Team submission deadline has passed", "TEAM_DEADLINE_PASSED", {}),
    ("Cannot use a card that is listed on the marketplace", "CARD_LISTED_ON_MARKETPLACE", {}),
])
def test_team_rejections_have_safe_actionable_diagnostics(message, code, details):
    diagnostic = _safe_exception(client_error(message))
    assert diagnostic["errorCode"] == "HTTP_400"
    assert diagnostic["businessErrorCode"] == code
    assert diagnostic["details"] == details
    assert diagnostic["businessErrorMessage"]


@pytest.mark.parametrize("message", [
    "Bearer super-secret", "Team submission deadline has passed\nBearer secret",
    "Card secret has no remaining uses", "Card -1 has no remaining uses",
    "Card 9999999999999999999 has no remaining uses",
    "Card 123 has only 2 uses and is already reserved in 2 active league(s) trailing secret",
])
def test_unknown_or_contaminated_messages_are_not_forwarded(message):
    assert _safe_exception(client_error(message)) == {
        "errorType": "DeterministicUpstreamError", "errorCode": "HTTP_400",
    }


@pytest.mark.parametrize("status", [408, 425, 499, 503])
def test_ambiguous_statuses_stay_ambiguous_without_business_code(status):
    error = client_error("Team submission deadline has passed", status)
    assert isinstance(error, FantasyApiError)
    assert error.uncertain
    assert "businessErrorCode" not in _safe_exception(error)


def test_mapping_is_scoped_to_team_endpoint():
    diagnostic = _safe_exception(client_error("Team submission deadline has passed", path="/api/v1/store/packs/1/buy"))
    assert "businessErrorCode" not in diagnostic


def test_known_rejection_persists_diagnostics_and_never_resends(tmp_path):
    store = AuditStore((tmp_path / "audit.sqlite3").resolve())
    try:
        store.start_run(run_id="run", model="test", prompt_hash="p", tools_hash="t", config_hash="c")
        now = dt.datetime.now(dt.timezone.utc)
        snapshot = store.create_snapshot(run_id="run", kind="TEST", as_of=now, generated_at=now, source="test", payload={})
        decision = store.record_decision(run_id="run", decision_type="TEST", subject_type="TEST", subject_id="274", snapshot_ids=[snapshot], alternatives=[], choice={}, rationale="test")
        transport = ErrorTransport("Card 152609 has only 2 uses and is already reserved in 2 active league(s)")
        client = FantasyHttpClient("https://fantasy.example", "credential", transport=transport)
        coordinator = OperationCoordinator(store)
        args = dict(operation_id="team-274", run_id="run", kind="TEAM_WRITE", target_id="274:BUDGET", request={}, is_economic=False, decision_id=decision,
                    send=lambda: client._post("/api/v1/series/274/leagues/BUDGET/fantasy-team", {}),
                    read_back=lambda: ReadBackResolution(IntentState.FAILED, {"team": None}, {"readBackCompleted": True}))
        first = coordinator.execute(**args)
        second = coordinator.execute(**args)
        assert first.state is second.state is IntentState.FAILED
        assert first.result["upstreamError"]["businessErrorCode"] == "CARD_USES_RESERVED"
        assert second.result == first.result
        assert transport.calls == 1
    finally:
        store.close()
