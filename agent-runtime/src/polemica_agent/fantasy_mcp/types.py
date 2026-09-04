from __future__ import annotations

import dataclasses
import datetime as dt
from typing import Any

from polemica_agent.common.operations import OperationResult


def observed_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


@dataclasses.dataclass(frozen=True)
class ReadEnvelope:
    observed_at: str
    source: str
    data: Any

    def as_dict(self) -> dict[str, Any]:
        return {"observedAt": self.observed_at, "source": self.source, "data": self.data}


@dataclasses.dataclass(frozen=True)
class OperationEnvelope:
    operation_id: str
    outcome: str
    observed_at: str
    result: Any
    verification: Any
    write_attempted: bool

    @classmethod
    def from_result(cls, result: OperationResult) -> "OperationEnvelope":
        return cls(
            operation_id=result.operation_id,
            outcome=result.state.value,
            observed_at=observed_now(),
            result=result.result,
            verification=result.verification,
            write_attempted=result.write_attempted,
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "operationId": self.operation_id,
            "outcome": self.outcome,
            "observedAt": self.observed_at,
            "result": self.result,
            "verification": self.verification,
            "writeAttempted": self.write_attempted,
        }
