from __future__ import annotations

import dataclasses
import enum
from collections.abc import Callable
from typing import Any

from .storage import AuditStore, FailClosedError, IntentConflictError


class IntentState(str, enum.Enum):
    PLANNED = "PLANNED"
    SENT = "SENT"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    UNKNOWN = "UNKNOWN"


class DeterministicUpstreamError(RuntimeError):
    """An upstream rejection known not to have committed the requested write."""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


@dataclasses.dataclass(frozen=True)
class ReadBackResolution:
    state: IntentState
    result: Any
    verification: Any

    def __post_init__(self) -> None:
        if self.state not in {IntentState.SUCCEEDED, IntentState.FAILED, IntentState.UNKNOWN}:
            raise ValueError("read-back must produce SUCCEEDED, FAILED, or UNKNOWN")


@dataclasses.dataclass(frozen=True)
class OperationResult:
    operation_id: str
    state: IntentState
    result: Any
    verification: Any | None
    write_attempted: bool


class OperationCoordinator:
    """Exactly-one-attempt coordinator with mandatory read-back reconciliation."""

    def __init__(self, store: AuditStore) -> None:
        self.store = store

    def execute(
        self,
        *,
        operation_id: str,
        run_id: str,
        kind: str,
        target_id: str,
        request: Any,
        is_economic: bool,
        send: Callable[[], Any],
        read_back: Callable[[], ReadBackResolution],
        decision_id: int,
    ) -> OperationResult:
        intent = self.store.plan_intent(
            operation_id=operation_id,
            run_id=run_id,
            kind=kind,
            target_id=target_id,
            request=request,
            is_economic=is_economic,
            decision_id=decision_id,
        )
        state = IntentState(intent["state"])
        if state in {IntentState.SUCCEEDED, IntentState.FAILED}:
            return self._stored_result(intent, write_attempted=False)
        if state in {IntentState.SENT, IntentState.UNKNOWN}:
            return self.reconcile(operation_id, read_back)

        try:
            self.store.mark_intent_sent(operation_id)
        except FailClosedError:
            concurrent = self.store.get_intent(operation_id)
            if concurrent is None or IntentState(concurrent["state"]) is IntentState.PLANNED:
                raise
            return self.reconcile(operation_id, read_back)
        try:
            send_result = send()
        except DeterministicUpstreamError as exc:
            upstream_error = _safe_exception(exc)
            try:
                resolution = read_back()
            except Exception as read_exc:
                resolution = ReadBackResolution(
                    IntentState.UNKNOWN,
                    _safe_exception(read_exc),
                    {"readBackCompleted": False},
                )
            row = self.store.resolve_intent(
                operation_id,
                resolution.state.value,
                {"upstreamError": upstream_error, "readBack": resolution.result},
                verification=resolution.verification,
            )
            return self._stored_result(row, write_attempted=True)
        except Exception as exc:
            self.store.resolve_intent(
                operation_id,
                IntentState.UNKNOWN.value,
                _safe_exception(exc),
            )
            return self.reconcile(operation_id, read_back, write_attempted=True)

        try:
            resolution = read_back()
        except Exception as exc:
            row = self.store.resolve_intent(
                operation_id,
                IntentState.UNKNOWN.value,
                {
                    "upstreamResponse": send_result,
                    "readBackError": _safe_exception(exc),
                },
                verification={"readBackCompleted": False},
            )
            return self._stored_result(row, write_attempted=True)
        result = {
            "upstreamResponse": send_result,
            "readBack": resolution.result,
        }
        row = self.store.resolve_intent(
            operation_id,
            resolution.state.value,
            result,
            verification=resolution.verification,
        )
        return self._stored_result(row, write_attempted=True)

    def reconcile(
        self,
        operation_id: str,
        read_back: Callable[[], ReadBackResolution],
        *,
        write_attempted: bool = False,
    ) -> OperationResult:
        row = self.store.get_intent(operation_id)
        if row is None:
            raise KeyError(operation_id)
        state = IntentState(row["state"])
        if state in {IntentState.SUCCEEDED, IntentState.FAILED}:
            return self._stored_result(row, write_attempted=False)
        if state not in {IntentState.SENT, IntentState.UNKNOWN}:
            raise RuntimeError(f"Operation {operation_id} is not ready for reconciliation")
        try:
            resolution = read_back()
        except Exception as exc:
            resolution = ReadBackResolution(
                IntentState.UNKNOWN,
                _safe_exception(exc),
                {"readBackCompleted": False},
            )
        updated = self.store.resolve_intent(
            operation_id,
            resolution.state.value,
            resolution.result,
            verification=resolution.verification,
        )
        return self._stored_result(updated, write_attempted=write_attempted)

    def _stored_result(self, row: Any, *, write_attempted: bool) -> OperationResult:
        result = None
        verification = None
        if row["result_hash"] and row["result_path"]:
            result = self.store.load_blob(row["result_hash"], row["result_path"])
        if row["verification_hash"] and row["verification_path"]:
            verification = self.store.load_blob(row["verification_hash"], row["verification_path"])
        return OperationResult(
            operation_id=row["operation_id"],
            state=IntentState(row["state"]),
            result=result,
            verification=verification,
            write_attempted=write_attempted,
        )


def _safe_exception(exc: Exception) -> dict[str, Any]:
    """Return diagnostic metadata without persisting or returning upstream bodies."""
    result: dict[str, Any] = {"errorType": type(exc).__name__}
    code = getattr(exc, "code", None)
    if isinstance(code, str) and code:
        result["errorCode"] = code
    uncertain = getattr(exc, "uncertain", None)
    if isinstance(uncertain, bool):
        result["uncertain"] = uncertain
    return result


__all__ = [
    "DeterministicUpstreamError",
    "IntentConflictError",
    "IntentState",
    "OperationCoordinator",
    "OperationResult",
    "ReadBackResolution",
]
