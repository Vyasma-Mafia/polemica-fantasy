from __future__ import annotations

import datetime as dt
import enum
from dataclasses import dataclass
from typing import Any, Mapping


UTC = dt.timezone.utc


class GateError(RuntimeError):
    pass


class Phase(enum.Enum):
    COLLECT = "COLLECT"
    SEAL = "SEAL"
    DECIDE = "DECIDE"
    ACT = "ACT"
    DONE = "DONE"


@dataclass
class OrchestrationGuard:
    write_enabled: bool = False
    maximum_clock_skew_seconds: int = 30
    deadline_margin_seconds: int = 300
    phase: Phase = Phase.COLLECT
    snapshot_id: str | None = None
    decision_id: int | None = None

    def seal(self, snapshot_id: str) -> None:
        if self.phase is not Phase.COLLECT or not snapshot_id:
            raise GateError("snapshot can only be sealed after COLLECT")
        self.snapshot_id = snapshot_id
        self.phase = Phase.SEAL

    def decide(self, decision_id: int, snapshot_id: str) -> None:
        if self.phase is not Phase.SEAL or snapshot_id != self.snapshot_id or decision_id <= 0:
            raise GateError("decision must reference this run's sealed snapshot")
        self.decision_id = decision_id
        self.phase = Phase.DECIDE

    def authorize_act(
        self, *, snapshot_id: str, decision_id: int, now: dt.datetime,
        trusted_now: dt.datetime, deadline: dt.datetime,
        open_intents: list[Mapping[str, Any]],
    ) -> None:
        if not self.write_enabled:
            raise GateError("WRITE_ENABLED is false")
        if open_intents:
            raise GateError("unresolved intents must be reconciled before ACT")
        if self.phase is not Phase.DECIDE or snapshot_id != self.snapshot_id or decision_id != self.decision_id:
            raise GateError("ACT requires the matching sealed decision")
        for value in (now, trusted_now, deadline):
            if value.tzinfo is None:
                raise GateError("gate timestamps must be timezone-aware")
        skew = abs((now.astimezone(UTC) - trusted_now.astimezone(UTC)).total_seconds())
        if skew > self.maximum_clock_skew_seconds:
            raise GateError("clock skew exceeds the write limit")
        remaining = (deadline.astimezone(UTC) - trusted_now.astimezone(UTC)).total_seconds()
        if remaining <= self.deadline_margin_seconds:
            raise GateError("deadline safety margin has elapsed")
        self.phase = Phase.ACT

    def finish(self) -> None:
        if self.phase not in {Phase.DECIDE, Phase.ACT}:
            raise GateError("run cannot finish before a decision")
        self.phase = Phase.DONE
