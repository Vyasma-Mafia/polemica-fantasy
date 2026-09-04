from __future__ import annotations

import dataclasses
import sqlite3
import uuid
from collections.abc import Mapping, Sequence
from typing import Any

from polemica_agent.common.canonical import canonical_json, redact
from polemica_agent.common.storage import AuditStore, FailClosedError


TERMINAL_STATES = frozenset({"SUCCEEDED", "FAILED", "TIMED_OUT"})
CLAIMABLE_STATES = frozenset({"PLANNED", "INTERRUPTED"})
_RECORD_FIELDS = (
    "source",
    "object_id",
    "source_version",
    "payload_hash",
    "blob_path",
    "first_seen_at",
    "fetched_at",
    "parser_version",
    "correction_index",
)
_IDENTITY_FIELDS = ("source", "object_id", "source_version", "payload_hash")


class ComputationConflictError(FailClosedError):
    """A computation id was reused with a different immutable identity."""


@dataclasses.dataclass(frozen=True)
class TrustedSnapshot:
    snapshot_id: int
    run_id: str
    collection_id: str
    as_of: str
    sealed_at: str
    manifest_hash: str
    manifest_path: str
    manifest: Mapping[str, Any]
    records: tuple[Mapping[str, Any], ...]


@dataclasses.dataclass(frozen=True)
class ComputationClaim:
    acquired: bool
    computation: Mapping[str, Any]


