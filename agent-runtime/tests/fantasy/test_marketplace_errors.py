from __future__ import annotations

import datetime as dt

import pytest

from polemica_agent.common.operations import (
    DeterministicUpstreamError, IntentState, OperationCoordinator, ReadBackResolution, _safe_exception,
)
from polemica_agent.common.storage import AuditStore
from polemica_agent.fantasy_mcp.client import FantasyApiError, FantasyHttpClient, HttpResponse


class ErrorTransport:
    def __init__(self, message="Cannot sell an expired card", status=400, *, timeout=False):
        self.message, self.status, self.timeout, self.calls = message, status, timeout, 0

    def request(self, **kwargs):
        self.calls += 1
        if self.timeout:
            raise FantasyApiError(None, "TRANSPORT_ERROR", "timeout", uncertain=True)
        return HttpResponse(self.status, {"message": self.message})


def client_error(message, status=400, method="POST", path="/api/v1/marketplace/listings"):
    client = FantasyHttpClient("https://fantasy.example", "credential", transport=ErrorTransport(message, status))
    with pytest.raises((DeterministicUpstreamError, FantasyApiError)) as caught:
        client._request(method, path, body={})
    return caught.value


@pytest.mark.parametrize("message,code,status", [
    ("Card not found or not owned", "CARD_NOT_OWNED", 400),
    ("Cannot sell an expired card", "CARD_USES_EXHAUSTED", 400),
    ("Cannot sell a card in an active team", "CARD_IN_ACTIVE_TEAM", 400),
    ("Card is already listed", "CARD_LISTED_ON_MARKETPLACE", 400),
    ("Maximum contract reissues reached for this card", "CARD_MAX_RENEWALS_REACHED", 400),
    ("Marketplace min price exceeds max for this rarity (economy config)", "MARKETPLACE_ECONOMY_CONFIG_INVALID", 400),
    ("Price below minimum for this rarity", "MARKETPLACE_PRICE_BELOW_MINIMUM", 400),
    ("Price above maximum for this rarity", "MARKETPLACE_PRICE_ABOVE_MAXIMUM", 400),
    ("Your marketplace access is suspended", "MARKETPLACE_ACCESS_SUSPENDED", 403),
])
def test_known_listing_rejections_have_safe_business_diagnostics(message, code, status):
    diagnostic = _safe_exception(client_error(message, status))
    assert diagnostic["errorCode"] == f"HTTP_{status}"
    assert diagnostic["businessErrorCode"] == code
    assert diagnostic["businessErrorMessage"]
    assert diagnostic["details"] == {}


@pytest.mark.parametrize("message", [
    "Bearer super-secret", "Cannot sell an expired card\nBearer secret",
    "Cannot sell an expired card trailing secret",
    "Your marketplace access is suspended until 2026-10-01T00:00:00Z",
    "Your marketplace access is suspended: arbitrary private reason",
])
def test_unknown_and_contaminated_messages_are_not_forwarded(message):
    assert _safe_exception(client_error(message)) == {
        "errorType": "DeterministicUpstreamError", "errorCode": "HTTP_400",
    }


@pytest.mark.parametrize("method,path", [
    ("GET", "/api/v1/marketplace/listings"),
    ("PUT", "/api/v1/marketplace/listings"),
    ("POST", "/api/v1/marketplace/listings/1/buy"),
    ("POST", "/api/v1/marketplace/listings/"),
    ("POST", "/api/v1/series/1/leagues/MAIN/fantasy-team"),
])
def test_mapping_is_scoped_to_exact_listing_creation(method, path):
    assert "businessErrorCode" not in _safe_exception(client_error("Cannot sell an expired card", method=method, path=path))


@pytest.mark.parametrize("status", [408, 425, 499, 500, 503])
def test_ambiguous_listing_response_stays_uncertain(status):
    error = client_error("Cannot sell an expired card", status)
    assert isinstance(error, FantasyApiError)
    assert error.uncertain
    assert "businessErrorCode" not in _safe_exception(error)


@pytest.mark.parametrize("status,timeout,expected", [
    (400, False, IntentState.FAILED),
    (503, False, IntentState.UNKNOWN),
    (408, False, IntentState.UNKNOWN),
    (400, True, IntentState.UNKNOWN),
])
def test_listing_error_pipeline_preserves_terminal_and_uncertain_states(tmp_path, status, timeout, expected):
    store = AuditStore((tmp_path / "audit.sqlite3").resolve())
    try:
        store.start_run(run_id="run", model="test", prompt_hash="p", tools_hash="t", config_hash="c")
        now = dt.datetime.now(dt.timezone.utc)
        snapshot = store.create_snapshot(run_id="run", kind="TEST", as_of=now, generated_at=now, source="test", payload={})
        decision = store.record_decision(run_id="run", decision_type="TEST", subject_type="TEST", subject_id="123", snapshot_ids=[snapshot], alternatives=[], choice={}, rationale="test")
        transport = ErrorTransport(status=status, timeout=timeout)
        client = FantasyHttpClient("https://fantasy.example", "credential", transport=transport)
        coordinator = OperationCoordinator(store)
        readbacks = []

        def read_back():
            readbacks.append(True)
            # No trustworthy read-back observation can establish success or
            # failure. Only a deterministic rejection may terminate here.
            return ReadBackResolution(IntentState.UNKNOWN, {"listing": None}, {"readBackCompleted": False})

        args = dict(operation_id="listing-123", run_id="run", kind="MARKETPLACE_LIST", target_id="123", request={}, is_economic=True, decision_id=decision,
                    send=lambda: client._post("/api/v1/marketplace/listings", {}), read_back=read_back)
        first = coordinator.execute(**args)
        second = coordinator.execute(**args)
        assert first.state is second.state is expected
        assert transport.calls == 1
        if expected is IntentState.FAILED:
            assert first.result["upstreamError"]["businessErrorCode"] == "CARD_USES_EXHAUSTED"
            assert second.result == first.result
            assert len(readbacks) == 1
        else:
            assert "businessErrorCode" not in first.result
            assert len(readbacks) == 2
    finally:
        store.close()
