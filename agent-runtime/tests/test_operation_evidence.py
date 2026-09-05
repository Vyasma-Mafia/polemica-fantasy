from pathlib import Path

import pytest

from polemica_agent.common.operations import DeterministicUpstreamError, IntentState, OperationCoordinator, ReadBackResolution
from polemica_agent.common.storage import AuditStore, FailClosedError
from test_operations import store_with_run


def arguments(send, read_back):
    return dict(operation_id="evidence", run_id="run", decision_id=1, kind="TEST",
                target_id="one", request={}, is_economic=True, send=send, read_back=read_back,
                recovery_context={"beforeCards": [1], "beforeBalance": 100})


@pytest.mark.parametrize("receipt", [None, {"choiceId": 42}])
def test_evidence_survives_restart_and_failed_reconciliation(tmp_path: Path, receipt) -> None:
    sends = []
    def send():
        sends.append(1)
        return receipt
    def broken():
        raise RuntimeError("private upstream content")
    with store_with_run(tmp_path) as store:
        first = OperationCoordinator(store).execute(**arguments(send, broken))
        assert first.state is IntentState.UNKNOWN
        assert "private upstream content" not in str(first.result)
    with AuditStore((tmp_path / "agent.sqlite3").resolve()) as store:
        evidence = store.get_operation_evidence("evidence")
        assert evidence == {"context": {"beforeCards": [1], "beforeBalance": 100},
                            "receipt": receipt, "receiptRecorded": True}
        coordinator = OperationCoordinator(store)
        for _ in range(2):
            result = coordinator.reconcile("evidence", broken)
            assert result.state is IntentState.UNKNOWN
            assert result.result["upstreamResponse"] == receipt
        changed = arguments(send, lambda: ReadBackResolution(IntentState.SUCCEEDED, {"verified": True}, {}))
        changed["recovery_context"] = {"beforeBalance": 999}
        result = coordinator.execute(**changed)
        assert result.state is IntentState.SUCCEEDED
        assert result.write_attempted is False
        assert store.get_operation_evidence("evidence") == evidence
        with pytest.raises(FailClosedError, match="immutable"):
            store.record_operation_context("evidence", {"changed": True})
    assert sends == [1]


def test_legacy_upstream_receipt_preserved_across_reconcile(tmp_path: Path) -> None:
    with store_with_run(tmp_path) as store:
        store.plan_intent(operation_id="evidence", run_id="run", decision_id=1, kind="TEST",
                          target_id="one", request={}, is_economic=True)
        store.mark_intent_sent("evidence")
        store.resolve_intent("evidence", "UNKNOWN", {"upstreamResponse": {"choices": [42]}})
        coordinator = OperationCoordinator(store)
        for _ in range(2):
            coordinator.reconcile("evidence", lambda: ReadBackResolution(IntentState.UNKNOWN, {}, {}))
        assert store.get_operation_evidence("evidence") == {
            "context": None, "receipt": {"choices": [42]}, "receiptRecorded": True}


def test_context_is_durable_before_send_and_receipt_before_readback(tmp_path: Path) -> None:
    with store_with_run(tmp_path) as store:
        def send():
            assert store.get_operation_evidence("evidence")["context"]["beforeBalance"] == 100
            return None
        def read():
            assert store.get_operation_evidence("evidence")["receiptRecorded"] is True
            return ReadBackResolution(IntentState.SUCCEEDED, {}, {})
        OperationCoordinator(store).execute(**arguments(send, read))


def test_corrupt_evidence_fails_closed_without_reconciliation(tmp_path: Path) -> None:
    with store_with_run(tmp_path) as store:
        coordinator = OperationCoordinator(store)
        coordinator.execute(**arguments(lambda: {}, lambda: ReadBackResolution(IntentState.UNKNOWN, {}, {})))
        with store.transaction() as db:
            db.execute("UPDATE operation_intents SET context_path=NULL WHERE operation_id='evidence'")
        with pytest.raises(FailClosedError, match="Incomplete"):
            coordinator.reconcile("evidence", lambda: pytest.fail("must not read when evidence is corrupt"))


def test_rejection_stays_failed_even_when_readback_errors(tmp_path: Path) -> None:
    def reject():
        raise DeterministicUpstreamError("HTTP_400", "private upstream message")
    def broken():
        raise TimeoutError("private transport info")
    with store_with_run(tmp_path) as store:
        result = OperationCoordinator(store).execute(**arguments(reject, broken))
        assert result.state is IntentState.FAILED
        assert result.verification["requestRejected"] is True
        assert result.verification["readBackCompleted"] is False
        assert "private" not in str(result.result)
        assert store.get_operation_evidence("evidence")["receiptRecorded"] is False


def test_receipt_storage_failure_stops_before_readback_and_never_resends(tmp_path: Path, monkeypatch) -> None:
    sends = []
    with store_with_run(tmp_path) as store:
        def failed_persistence(*args):
            raise FailClosedError("simulated disk error")
        monkeypatch.setattr(store, "record_operation_receipt", failed_persistence)
        coordinator = OperationCoordinator(store)
        params = arguments(lambda: sends.append(1), lambda: pytest.fail("readback must not run"))
        with pytest.raises(FailClosedError, match="disk error"):
            coordinator.execute(**params)
        assert store.get_intent("evidence")["state"] == "SENT"
        params["read_back"] = lambda: ReadBackResolution(IntentState.UNKNOWN, {}, {})
        assert coordinator.execute(**params).state is IntentState.UNKNOWN
    assert sends == [1]
