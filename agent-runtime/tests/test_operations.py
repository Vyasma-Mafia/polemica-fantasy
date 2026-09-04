from __future__ import annotations

import datetime as dt
from pathlib import Path

import pytest

from polemica_agent.common.operations import (
    DeterministicUpstreamError,
    IntentState,
    OperationCoordinator,
    ReadBackResolution,
)
from polemica_agent.common.storage import AuditStore


def store_with_run(tmp_path: Path) -> AuditStore:
    store = AuditStore((tmp_path / "agent.sqlite3").resolve())
    store.start_run(
        run_id="run",
        model="test",
        prompt_hash="p",
        tools_hash="t",
        config_hash="c",
    )
    now = dt.datetime.now(dt.timezone.utc)
    snapshot_id = store.create_snapshot(
        run_id="run", kind="TEST", as_of=now, generated_at=now, source="test", payload={},
    )
    store.record_decision(
        run_id="run", decision_type="TEST", subject_type="TEST", subject_id="1",
        snapshot_ids=[snapshot_id], alternatives=[], choice={}, rationale="test",
    )
    return store


def test_successful_write_is_sent_once_across_duplicate_call_and_restart(tmp_path: Path) -> None:
    path = (tmp_path / "agent.sqlite3").resolve()
    calls = {"send": 0, "read": 0}

    def send() -> dict[str, bool]:
        calls["send"] += 1
        return {"accepted": True}

    def read() -> ReadBackResolution:
        calls["read"] += 1
        return ReadBackResolution(IntentState.SUCCEEDED, {"saved": True}, {"exact": True})

    with store_with_run(tmp_path) as store:
        first = OperationCoordinator(store).execute(
            operation_id="op",
            run_id="run",
            kind="TEAM_WRITE",
            target_id="1:MAIN",
            request={"cards": [1]},
            is_economic=False,
            decision_id=1,
            send=send,
            read_back=read,
        )
        assert first.state is IntentState.SUCCEEDED
    with AuditStore(path) as restarted:
        second = OperationCoordinator(restarted).execute(
            operation_id="op",
            run_id="run",
            kind="TEAM_WRITE",
            target_id="1:MAIN",
            request={"cards": [1]},
            is_economic=False,
            decision_id=1,
            send=send,
            read_back=read,
        )
        assert second.state is IntentState.SUCCEEDED
        assert second.write_attempted is False
    assert calls == {"send": 1, "read": 1}


def test_timeout_reconciles_without_resend(tmp_path: Path) -> None:
    calls = {"send": 0, "read": 0}

    def timeout() -> None:
        calls["send"] += 1
        raise TimeoutError("unknown commit")

    def read() -> ReadBackResolution:
        calls["read"] += 1
        return ReadBackResolution(IntentState.SUCCEEDED, {"saved": True}, {"exact": True})

    with store_with_run(tmp_path) as store:
        coordinator = OperationCoordinator(store)
        result = coordinator.execute(
            operation_id="op",
            run_id="run",
            kind="TEAM_WRITE",
            target_id="1:MAIN",
            request={"cards": [1]},
            is_economic=False,
            decision_id=1,
            send=timeout,
            read_back=read,
        )
        assert result.state is IntentState.SUCCEEDED
        duplicate = coordinator.execute(
            operation_id="op",
            run_id="run",
            kind="TEAM_WRITE",
            target_id="1:MAIN",
            request={"cards": [1]},
            is_economic=False,
            decision_id=1,
            send=timeout,
            read_back=read,
        )
        assert duplicate.state is IntentState.SUCCEEDED
    assert calls == {"send": 1, "read": 1}


def test_unresolved_readback_remains_unknown_and_is_reconciled_later(tmp_path: Path) -> None:
    observed = {"saved": False}
    sends = 0

    def send() -> None:
        nonlocal sends
        sends += 1
        raise TimeoutError("unknown commit")

    def read() -> ReadBackResolution:
        return ReadBackResolution(
            IntentState.SUCCEEDED if observed["saved"] else IntentState.UNKNOWN,
            dict(observed),
            {"exact": observed["saved"]},
        )

    with store_with_run(tmp_path) as store:
        coordinator = OperationCoordinator(store)
        first = coordinator.execute(
            operation_id="op",
            run_id="run",
            kind="BUY_PACK",
            target_id="1",
            request={"packId": 1},
            is_economic=True,
            decision_id=1,
            send=send,
            read_back=read,
        )
        assert first.state is IntentState.UNKNOWN
        observed["saved"] = True
        reconciled = coordinator.execute(
            operation_id="op",
            run_id="run",
            kind="BUY_PACK",
            target_id="1",
            request={"packId": 1},
            is_economic=True,
            decision_id=1,
            send=send,
            read_back=read,
        )
        assert reconciled.state is IntentState.SUCCEEDED
        assert sends == 1


