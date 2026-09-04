from __future__ import annotations

import datetime as dt
from typing import Any, Sequence

from polemica_agent.memory_mcp.service import MemoryService
from polemica_agent.memory_mcp.evidence import assert_trusted_research_snapshots


def _instant(value: str) -> dt.datetime:
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("timestamp must include an offset")
    return parsed


class MemoryTools:
    """JSON-shaped fixed surface over the durable journal; no SQL/filesystem tool."""

    def __init__(self, service: MemoryService) -> None:
        self.service = service

    def start_run(
        self, run_id: str, model: str, prompt_hash: str, tools_hash: str, config_hash: str
    ) -> str:
        return self.service.start_run(
            run_id=run_id, model=model, prompt_hash=prompt_hash,
            tools_hash=tools_hash, config_hash=config_hash,
        )

    def finish_run(self, run_id: str, status: str, summary: Any | None = None) -> None:
        self.service.finish_run(run_id, status, summary)

    def get_open_intents(self, economic_only: bool = False) -> list[dict[str, Any]]:
        return self.service.get_open_intents(economic_only=economic_only)

    def get_relevant_memory(
        self, limit: int = 20, subject_type: str | None = None, subject_id: str | None = None
    ) -> list[dict[str, Any]]:
        return self.service.get_relevant_memory(
            limit=limit, subject_type=subject_type, subject_id=subject_id
        )

    def store_snapshot(
        self, run_id: str, kind: str, as_of: str, generated_at: str, source: str,
        payload: Any, sample_size: int | None = None, completeness: str = "COMPLETE",
    ) -> int:
        snapshot_id = self.service.store_snapshot(
            run_id=run_id, kind=kind, as_of=_instant(as_of), generated_at=_instant(generated_at),
            source=source, payload=payload, sample_size=sample_size, completeness=completeness,
        )
        with self.service.store.transaction() as db:
            db.execute(
                "INSERT INTO snapshot_trust(snapshot_id, trust_kind, collection_id, attested_at) "
                "VALUES (?, 'NON_EVIDENCE', NULL, ?)",
                (snapshot_id, dt.datetime.now(dt.timezone.utc).isoformat()),
            )
        return snapshot_id

    def record_decision(
        self, run_id: str, decision_type: str, subject_type: str, subject_id: str,
        snapshot_ids: Sequence[int], alternatives: Any, choice: Any, rationale: str,
        strategy_version: str | None = None,
    ) -> int:
        assert_trusted_research_snapshots(self.service.store, run_id, snapshot_ids)
        return self.service.record_decision(
            run_id=run_id, decision_type=decision_type, subject_type=subject_type,
            subject_id=subject_id, snapshot_ids=snapshot_ids, alternatives=alternatives,
            choice=choice, rationale=rationale, strategy_version=strategy_version,
        )

    def record_outcome(self, decision_id: int, payload: Any, score: float | None = None) -> int:
        return self.service.record_outcome(decision_id, payload, score)

    def store_raw_payload(
        self, source: str, source_key: str, as_of: str, payload: Any,
        source_version: str | None = None,
    ) -> int:
        return self.service.store_raw_payload(
            source=source, source_key=source_key, as_of=_instant(as_of), payload=payload,
            source_version=source_version,
        )

    def store_derived_features(
        self, subject_type: str, subject_id: str, feature_version: str, as_of: str,
        generated_at: str, payload: Any,
    ) -> int:
        return self.service.store_derived_features(
            subject_type=subject_type, subject_id=subject_id, feature_version=feature_version,
            as_of=_instant(as_of), generated_at=_instant(generated_at), payload=payload,
        )

    def record_intervention(self, reason: str, details: Any, run_id: str | None = None) -> int:
        return self.service.record_intervention(reason, details, run_id)