class ComputeAudit:
    """Durable broker-owned audit state for deterministic Compute MCP jobs."""

    def __init__(self, store: AuditStore) -> None:
        self.store = store

    def validate_snapshot(self, run_id: str, snapshot_id: int) -> TrustedSnapshot:
        _positive_int(snapshot_id, "snapshot_id")
        _bounded_text(run_id, "run_id")
        with self.store.transaction() as db:
            return self._validate_snapshot(db, run_id, snapshot_id)

    def plan(
        self,
        *,
        computation_id: str,
        run_id: str,
        source_snapshot_id: int,
        tool_name: str,
        engine_version: str,
        dataset_schema_version: str,
        request: Any,
        input_records: Sequence[Mapping[str, Any]],
        input_payload: Any,
    ) -> Mapping[str, Any]:
        _uuid(computation_id, "computation_id")
        _bounded_text(run_id, "run_id")
        tool_name = _bounded_text(tool_name, "tool_name")
        engine_version = _bounded_text(engine_version, "engine_version")
        dataset_schema_version = _bounded_text(dataset_schema_version, "dataset_schema_version")
        if not 1 <= len(input_records) <= 1024:
            raise ValueError("input_records must contain 1..1024 records")

        snapshot = self.validate_snapshot(run_id, source_snapshot_id)
        selected_records = _select_records(snapshot.records, input_records)
        selected_manifest = {
            "sourceSnapshotId": snapshot.snapshot_id,
            "sourceManifestHash": snapshot.manifest_hash,
            "asOf": snapshot.as_of,
            "records": list(selected_records),
        }
        clean_request = redact(request)
        clean_input = redact(input_payload)
        request_hash, request_path = self.store.store_blob(clean_request)
        manifest_hash, manifest_path = self.store.store_blob(selected_manifest)
        input_hash, input_path = self.store.store_blob(clean_input)
        immutable = {
            "run_id": run_id,
            "source_snapshot_id": source_snapshot_id,
            "tool_name": tool_name,
            "engine_version": engine_version,
            "dataset_schema_version": dataset_schema_version,
            "request_hash": request_hash,
            "request_path": request_path,
            "manifest_hash": manifest_hash,
            "manifest_path": manifest_path,
            "input_hash": input_hash,
            "input_path": input_path,
            "input_count": len(selected_records),
        }
        with self.store.transaction() as db:
            existing = db.execute("SELECT * FROM computations WHERE id=?", (computation_id,)).fetchone()
            if existing is not None:
                _assert_identity(existing, immutable, computation_id)
                _assert_input_rows(db, computation_id, selected_records)
                return dict(existing)
            # Close the validate/plan race with run finalization or evidence replacement.
            self._validate_snapshot(db, run_id, source_snapshot_id)
            db.execute(
                """
                INSERT INTO computations(
                  id, run_id, source_snapshot_id, tool_name, engine_version,
                  dataset_schema_version, request_hash, request_path, manifest_hash,
                  manifest_path, input_hash, input_path, state, input_count, planned_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PLANNED', ?, ?)
                """,
                (
                    computation_id, run_id, source_snapshot_id, tool_name, engine_version,
                    dataset_schema_version, request_hash, request_path, manifest_hash,
                    manifest_path, input_hash, input_path, len(selected_records), _timestamp(),
                ),
            )
            db.executemany(
                """
                INSERT INTO computation_inputs(
                  computation_id, ordinal, source, object_id, source_version, payload_hash
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                [
                    (
                        computation_id, ordinal, record["source"], record["object_id"],
                        record["source_version"], record["payload_hash"],
                    )
                    for ordinal, record in enumerate(selected_records)
                ],
            )
            return dict(db.execute("SELECT * FROM computations WHERE id=?", (computation_id,)).fetchone())

    def claim(self, computation_id: str) -> ComputationClaim:
        _uuid(computation_id, "computation_id")
        with self.store.transaction() as db:
            row = db.execute("SELECT * FROM computations WHERE id=?", (computation_id,)).fetchone()
            if row is None:
                raise KeyError(computation_id)
            if row["state"] not in CLAIMABLE_STATES:
                return ComputationClaim(False, dict(row))
            snapshot = self._validate_snapshot(db, row["run_id"], int(row["source_snapshot_id"]))
            computation_manifest = self.store.load_blob(row["manifest_hash"], row["manifest_path"])
            if not isinstance(computation_manifest, Mapping):
                raise FailClosedError("computation manifest is not an object")
            _assert_input_rows(
                db,
                computation_id,
                _selected_manifest_records(computation_manifest),
            )
            if snapshot.manifest_hash != computation_manifest.get("sourceManifestHash"):
                raise FailClosedError("computation source manifest changed")
            updated = db.execute(
                """
                UPDATE computations
                SET state='RUNNING', started_at=?, finished_at=NULL, duration_ms=NULL,
                    error_code=NULL, claim_count=claim_count+1
                WHERE id=? AND state IN ('PLANNED', 'INTERRUPTED')
                """,
                (_timestamp(), computation_id),
            ).rowcount
            current = db.execute("SELECT * FROM computations WHERE id=?", (computation_id,)).fetchone()
            return ComputationClaim(updated == 1, dict(current))

    def resolve_success(
        self,
        computation_id: str,
        *,
        result: Any,
        verification: Any,
        result_count: int,
        output_bytes: int,
        duration_ms: int,
    ) -> Mapping[str, Any]:
        return self._resolve(
            computation_id,
            state="SUCCEEDED",
            result=result,
            verification=verification,
            error_code=None,
            result_count=result_count,
            output_bytes=output_bytes,
            duration_ms=duration_ms,
        )

    def resolve_failure(
        self,
        computation_id: str,
        *,
        state: str,
        error_code: str,
        verification: Any,
        result: Any | None = None,
        result_count: int | None = None,
        output_bytes: int | None = None,
        duration_ms: int | None = None,
    ) -> Mapping[str, Any]:
        if state not in {"FAILED", "TIMED_OUT"}:
            raise ValueError("failure state must be FAILED or TIMED_OUT")
        return self._resolve(
            computation_id,
            state=state,
            result=result,
            verification=verification,
            error_code=_bounded_text(error_code, "error_code"),
            result_count=result_count,
            output_bytes=output_bytes,
            duration_ms=duration_ms,
        )

    def get_result(self, computation_id: str) -> Mapping[str, Any]:
        _uuid(computation_id, "computation_id")
        with self.store.transaction() as db:
            row = db.execute("SELECT * FROM computations WHERE id=?", (computation_id,)).fetchone()
            if row is None:
                raise KeyError(computation_id)
            inputs = db.execute(
                "SELECT * FROM computation_inputs WHERE computation_id=? ORDER BY ordinal",
                (computation_id,),
            ).fetchall()
        value = dict(row)
        value["inputs"] = [dict(item) for item in inputs]
        for name in ("request", "manifest", "input", "result", "verification"):
            digest, path = row[f"{name}_hash"], row[f"{name}_path"]
            value[name] = None if digest is None else self.store.load_blob(digest, path)
        return value

    def _recover_interrupted_after_singleton_lease(
        self, *, error_code: str = "BROKER_RESTART"
    ) -> int:
        """Internal startup hook; caller must already own the gateway singleton lease."""
        error_code = _bounded_text(error_code, "error_code")
        with self.store.transaction() as db:
            return db.execute(
                """
                UPDATE computations
                SET state='INTERRUPTED', error_code=?, finished_at=?
                WHERE state='RUNNING'
                """,
                (error_code, _timestamp()),
            ).rowcount

    def _resolve(
        self,
        computation_id: str,
        *,
        state: str,
        result: Any | None,
        verification: Any,
        error_code: str | None,
        result_count: int | None,
        output_bytes: int | None,
        duration_ms: int | None,
    ) -> Mapping[str, Any]:
        _uuid(computation_id, "computation_id")
        _nonnegative_optional(result_count, "result_count")
        _nonnegative_optional(output_bytes, "output_bytes")
        _nonnegative_optional(duration_ms, "duration_ms")
        result_hash = result_path = None
        if result is not None:
            result_hash, result_path = self.store.store_blob(redact(result))
        verification_hash, verification_path = self.store.store_blob(redact(verification))
        expected = {
            "state": state,
            "result_hash": result_hash,
            "result_path": result_path,
            "verification_hash": verification_hash,
            "verification_path": verification_path,
            "error_code": error_code,
            "result_count": result_count,
            "output_bytes": output_bytes,
            "duration_ms": duration_ms,
        }
        with self.store.transaction() as db:
            row = db.execute("SELECT * FROM computations WHERE id=?", (computation_id,)).fetchone()
            if row is None:
                raise KeyError(computation_id)
            if row["state"] in TERMINAL_STATES:
                if all(row[key] == value for key, value in expected.items()):
                    return dict(row)
                raise ComputationConflictError(
                    f"Computation {computation_id} already has a different terminal result"
                )
            if row["state"] != "RUNNING":
                raise FailClosedError(f"Computation {computation_id} cannot resolve from {row['state']}")
            db.execute(
                """
                UPDATE computations
                SET state=?, result_hash=?, result_path=?, verification_hash=?,
                    verification_path=?, error_code=?, result_count=?, output_bytes=?,
                    duration_ms=?, finished_at=?
                WHERE id=? AND state='RUNNING'
                """,
                (
                    state, result_hash, result_path, verification_hash, verification_path,
                    error_code, result_count, output_bytes, duration_ms, _timestamp(), computation_id,
                ),
            )
            return dict(db.execute("SELECT * FROM computations WHERE id=?", (computation_id,)).fetchone())

    def _validate_snapshot(
        self, db: sqlite3.Connection, run_id: str, snapshot_id: int
    ) -> TrustedSnapshot:
        row = db.execute(
            """
            SELECT s.*, st.trust_kind, st.collection_id, rc.state AS collection_state,
                   rc.completeness AS collection_completeness,
                   rc.run_id AS collection_run_id, rc.error_count AS collection_error_count,
                   rc.evidence_snapshot_id, rc.sealed_at AS collection_sealed_at,
                   r.status AS run_status
            FROM snapshots s
            JOIN snapshot_trust st ON st.snapshot_id=s.id
            JOIN research_collections rc ON rc.collection_id=st.collection_id
            JOIN runs r ON r.id=s.run_id
            WHERE s.id=? AND s.run_id=?
            """,
            (snapshot_id, run_id),
        ).fetchone()
        if row is None or row["run_status"] != "RUNNING":
            raise FailClosedError("compute requires a current RUNNING run snapshot")
        if (
            row["trust_kind"] != "TRUSTED_RESEARCH"
            or row["kind"] != "RESEARCH_EVIDENCE"
            or row["completeness"] != "COMPLETE"
            or row["collection_completeness"] != "COMPLETE"
            or row["collection_run_id"] != run_id
            or row["evidence_snapshot_id"] != snapshot_id
            or row["sealed_at"] is None
            or row["collection_sealed_at"] is None
            or row["collection_state"] != "SEALED"
        ):
            raise FailClosedError("compute requires COMPLETE sealed TRUSTED_RESEARCH evidence")
        manifest = self.store.load_blob(row["payload_hash"], row["payload_path"])
        if not isinstance(manifest, Mapping):
            raise FailClosedError("trusted research manifest is not an object")
        record_rows = db.execute(
            "SELECT * FROM research_collection_records WHERE collection_id=? ",
            (row["collection_id"],),
        ).fetchall()
        records = tuple(_record_from_row(item) for item in record_rows)
        _verify_manifest(manifest, row, records)
        return TrustedSnapshot(
            snapshot_id=snapshot_id,
            run_id=run_id,
            collection_id=row["collection_id"],
            as_of=row["as_of"],
            sealed_at=row["sealed_at"],
            manifest_hash=row["payload_hash"],
            manifest_path=row["payload_path"],
            manifest=manifest,
            records=records,
        )


def _verify_manifest(
    manifest: Mapping[str, Any], row: sqlite3.Row, records: Sequence[Mapping[str, Any]]
) -> None:
    if (
        manifest.get("collectionId") != row["collection_id"]
        or manifest.get("runId") != row["run_id"]
        or manifest.get("asOf") != row["as_of"]
        or manifest.get("completeness") != "COMPLETE"
        or manifest.get("errorCount") != row["collection_error_count"]
        or not isinstance(manifest.get("records"), list)
        or row["sample_size"] != len(records)
    ):
        raise FailClosedError("trusted research manifest metadata mismatch")
    actual = sorted(canonical_json(_normal_record(item)) for item in manifest["records"])
    expected = sorted(canonical_json(item) for item in records)
    if actual != expected:
        raise FailClosedError("trusted research manifest records mismatch")


def _select_records(
    available: Sequence[Mapping[str, Any]], selected: Sequence[Mapping[str, Any]]
) -> tuple[Mapping[str, Any], ...]:
    indexed = {_identity(record): record for record in available}
    result: list[Mapping[str, Any]] = []
    seen: set[tuple[Any, ...]] = set()
    for item in selected:
        key = _identity(item)
        if key in seen:
            raise ValueError("input_records must be unique")
        try:
            record = indexed[key]
        except KeyError:
            raise FailClosedError("compute input is not part of the sealed snapshot") from None
        seen.add(key)
        result.append(record)
    return tuple(result)


def _normal_record(value: Mapping[str, Any]) -> dict[str, Any]:
    if not isinstance(value, Mapping):
        raise FailClosedError("research manifest record is not an object")
    try:
        return {field: value[field] for field in _RECORD_FIELDS}
    except KeyError:
        raise FailClosedError("research manifest record is incomplete") from None


def _record_from_row(row: sqlite3.Row) -> Mapping[str, Any]:
    return {field: row[field] for field in _RECORD_FIELDS}


def _identity(record: Mapping[str, Any]) -> tuple[Any, ...]:
    if not isinstance(record, Mapping):
        raise ValueError("compute input selector must be an object")
    try:
        return tuple(record[field] for field in _IDENTITY_FIELDS)
    except KeyError:
        raise ValueError("compute input selector identity is incomplete") from None


def _selected_manifest_records(manifest: Any) -> tuple[Mapping[str, Any], ...]:
    if not isinstance(manifest, Mapping) or not isinstance(manifest.get("records"), list):
        raise FailClosedError("computation manifest is invalid")
    return tuple(_normal_record(item) for item in manifest["records"])


def _assert_input_rows(
    db: sqlite3.Connection, computation_id: str, records: Sequence[Mapping[str, Any]]
) -> None:
    rows = db.execute(
        "SELECT * FROM computation_inputs WHERE computation_id=? ORDER BY ordinal",
        (computation_id,),
    ).fetchall()
    expected = [
        (index, item["source"], item["object_id"], item["source_version"], item["payload_hash"])
        for index, item in enumerate(records)
    ]
    actual = [
        (row["ordinal"], row["source"], row["object_id"], row["source_version"], row["payload_hash"])
        for row in rows
    ]
    if actual != expected:
        raise FailClosedError("computation input identities mismatch")


def _assert_identity(row: sqlite3.Row, immutable: Mapping[str, Any], computation_id: str) -> None:
    if any(row[key] != value for key, value in immutable.items()):
        raise ComputationConflictError(
            f"Computation {computation_id} already exists with a different identity"
        )


def _positive_int(value: Any, name: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{name} must be a positive integer")
    return value


def _nonnegative_optional(value: Any, name: str) -> None:
    if value is not None and (isinstance(value, bool) or not isinstance(value, int) or value < 0):
        raise ValueError(f"{name} must be a non-negative integer")


def _uuid(value: str, name: str) -> None:
    try:
        uuid.UUID(value)
    except (ValueError, AttributeError, TypeError):
        raise ValueError(f"{name} must be a UUID") from None


def _bounded_text(value: Any, name: str) -> str:
    if not isinstance(value, str) or not value or len(value) > 128 or "\x00" in value:
        raise ValueError(f"{name} must contain 1..128 non-NUL characters")
    return value


def _timestamp() -> str:
    import datetime as dt

    return dt.datetime.now(dt.timezone.utc).isoformat()


__all__ = [
    "ComputationClaim",
    "ComputationConflictError",
    "ComputeAudit",
    "TrustedSnapshot",
]
