from __future__ import annotations

import datetime as dt
from pathlib import Path
from typing import Any

from polemica_agent.common.storage import AuditStore


class MemoryService:
    """Narrow application API intended to become the Memory MCP tool surface."""

    def __init__(self, database_path: Path) -> None:
        self.store = AuditStore(database_path)

    def close(self) -> None:
        self.store.close()

    def start_run(self, **metadata: str) -> str:
        return self.store.start_run(**metadata)

    def get_open_intents(self, *, economic_only: bool = False) -> list[dict[str, Any]]:
        rows = self.store.unresolved_intents(is_economic=True if economic_only else None)
        return [
            {
                "operationId": row["operation_id"],
                "runId": row["run_id"],
                "kind": row["kind"],
                "targetId": row["target_id"],
                "state": row["state"],
                "plannedAt": row["planned_at"],
                "sentAt": row["sent_at"],
            }
            for row in rows
        ]

    def get_relevant_memory(
        self,
        *,
        limit: int = 20,
        subject_type: str | None = None,
        subject_id: str | None = None,
    ) -> list[dict[str, Any]]:
        return self.store.recent_decisions(
            limit=limit,
            subject_type=subject_type,
            subject_id=subject_id,
        )

    def store_snapshot(
        self,
        *,
        run_id: str,
        kind: str,
        as_of: dt.datetime,
        generated_at: dt.datetime,
        source: str,
        payload: Any,
        sample_size: int | None = None,
        completeness: str = "COMPLETE",
    ) -> int:
        return self.store.create_snapshot(
            run_id=run_id,
            kind=kind,
            as_of=as_of,
            generated_at=generated_at,
            source=source,
            payload=payload,
            sample_size=sample_size,
            completeness=completeness,
        )

    def record_decision(self, **decision: Any) -> int:
        return self.store.record_decision(**decision)

    def record_outcome(self, decision_id: int, payload: Any, score: float | None = None) -> int:
        return self.store.record_outcome(decision_id, payload, score)

    def store_raw_payload(self, **payload: Any) -> int:
        return self.store.store_raw_payload(**payload)

    def store_derived_features(self, **features: Any) -> int:
        return self.store.store_derived_features(**features)

    def record_intervention(self, reason: str, details: Any, run_id: str | None = None) -> int:
        return self.store.record_intervention(reason, details, run_id)

    def finish_run(self, run_id: str, status: str, summary: Any | None = None) -> None:
        self.store.finish_run(run_id, status, summary)