def test_readback_failure_after_accepted_write_is_unknown_not_resent(tmp_path: Path) -> None:
    sends = 0

    def send() -> dict[str, bool]:
        nonlocal sends
        sends += 1
        return {"accepted": True}

    def broken_readback() -> ReadBackResolution:
        raise OSError("read path unavailable")

    with store_with_run(tmp_path) as store:
        coordinator = OperationCoordinator(store)
        result = coordinator.execute(
            operation_id="op",
            run_id="run",
            kind="BUY_PACK",
            target_id="1",
            request={"packId": 1},
            is_economic=True,
            decision_id=1,
            send=send,
            read_back=broken_readback,
        )
        assert result.state is IntentState.UNKNOWN
        assert result.verification == {"readBackCompleted": False}
        duplicate = coordinator.execute(
            operation_id="op",
            run_id="run",
            kind="BUY_PACK",
            target_id="1",
            request={"packId": 1},
            is_economic=True,
            decision_id=1,
            send=send,
            read_back=broken_readback,
        )
        assert duplicate.state is IntentState.UNKNOWN
    assert sends == 1


def test_restart_from_sent_state_reconciles_without_send(tmp_path: Path) -> None:
    path = (tmp_path / "agent.sqlite3").resolve()
    with store_with_run(tmp_path) as store:
        store.plan_intent(
            operation_id="op",
            run_id="run",
            kind="TEAM_WRITE",
            target_id="1:MAIN",
            request={"cards": [1]},
            is_economic=False,
            decision_id=1,
        )
        store.mark_intent_sent("op")

    def forbidden_send() -> None:
        raise AssertionError("a SENT operation must never be sent again")

    with AuditStore(path) as restarted:
        result = OperationCoordinator(restarted).execute(
            operation_id="op",
            run_id="run",
            kind="TEAM_WRITE",
            target_id="1:MAIN",
            request={"cards": [1]},
            is_economic=False,
            decision_id=1,
            send=forbidden_send,
            read_back=lambda: ReadBackResolution(
                IntentState.SUCCEEDED,
                {"saved": True},
                {"exact": True},
            ),
        )
        assert result.state is IntentState.SUCCEEDED
        assert result.write_attempted is False


@pytest.mark.parametrize(
    "readback_state,expected_state",
    [
        (IntentState.SUCCEEDED, IntentState.SUCCEEDED),
        (IntentState.FAILED, IntentState.FAILED),
        (IntentState.UNKNOWN, IntentState.UNKNOWN),
    ],
)
def test_deterministic_4xx_is_resolved_by_readback_without_retry(
    tmp_path: Path,
    readback_state: IntentState,
    expected_state: IntentState,
) -> None:
    sends = 0
    reads = 0

    def rejected() -> None:
        nonlocal sends
        sends += 1
        raise DeterministicUpstreamError("HTTP_409", "conflict")

    def read_back() -> ReadBackResolution:
        nonlocal reads
        reads += 1
        return ReadBackResolution(
            readback_state,
            {"observed": readback_state.value},
            {
                "readBackCompleted": True,
                "matchesExpectedState": readback_state is IntentState.SUCCEEDED,
                "provesNoChange": readback_state is IntentState.FAILED,
            },
        )

    with store_with_run(tmp_path) as store:
        coordinator = OperationCoordinator(store)
        arguments = {
            "operation_id": "four-x-x",
            "run_id": "run",
            "decision_id": 1,
            "kind": "TEAM_WRITE",
            "target_id": "1:MAIN",
            "request": {"cards": [1]},
            "is_economic": False,
            "send": rejected,
            "read_back": read_back,
        }
        first = coordinator.execute(**arguments)
        duplicate = coordinator.execute(**arguments)

        assert first.state is expected_state
        assert duplicate.state is expected_state
        assert sends == 1
        assert reads == (2 if expected_state is IntentState.UNKNOWN else 1)
        assert first.result == {
            "upstreamError": {
                "errorType": "DeterministicUpstreamError", "errorCode": "HTTP_409",
            },
            "readBack": {"observed": readback_state.value},
        }
        assert first.verification["readBackCompleted"] is True
