from __future__ import annotations

import dataclasses
import datetime as dt
import hashlib
import sqlite3
from pathlib import Path
from typing import Any, Sequence

from polemica_agent.common.canonical import redact
from polemica_agent.common.storage import AuditStore, FailClosedError
from polemica_agent.research_mcp.cache import RawPayloadRecord
from polemica_agent.research_mcp.errors import ContractError, SnapshotSealedError
from polemica_agent.research_mcp.snapshots import Snapshot, SnapshotJournal
from polemica_agent.research_mcp.types import PartialError, isoformat, utc_now
from .sql_read import fetchall, fetchone


class DurableResearchSnapshotJournal(SnapshotJournal):
    """Single-broker durable COLLECT/SEAL journal.

    A collection token is not evidence. Only atomic `seal` creates both the
    numeric `snapshots.id` consumed by decisions and its TRUSTED_RESEARCH marker.
    """

    def __init__(self, database_path: Path, *, clock=utc_now) -> None:
        self.store = AuditStore(database_path)
        self.clock = clock

    def close(self) -> None:
        self.store.close()

    def create_collecting(self, run_id: str, snapshot_id: str, created_at: str) -> Snapshot:
        with self.store.transaction() as db:
            run = db.execute("SELECT status FROM runs WHERE id=?", (run_id,)).fetchone()
            if run is None or run["status"] != "RUNNING":
                raise FailClosedError("research collection requires a RUNNING audit run")
            try:
                db.execute(
                    "INSERT INTO research_collections(collection_id, run_id, state, created_at) "
                    "VALUES (?, ?, 'COLLECTING', ?)",
                    (snapshot_id, run_id, created_at),
                )
            except sqlite3.IntegrityError as exc:
                raise ContractError("collection_id already exists") from exc
        return Snapshot(snapshot_id, run_id, "COLLECTING", created_at)

    def get(self, snapshot_id: str) -> Snapshot:
        row = fetchone(
            self.store.database_path,
            "SELECT * FROM research_collections WHERE collection_id=?", (snapshot_id,),
        )
        if row is None:
            raise ContractError("snapshot collection not found")
        records = self._records(snapshot_id)
        public_id: str | int = snapshot_id
        if row["state"] == "SEALED":
            public_id = int(row["evidence_snapshot_id"])
        return Snapshot(
            public_id, row["run_id"], row["state"], row["created_at"], row["as_of"],
            records, row["completeness"], int(row["error_count"]), snapshot_id,
        )

    def attach(self, snapshot_id: str, record: RawPayloadRecord) -> Snapshot:
        with self.store.transaction() as db:
            row = db.execute(
                "SELECT state FROM research_collections WHERE collection_id=?", (snapshot_id,)
            ).fetchone()
            if row is None:
                raise ContractError("snapshot collection not found")
            if row["state"] != "COLLECTING":
                raise SnapshotSealedError("sealed snapshot cannot accept payloads")
            db.execute(
                """
                INSERT OR IGNORE INTO research_collection_records(
                  collection_id, source, object_id, source_version, payload_hash, blob_path,
                  first_seen_at, fetched_at, parser_version, correction_index
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (snapshot_id, record.source, record.object_id, record.source_version,
                 record.payload_hash, record.blob_path, record.first_seen_at,
                 record.fetched_at, record.parser_version, record.correction_index),
            )
        return self.get(snapshot_id)

    def observe_result(
        self, snapshot_id: str, *, complete: bool, sample_size: int,
        errors: Sequence[PartialError],
    ) -> None:
        del sample_size
        with self.store.transaction() as db:
            row = db.execute(
                "SELECT state FROM research_collections WHERE collection_id=?", (snapshot_id,)
            ).fetchone()
            if row is None or row["state"] != "COLLECTING":
                raise SnapshotSealedError("result cannot mutate a sealed or absent snapshot")
            if not complete or errors:
                db.execute(
                    "UPDATE research_collections SET completeness='PARTIAL', "
                    "error_count=MAX(error_count, ?) "
                    "WHERE collection_id=?",
                    (max(1, len(errors)), snapshot_id),
                )

    def seal(self, snapshot_id: str, as_of: str) -> Snapshot:
        now = isoformat(self.clock())
        # BEGIN IMMEDIATE is the freeze boundary shared with attach/observe_result.
        # The exact record set, manifest, trusted snapshot, and SEALED transition
        # are produced while competing writers are excluded.
        with self.store.transaction() as db:
            current = db.execute(
                "SELECT * FROM research_collections WHERE collection_id=?", (snapshot_id,)
            ).fetchone()
            if current is None or current["state"] != "COLLECTING":
                raise SnapshotSealedError("snapshot was sealed concurrently")
            record_rows = db.execute(
                "SELECT * FROM research_collection_records WHERE collection_id=? "
                "ORDER BY fetched_at, source, object_id, payload_hash",
                (snapshot_id,),
            ).fetchall()
            records = _records_from_rows(record_rows)
            if not records:
                raise FailClosedError("an empty research collection cannot become trusted evidence")
            for record in records:
                try:
                    raw = Path(record.blob_path).read_bytes()
                except OSError:
                    raise FailClosedError("research evidence blob is unavailable") from None
                if hashlib.sha256(raw).hexdigest() != record.payload_hash:
                    raise FailClosedError("research evidence blob hash mismatch")
            manifest = {
                "collectionId": snapshot_id,
                "runId": current["run_id"],
                "asOf": as_of,
                "completeness": current["completeness"],
                "errorCount": int(current["error_count"]),
                "records": [dataclasses.asdict(record) for record in records],
            }
            digest, relative = self.store.store_blob(redact(manifest))
            cursor = db.execute(
                """
                INSERT INTO snapshots(
                  run_id, kind, as_of, generated_at, source, sample_size, completeness,
                  payload_hash, payload_path, sealed_at
                ) VALUES (?, 'RESEARCH_EVIDENCE', ?, ?, 'polemica-research', ?, ?, ?, ?, ?)
                """,
                (current["run_id"], as_of, now, len(records), current["completeness"],
                 digest, relative, now),
            )
            evidence_id = int(cursor.lastrowid)
            db.execute(
                "INSERT INTO snapshot_trust(snapshot_id, trust_kind, collection_id, attested_at) "
                "VALUES (?, 'TRUSTED_RESEARCH', ?, ?)",
                (evidence_id, snapshot_id, now),
            )
            updated = db.execute(
                """
                UPDATE research_collections
                SET state='SEALED', as_of=?, evidence_snapshot_id=?, sealed_at=?
                WHERE collection_id=? AND state='COLLECTING'
                """,
                (as_of, evidence_id, now, snapshot_id),
            ).rowcount
            if updated != 1:
                raise FailClosedError("collection seal lost its atomic state transition")
        return self.get(snapshot_id)

    def _records(self, snapshot_id: str) -> tuple[RawPayloadRecord, ...]:
        rows = fetchall(
            self.store.database_path,
            "SELECT * FROM research_collection_records WHERE collection_id=? "
            "ORDER BY fetched_at, source, object_id, payload_hash",
            (snapshot_id,),
        )
        return _records_from_rows(rows)


def _records_from_rows(rows: Sequence[Any]) -> tuple[RawPayloadRecord, ...]:
    return tuple(RawPayloadRecord(
            source=row["source"], object_id=row["object_id"], source_version=row["source_version"],
            payload_hash=row["payload_hash"], blob_path=row["blob_path"],
            first_seen_at=row["first_seen_at"], fetched_at=row["fetched_at"],
            parser_version=row["parser_version"],
            correction_index=int(row["correction_index"]),
        ) for row in rows)


def assert_trusted_research_snapshots(store: AuditStore, run_id: str, snapshot_ids: Sequence[int]) -> None:
    if not snapshot_ids:
        raise FailClosedError("decision requires trusted research evidence")
    placeholders = ",".join("?" for _ in snapshot_ids)
    rows = fetchall(
        store.database_path,
        f"""
        SELECT s.id, s.run_id, s.sealed_at, s.completeness, st.trust_kind,
               rc.run_id AS collection_run_id, rc.state AS collection_state,
               rc.completeness AS collection_completeness, rc.evidence_snapshot_id,
               rc.sealed_at AS collection_sealed_at
        FROM snapshots s
        JOIN snapshot_trust st ON st.snapshot_id=s.id
        LEFT JOIN research_collections rc ON rc.collection_id=st.collection_id
        WHERE s.id IN ({placeholders})
        """,
        tuple(snapshot_ids),
    )
    if len(rows) != len(set(snapshot_ids)) or any(
        row["run_id"] != run_id or row["sealed_at"] is None or
        row["completeness"] != "COMPLETE" or row["trust_kind"] != "TRUSTED_RESEARCH" or
        row["collection_run_id"] != run_id or row["collection_state"] != "SEALED" or
        row["collection_completeness"] != "COMPLETE" or
        row["evidence_snapshot_id"] != row["id"] or row["collection_sealed_at"] is None
        for row in rows
    ):
        raise FailClosedError("decision references untrusted, unsealed, or cross-run evidence")


def assert_decision_computation_lineage(store: AuditStore, run_id: str, decision_id: int) -> None:
    invalid = fetchone(
        store.database_path,
        """
        SELECT 1
        FROM decision_computations dc
        JOIN computations c ON c.id=dc.computation_id
        LEFT JOIN decision_snapshots ds
          ON ds.decision_id=dc.decision_id AND ds.snapshot_id=c.source_snapshot_id
        LEFT JOIN snapshots s ON s.id=c.source_snapshot_id
        LEFT JOIN snapshot_trust st ON st.snapshot_id=s.id
        LEFT JOIN research_collections rc ON rc.collection_id=st.collection_id
        WHERE dc.decision_id=? AND (
          ds.snapshot_id IS NULL OR c.run_id<>? OR c.state<>'SUCCEEDED' OR c.result_hash IS NULL OR
          c.verification_hash IS NULL OR s.run_id<>? OR s.completeness<>'COMPLETE' OR
          s.sealed_at IS NULL OR st.trust_kind IS NULL OR
          st.trust_kind<>'TRUSTED_RESEARCH' OR rc.state IS NULL OR rc.state<>'SEALED' OR
          rc.completeness<>'COMPLETE' OR rc.run_id<>? OR
          rc.evidence_snapshot_id IS NULL OR
          rc.evidence_snapshot_id<>c.source_snapshot_id OR rc.sealed_at IS NULL
        )
        LIMIT 1
        """,
        (decision_id, run_id, run_id, run_id),
    )
    if invalid is not None:
        raise FailClosedError("decision references invalid computation lineage")
