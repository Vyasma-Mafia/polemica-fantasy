from __future__ import annotations

import datetime as dt
from pathlib import Path
from typing import Any, Callable, Mapping

from polemica_agent.common.canonical import payload_hash
from polemica_agent.common.storage import AuditStore, FailClosedError
from polemica_agent.memory_mcp.evidence import assert_decision_computation_lineage
from .sql_read import fetchall, fetchone


UTC = dt.timezone.utc
TEAM_WRITE_TOOLS = {"fantasy_create_team", "fantasy_update_team"}
META_ARGUMENTS = {"run_id", "operation_id", "decision_id"}


class PersistentActAuthorizer:
    """Authorize one Fantasy call from durable broker-owned evidence and intent state."""

    def __init__(
        self,
        store: AuditStore,
        *,
        series_reader: Callable[[int], Mapping[str, Any]],
        clock: Callable[[], dt.datetime] = lambda: dt.datetime.now(UTC),
        deadline_margin_seconds: int = 300,
        max_decision_age_seconds: int = 3300,
        max_observation_age_seconds: int = 30,
    ) -> None:
        self.store = store
        self.series_reader = series_reader
        self.clock = clock
        self.deadline_margin_seconds = deadline_margin_seconds
        self.max_decision_age_seconds = max_decision_age_seconds
        self.max_observation_age_seconds = max_observation_age_seconds

    def authorize_write(self, tool_name: str, arguments: Mapping[str, Any]) -> None:
        try:
            self._authorize_write(tool_name, arguments)
        except Exception as exc:
            self.store.record_intervention(
                "ACT_DENIED",
                {
                    "tool": tool_name,
                    "operationId": arguments.get("operation_id"),
                    "decisionId": arguments.get("decision_id"),
                    "runId": arguments.get("run_id"),
                    "reason": type(exc).__name__,
                },
                run_id=None,
            )
            raise

    def _authorize_write(self, tool_name: str, arguments: Mapping[str, Any]) -> None:
        run_id = _required_str(arguments, "run_id")
        operation_id = _required_str(arguments, "operation_id")
        decision_id = _required_int(arguments, "decision_id")
        request_digest = payload_hash(dict(arguments))
        now = _aware(self.clock(), "trusted clock")
        decision = fetchone(
            self.store.database_path,
            """
            SELECT d.*, r.status AS run_status
            FROM decisions d JOIN runs r ON r.id=d.run_id
            WHERE d.id=? AND d.run_id=?
            """,
            (decision_id, run_id),
        )
        if decision is None or decision["run_status"] != "RUNNING":
            raise FailClosedError("ACT requires this RUNNING run's decision")
        decided_at = _parse(decision["decided_at"], "decision time")
        age = (now - decided_at).total_seconds()
        if age < -self.max_observation_age_seconds or age > self.max_decision_age_seconds:
            raise FailClosedError("decision is future-dated or stale")
        evidence = fetchall(
            self.store.database_path,
            """
            SELECT s.*, st.trust_kind
            FROM decision_snapshots ds
            JOIN snapshots s ON s.id=ds.snapshot_id
            JOIN snapshot_trust st ON st.snapshot_id=s.id
            WHERE ds.decision_id=?
            """,
            (decision_id,),
        )
        if not evidence or any(
            row["run_id"] != run_id or row["trust_kind"] != "TRUSTED_RESEARCH" or
            row["completeness"] != "COMPLETE" or row["sealed_at"] is None or
            _parse(row["sealed_at"], "seal time") > decided_at or
            _parse(row["as_of"], "evidence as_of") > decided_at
            for row in evidence
        ):
            raise FailClosedError("ACT requires complete trusted evidence sealed before decision")
        assert_decision_computation_lineage(self.store, run_id, decision_id)
        choice = self.store.load_blob(decision["choice_hash"], decision["choice_path"])
        _assert_choice_binding(choice, tool_name, arguments)

        existing = self.store.get_intent(operation_id)
        if existing is not None:
            if existing["run_id"] != run_id or existing["decision_id"] != decision_id:
                raise FailClosedError("operation id is linked to another run or decision")
            # Terminal calls are read-only replays; SENT/UNKNOWN calls reconcile by read-back.
            if existing["state"] in {"SUCCEEDED", "FAILED", "SENT", "UNKNOWN"}:
                self._verify_reservation(
                    decision_id, operation_id, run_id, tool_name, request_digest
                )
                return
        if tool_name in TEAM_WRITE_TOOLS:
            series_id = _required_int(arguments, "series_id")
            observed = self.series_reader(series_id)
            observed_at = _parse(str(observed.get("observedAt", "")), "series observation")
            if abs((now - observed_at).total_seconds()) > self.max_observation_age_seconds:
                raise FailClosedError("series deadline observation is stale")
            deadline = _find_deadline(observed.get("data"))
            if (deadline - now).total_seconds() <= self.deadline_margin_seconds:
                raise FailClosedError("team deadline safety margin has elapsed")
        with self.store.transaction() as db:
            reservation = db.execute(
                "SELECT * FROM act_authorizations WHERE decision_id=? OR operation_id=?",
                (decision_id, operation_id),
            ).fetchone()
            if reservation is not None:
                _assert_reservation(
                    reservation, decision_id, operation_id, run_id, tool_name, request_digest
                )
                return
            blocker = db.execute(
                "SELECT operation_id FROM operation_intents "
                "WHERE state IN ('SENT', 'UNKNOWN') LIMIT 1"
            ).fetchone()
            if blocker is not None:
                raise FailClosedError("open intents must be reconciled before a new ACT")
            pending_grant = db.execute(
                """
                SELECT aa.operation_id
                FROM act_authorizations aa
                LEFT JOIN operation_intents oi ON oi.operation_id=aa.operation_id
                WHERE oi.operation_id IS NULL OR oi.state IN ('PLANNED', 'SENT', 'UNKNOWN')
                LIMIT 1
                """
            ).fetchone()
            if pending_grant is not None:
                raise FailClosedError("another ACT authorization is not terminal")
            prior = db.execute(
                "SELECT operation_id FROM operation_intents WHERE decision_id=? LIMIT 1",
                (decision_id,),
            ).fetchone()
            if prior is not None:
                raise FailClosedError("one decision cannot authorize a second operation")
            db.execute(
                """
                INSERT INTO act_authorizations(
                  decision_id, operation_id, run_id, tool_name, request_hash, authorized_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                (decision_id, operation_id, run_id, tool_name, request_digest, now.isoformat()),
            )

    def _verify_reservation(
        self, decision_id: int, operation_id: str, run_id: str,
        tool_name: str, request_digest: str,
    ) -> None:
        row = fetchone(
            self.store.database_path,
            "SELECT * FROM act_authorizations WHERE decision_id=? OR operation_id=?",
            (decision_id, operation_id),
        )
        if row is None:
            raise FailClosedError("existing intent has no persistent ACT authorization")
        _assert_reservation(row, decision_id, operation_id, run_id, tool_name, request_digest)


def _assert_choice_binding(choice: Any, tool_name: str, arguments: Mapping[str, Any]) -> None:
    if not isinstance(choice, Mapping) or choice.get("tool") != tool_name:
        raise FailClosedError("decision choice is not bound to the requested tool")
    chosen = choice.get("arguments")
    actual = {key: value for key, value in arguments.items() if key not in META_ARGUMENTS}
    if not isinstance(chosen, Mapping) or dict(chosen) != actual:
        raise FailClosedError("decision choice arguments do not match ACT arguments")


def _assert_reservation(
    row: Any, decision_id: int, operation_id: str, run_id: str,
    tool_name: str, request_digest: str,
) -> None:
    if (
        int(row["decision_id"]) != decision_id or row["operation_id"] != operation_id or
        row["run_id"] != run_id or row["tool_name"] != tool_name or
        row["request_hash"] != request_digest
    ):
        raise FailClosedError("ACT authorization is already bound to different parameters")


def _find_deadline(value: Any) -> dt.datetime:
    names = {"teamdeadline", "teamdeadlineat", "deadline", "deadlineat"}
    if isinstance(value, Mapping):
        for key, item in value.items():
            if str(key).lower().replace("_", "") in names and isinstance(item, str):
                return _parse(item, "team deadline")
        for item in value.values():
            try:
                return _find_deadline(item)
            except FailClosedError:
                pass
    elif isinstance(value, list):
        for item in value:
            try:
                return _find_deadline(item)
            except FailClosedError:
                pass
    raise FailClosedError("series response has no parseable team deadline")


def _parse(value: str, name: str) -> dt.datetime:
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        raise FailClosedError(f"{name} is invalid") from None
    return _aware(parsed, name)


def _aware(value: dt.datetime, name: str) -> dt.datetime:
    if value.tzinfo is None:
        raise FailClosedError(f"{name} must be timezone-aware")
    return value.astimezone(UTC)


def _required_str(arguments: Mapping[str, Any], name: str) -> str:
    value = arguments.get(name)
    if not isinstance(value, str) or not value:
        raise FailClosedError(f"{name} is required for ACT")
    return value


def _required_int(arguments: Mapping[str, Any], name: str) -> int:
    value = arguments.get(name)
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise FailClosedError(f"{name} is required for ACT")
    return value
