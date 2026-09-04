from __future__ import annotations

import time
import uuid
from pathlib import Path
from typing import Any, Mapping, Sequence

from polemica_agent.common.canonical import canonical_json
from polemica_agent.common.storage import AuditStore

from .audit import ComputeAudit
from .client import ComputeWorkerClient, ComputeWorkerError
from .dataset import load_player_game_rows
from .engine import ENGINE_VERSION, OPERATIONS


DATASET_SCHEMA_VERSION = "player-game-v1"


class ComputeServiceError(RuntimeError):
    def __init__(self, code: str) -> None:
        super().__init__(code)
        self.code = code


class ComputeService:
    def __init__(
        self,
        store: AuditStore,
        research_cache: Path,
        worker: ComputeWorkerClient,
    ) -> None:
        self.store = store
        self.audit = ComputeAudit(store)
        self.research_cache = research_cache
        self.worker = worker

    def recover_interrupted_after_singleton_lease(self) -> int:
        """Recover abandoned work only after the caller owns the gateway singleton lease."""
        return self.audit._recover_interrupted_after_singleton_lease()

    def close(self) -> None:
        self.store.close()

    def list_operations(self) -> dict[str, Any]:
        return {
            "engineVersion": ENGINE_VERSION,
            "datasetSchemaVersion": DATASET_SCHEMA_VERSION,
            "operations": [
                {
                    "name": "describe_player_points",
                    "purpose": "Per-player point distribution and bounded quantiles.",
                },
                {
                    "name": "correlate_player_metrics",
                    "purpose": "Pairwise-complete Pearson correlations for fixed numeric features.",
                    "features": ["points", "mmr", "win", "isBlack"],
                },
                {
                    "name": "simulate_player_totals",
                    "purpose": "Seeded empirical simulation of player totals and top-place rates.",
                },
            ],
            "limits": {
                "rows": 20_000, "players": 100, "features": 4,
                "gamesCount": 20, "trials": 10_000,
            },
        }

    def get_result(self, run_id: str, computation_id: str) -> dict[str, Any]:
        _uuid(computation_id)
        stored = self.audit.get_result(computation_id)
        if stored["run_id"] != run_id:
            raise ComputeServiceError("COMPUTE_CROSS_RUN")
        self.audit.validate_snapshot(run_id, int(stored["source_snapshot_id"]))
        return _public_result(stored)

    def run(
        self,
        *,
        run_id: str,
        snapshot_id: int,
        computation_id: str,
        operation: str,
        player_ids: Sequence[int],
        parameters: Mapping[str, Any],
    ) -> dict[str, Any]:
        _uuid(computation_id)
        if operation not in OPERATIONS:
            raise ComputeServiceError("COMPUTE_OPERATION_UNKNOWN")
        snapshot = self.audit.validate_snapshot(run_id, snapshot_id)
        rows, input_records, normalized_rows_hash = load_player_game_rows(
            snapshot.records,
            research_cache=self.research_cache,
            player_ids=player_ids,
        )
        worker_rows = [
            {key: row[key] for key in ("playerId", "points", "mmr", "win", "roleCode")}
            for row in rows
        ]
        worker_payload = {
            "rows": worker_rows,
            "playerIds": list(player_ids),
            **dict(parameters),
        }
        request = {
            "operation": operation,
            "playerIds": list(player_ids),
            "parameters": dict(parameters),
        }
        self.audit.plan(
            computation_id=computation_id,
            run_id=run_id,
            source_snapshot_id=snapshot_id,
            tool_name=f"compute_{operation}",
            engine_version=ENGINE_VERSION,
            dataset_schema_version=DATASET_SCHEMA_VERSION,
            request=request,
            input_records=input_records,
            input_payload=worker_payload,
        )
        claim = self.audit.claim(computation_id)
        if not claim.acquired:
            stored = self.audit.get_result(computation_id)
            if stored["state"] in {"SUCCEEDED", "FAILED", "TIMED_OUT"}:
                return _public_result(stored)
            raise ComputeServiceError("COMPUTE_BUSY")

        started = time.monotonic()
        try:
            # Execute exactly the broker-persisted canonical input, never mutable caller data.
            persisted = self.audit.get_result(computation_id)
            persisted_input = persisted.get("input")
            if not isinstance(persisted_input, Mapping):
                raise ComputeServiceError("COMPUTE_PERSISTED_INPUT_INVALID")
            result = self.worker.run(operation, persisted_input)
            elapsed_ms = max(0, int((time.monotonic() - started) * 1000))
            output_bytes = len(canonical_json(result).encode("utf-8"))
            resolved = self.audit.resolve_success(
                computation_id,
                result=result,
                verification={
                    "sourceSnapshotId": snapshot_id,
                    "sourceManifestHash": snapshot.manifest_hash,
                    "normalizedRowsHash": normalized_rows_hash,
                    "engineVersion": ENGINE_VERSION,
                    "datasetSchemaVersion": DATASET_SCHEMA_VERSION,
                    "bounded": True,
                },
                result_count=_result_count(result),
                output_bytes=output_bytes,
                duration_ms=elapsed_ms,
            )
            return _public_result(self.audit.get_result(str(resolved["id"])))
        except ComputeWorkerError as exc:
            elapsed_ms = max(0, int((time.monotonic() - started) * 1000))
            state = "TIMED_OUT" if exc.code == "COMPUTE_WORKER_TIMEOUT" else "FAILED"
            self.audit.resolve_failure(
                computation_id,
                state=state,
                error_code=exc.code,
                verification={"bounded": True, "workerAccepted": False},
                duration_ms=elapsed_ms,
            )
            return _public_result(self.audit.get_result(computation_id))
        except Exception as exc:
            elapsed_ms = max(0, int((time.monotonic() - started) * 1000))
            code = exc.code if isinstance(exc, ComputeServiceError) else "COMPUTE_BROKER_FAILURE"
            self.audit.resolve_failure(
                computation_id,
                state="FAILED",
                error_code=code,
                verification={"bounded": True, "workerAccepted": False},
                duration_ms=elapsed_ms,
            )
            if isinstance(exc, ComputeServiceError):
                raise
            raise ComputeServiceError(code) from None


def _public_result(stored: Mapping[str, Any]) -> dict[str, Any]:
    return {
        "computationId": stored["id"],
        "runId": stored["run_id"],
        "snapshotId": stored["source_snapshot_id"],
        "operation": stored["tool_name"],
        "state": stored["state"],
        "engineVersion": stored["engine_version"],
        "datasetSchemaVersion": stored["dataset_schema_version"],
        "requestHash": stored["request_hash"],
        "inputHash": stored["input_hash"],
        "resultHash": stored["result_hash"],
        "result": stored.get("result"),
        "verification": stored.get("verification"),
        "errorCode": stored["error_code"],
        "inputCount": stored["input_count"],
        "resultCount": stored["result_count"],
        "outputBytes": stored["output_bytes"],
        "durationMs": stored["duration_ms"],
    }


def _result_count(result: Mapping[str, Any]) -> int:
    for key in ("players", "matrix"):
        value = result.get(key)
        if isinstance(value, list):
            return len(value)
    return 1


def _uuid(value: str) -> None:
    try:
        uuid.UUID(value)
    except (ValueError, TypeError, AttributeError):
        raise ComputeServiceError("COMPUTE_ID_INVALID") from None


__all__ = ["ComputeService", "ComputeServiceError", "DATASET_SCHEMA_VERSION"]
