from __future__ import annotations

import contextlib
import datetime as dt
import hashlib
import json
import os
import sqlite3
import tempfile
import threading
import uuid
from collections.abc import Iterator, Mapping, Sequence
from pathlib import Path
from typing import Any

from .canonical import canonical_json, payload_hash, redact


UTC = dt.timezone.utc
_SQLITE_LOCK = threading.RLock()


class FailClosedError(RuntimeError):
    """Raised when durable audit state cannot be proven safe or complete."""


class IntentConflictError(RuntimeError):
    pass


class AuditStore:
    """Durable SQLite audit store and content-addressed payload repository.

    Writes use `BEGIN IMMEDIATE`, SQLite WAL, and `synchronous=FULL`. Payload blobs
    are fsynced and atomically renamed before a database row may reference them.
    """

    def __init__(self, database_path: Path, migrations_path: Path | None = None) -> None:
        database_path = database_path.expanduser()
        if not database_path.is_absolute():
            raise ValueError("database_path must be absolute")
        self.database_path = database_path
        self.state_dir = database_path.parent
        self.blob_dir = self.state_dir / "blobs"
        self.migrations_path = migrations_path or _default_migrations_path()
        # MCP SDK executes synchronous tools in worker threads. One process-wide
        # re-entrant lock protects the shared sqlite connection and nested blob I/O.
        self._lock = _SQLITE_LOCK
        self._prepare_directories()
        try:
            self.connection = sqlite3.connect(
                self.database_path,
                timeout=5.0,
                isolation_level=None,
                check_same_thread=False,
            )
            with self._lock:
                self.connection.row_factory = sqlite3.Row
                self.connection.execute("PRAGMA foreign_keys=ON")
                self.connection.execute("PRAGMA busy_timeout=5000")
                journal_mode = self.connection.execute("PRAGMA journal_mode=WAL").fetchone()[0]
                if str(journal_mode).lower() != "wal":
                    raise FailClosedError(f"SQLite refused WAL mode: {journal_mode}")
                self.connection.execute("PRAGMA synchronous=FULL")
                self._apply_migrations()
            self._chmod_database_files()
        except (OSError, sqlite3.Error) as exc:
            raise FailClosedError(f"Cannot initialize durable audit store: {exc}") from exc

    def __enter__(self) -> "AuditStore":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()

    def close(self) -> None:
        with self._lock:
            self.connection.close()

    def _prepare_directories(self) -> None:
        try:
            self.state_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
            self.blob_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
            os.chmod(self.state_dir, 0o700)
            os.chmod(self.blob_dir, 0o700)
        except OSError as exc:
            raise FailClosedError(f"Cannot prepare state directories: {exc}") from exc

    def _chmod_database_files(self) -> None:
        for path in (self.database_path, self.database_path.with_name(self.database_path.name + "-wal")):
            if path.exists():
                os.chmod(path, 0o600)

    def _apply_migrations(self) -> None:
        migration_files = sorted(self.migrations_path.glob("[0-9][0-9][0-9]_*.sql"))
        if not migration_files:
            raise FailClosedError(f"No runtime migrations found in {self.migrations_path}")
        self.connection.execute(
            """
            CREATE TABLE IF NOT EXISTS schema_meta (
              version INTEGER PRIMARY KEY,
              checksum TEXT NOT NULL,
              applied_at TEXT NOT NULL
            )
            """,
        )
        applied = {
            int(row["version"]): row["checksum"]
            for row in self.connection.execute("SELECT version, checksum FROM schema_meta").fetchall()
        }
        for path in migration_files:
            version = int(path.name.split("_", 1)[0])
            sql = path.read_text(encoding="utf-8")
            checksum = hashlib.sha256(sql.encode("utf-8")).hexdigest()
            if version in applied:
                if applied[version] != checksum:
                    raise FailClosedError(f"Runtime migration {path.name} checksum changed")
                continue
            applied_at = _timestamp()
            escaped = applied_at.replace("'", "''")
            try:
                self.connection.executescript(
                    f"BEGIN IMMEDIATE;\n{sql}\n"
                    "INSERT INTO schema_meta(version, checksum, applied_at) "
                    f"VALUES ({version}, '{checksum}', '{escaped}');\nCOMMIT;",
                )
            except (OSError, sqlite3.Error) as exc:
                with contextlib.suppress(sqlite3.Error):
                    self.connection.execute("ROLLBACK")
                raise FailClosedError(f"Runtime migration {path.name} failed: {exc}") from exc

    @contextlib.contextmanager
    def transaction(self) -> Iterator[sqlite3.Connection]:
        with self._lock:
            try:
                self.connection.execute("BEGIN IMMEDIATE")
                yield self.connection
                self.connection.execute("COMMIT")
            except Exception:
                with contextlib.suppress(sqlite3.Error):
                    self.connection.execute("ROLLBACK")
                raise

    def store_blob(self, value: Any) -> tuple[str, str]:
        with self._lock:
            encoded = canonical_json(value).encode("utf-8")
            digest = hashlib.sha256(encoded).hexdigest()
            relative = f"{digest[:2]}/{digest}.json"
            destination = self.blob_dir / relative
            if destination.exists():
                self._verify_blob(destination, digest)
                return digest, relative
            destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            os.chmod(destination.parent, 0o700)
            try:
                fd, temporary_name = tempfile.mkstemp(prefix=".blob-", dir=destination.parent)
                temporary = Path(temporary_name)
                try:
                    with os.fdopen(fd, "wb") as handle:
                        handle.write(encoded)
                        handle.flush()
                        os.fsync(handle.fileno())
                    os.chmod(temporary, 0o600)
                    os.replace(temporary, destination)
                    directory_fd = os.open(destination.parent, os.O_RDONLY)
                    try:
                        os.fsync(directory_fd)
                    finally:
                        os.close(directory_fd)
                finally:
                    with contextlib.suppress(FileNotFoundError):
                        temporary.unlink()
            except OSError as exc:
                raise FailClosedError(f"Cannot persist audit blob {digest}: {exc}") from exc
            return digest, relative

    def load_blob(self, digest: str, relative_path: str) -> Any:
        with self._lock:
            path = self.blob_dir / relative_path
            try:
                data = path.read_bytes()
            except OSError as exc:
                raise FailClosedError(f"Cannot read audit blob {digest}: {exc}") from exc
            actual = hashlib.sha256(data).hexdigest()
            if actual != digest:
                raise FailClosedError(f"Audit blob hash mismatch: expected {digest}, got {actual}")
            return json.loads(data)

    @staticmethod
    def _verify_blob(path: Path, expected_hash: str) -> None:
        try:
            actual = hashlib.sha256(path.read_bytes()).hexdigest()
        except OSError as exc:
            raise FailClosedError(f"Cannot verify audit blob: {exc}") from exc
        if actual != expected_hash:
            raise FailClosedError(f"Existing audit blob hash mismatch for {expected_hash}")

    def start_run(
        self,
        *,
        run_id: str | None = None,
        model: str,
        prompt_hash: str,
        tools_hash: str,
        config_hash: str,
        strategy_version: str | None = None,
    ) -> str:
        run_id = run_id or str(uuid.uuid4())
        with self.transaction() as db:
            db.execute(
                """
                INSERT INTO runs(
                  id, started_at, status, model, prompt_hash, tools_hash, config_hash,
                  strategy_version
                ) VALUES (?, ?, 'RUNNING', ?, ?, ?, ?, ?)
                """,
                (
                    run_id, _timestamp(), model, prompt_hash, tools_hash, config_hash,
                    strategy_version,
                ),
            )
        return run_id

    def finish_run(
        self, run_id: str, status: str, summary: Mapping[str, Any] | None = None,
        *, require_decision: bool = False,
    ) -> None:
        if status not in {"SUCCEEDED", "FAILED", "TIMED_OUT"}:
            raise ValueError(f"Invalid terminal run status: {status}")
        summary_hash = summary_path = None
        if summary is not None:
            summary_hash, summary_path = self.store_blob(redact(summary))
        with self.transaction() as db:
            if require_decision and status == "SUCCEEDED":
                decision = db.execute(
                    """
                    SELECT 1
                    FROM decisions d
                    JOIN runs r ON r.id = d.run_id
                    WHERE d.run_id=?
                      AND r.strategy_version IS NOT NULL
                      AND d.strategy_version = r.strategy_version
                    LIMIT 1
                    """,
                    (run_id,),
                ).fetchone()
                if decision is None:
                    raise FailClosedError(f"Run {run_id} cannot succeed without a decision")
            updated = db.execute(
                """
                UPDATE runs SET finished_at=?, status=?, summary_hash=?, summary_path=?
                WHERE id=? AND status='RUNNING'
                """,
                (_timestamp(), status, summary_hash, summary_path, run_id),
            ).rowcount
            if updated != 1:
                raise FailClosedError(f"Run {run_id} is absent or already terminal")

    def create_snapshot(
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
        as_of = _aware_utc(as_of)
        generated_at = _aware_utc(generated_at)
        if as_of > generated_at:
            raise ValueError("snapshot as_of cannot be after generated_at")
        if completeness not in {"COMPLETE", "PARTIAL"}:
            raise ValueError("completeness must be COMPLETE or PARTIAL")
        digest, relative = self.store_blob(redact(payload))
        with self.transaction() as db:
            cursor = db.execute(
                """
                INSERT INTO snapshots(
                  run_id, kind, as_of, generated_at, source, sample_size, completeness,
                  payload_hash, payload_path, sealed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    run_id,
                    kind,
                    as_of.isoformat(),
                    generated_at.isoformat(),
                    source,
                    sample_size,
                    completeness,
                    digest,
                    relative,
                    _timestamp(),
                ),
            )
        return int(cursor.lastrowid)

    def record_strategy(self, version: str, prompt_hash: str, config: Any) -> None:
        digest, relative = self.store_blob(redact(config))
        with self.transaction() as db:
            existing = db.execute(
                "SELECT prompt_hash, config_hash FROM strategy_versions WHERE version=?",
                (version,),
            ).fetchone()
            if existing is not None:
                if existing["prompt_hash"] != prompt_hash or existing["config_hash"] != digest:
                    raise FailClosedError(f"Strategy version {version} has different immutable content")
                return
            db.execute(
                """
                INSERT INTO strategy_versions(version, created_at, prompt_hash, config_hash, config_path)
                VALUES (?, ?, ?, ?, ?)
                """,
                (version, _timestamp(), prompt_hash, digest, relative),
            )

    def store_raw_payload(
        self,
        *,
        source: str,
        source_key: str,
        as_of: dt.datetime,
        payload: Any,
        source_version: str | None = None,
    ) -> int:
        as_of = _aware_utc(as_of)
        digest, relative = self.store_blob(redact(payload))
        with self.transaction() as db:
            cursor = db.execute(
                """
                INSERT OR IGNORE INTO raw_payload_refs(
                  source, source_key, source_version, observed_at, as_of, payload_hash, payload_path
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (source, source_key, source_version, _timestamp(), as_of.isoformat(), digest, relative),
            )
            if cursor.rowcount == 1:
                return int(cursor.lastrowid)
            row = db.execute(
                """
                SELECT id FROM raw_payload_refs
                WHERE source=? AND source_key=? AND source_version IS ? AND payload_hash=?
                """,
                (source, source_key, source_version, digest),
            ).fetchone()
            if row is None:
                raise FailClosedError("Raw payload insert could not be verified")
            return int(row["id"])

    def store_derived_features(
        self,
        *,
        subject_type: str,
        subject_id: str,
        feature_version: str,
        as_of: dt.datetime,
        generated_at: dt.datetime,
        payload: Any,
    ) -> int:
        as_of = _aware_utc(as_of)
        generated_at = _aware_utc(generated_at)
        if as_of > generated_at:
            raise ValueError("feature as_of cannot be after generated_at")
        digest, relative = self.store_blob(redact(payload))
        with self.transaction() as db:
            cursor = db.execute(
                """
                INSERT INTO derived_features(
                  subject_type, subject_id, feature_version, as_of, generated_at,
                  payload_hash, payload_path
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    subject_type,
                    subject_id,
                    feature_version,
                    as_of.isoformat(),
                    generated_at.isoformat(),
                    digest,
                    relative,
                ),
            )
        return int(cursor.lastrowid)

    def record_decision(
        self,
        *,
        run_id: str,
        decision_type: str,
        subject_type: str,
        subject_id: str,
        snapshot_ids: Sequence[int],
        alternatives: Any,
        choice: Any,
        rationale: str,
        strategy_version: str | None = None,
    ) -> int:
        if not snapshot_ids:
            raise ValueError("A decision must reference at least one sealed snapshot")
        alternatives_hash, alternatives_path = self.store_blob(redact(alternatives))
        choice_hash, choice_path = self.store_blob(redact(choice))
        with self.transaction() as db:
            run = db.execute(
                "SELECT strategy_version FROM runs WHERE id=? AND status='RUNNING'", (run_id,),
            ).fetchone()
            if run is None:
                raise FailClosedError("Decision run is absent or already terminal")
            trusted_strategy = run["strategy_version"]
            if trusted_strategy is not None and strategy_version != trusted_strategy:
                raise FailClosedError("Decision strategy does not match the trusted run strategy")
            placeholders = ",".join("?" for _ in snapshot_ids)
            rows = db.execute(
                f"SELECT id, run_id, as_of, sealed_at FROM snapshots WHERE id IN ({placeholders})",
                tuple(snapshot_ids),
            ).fetchall()
            if len(rows) != len(set(snapshot_ids)):
                raise FailClosedError("Decision references an absent snapshot")
            if any(row["run_id"] != run_id or row["sealed_at"] is None for row in rows):
                raise FailClosedError("Decision snapshots must be sealed by the same run")
            decision_as_of = max(row["as_of"] for row in rows)
            cursor = db.execute(
                """
                INSERT INTO decisions(
                  run_id, decision_type, subject_type, subject_id, decided_at, as_of,
                  strategy_version, alternatives_hash, alternatives_path, choice_hash,
                  choice_path, rationale
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    run_id,
                    decision_type,
                    subject_type,
                    subject_id,
                    _timestamp(),
                    decision_as_of,
                    strategy_version,
                    alternatives_hash,
                    alternatives_path,
                    choice_hash,
                    choice_path,
                    rationale,
                ),
            )
            decision_id = int(cursor.lastrowid)
            db.executemany(
                "INSERT INTO decision_snapshots(decision_id, snapshot_id) VALUES (?, ?)",
                [(decision_id, snapshot_id) for snapshot_id in snapshot_ids],
            )
        return decision_id

    def plan_intent(
        self,
        *,
        operation_id: str,
        run_id: str,
        kind: str,
        target_id: str,
        request: Any,
        is_economic: bool,
        decision_id: int,
    ) -> sqlite3.Row:
        clean_request = redact(request)
        request_digest = payload_hash(clean_request)
        with self.transaction() as db:
            existing = db.execute(
                "SELECT * FROM operation_intents WHERE operation_id=?",
                (operation_id,),
            ).fetchone()
            if existing is not None:
                identity_matches = (
                    existing["run_id"] == run_id
                    and existing["decision_id"] == decision_id
                    and existing["kind"] == kind
                    and existing["target_id"] == target_id
                    and bool(existing["is_economic"]) == bool(is_economic)
                    and existing["request_hash"] == request_digest
                )
                if not identity_matches:
                    raise IntentConflictError(
                        f"Operation {operation_id} already exists with a different identity",
                    )
                return existing
            self._validate_decision_lineage(db, decision_id=decision_id, run_id=run_id)
            stored_digest, request_path = self.store_blob(clean_request)
            if stored_digest != request_digest:
                raise FailClosedError("Canonical request hash changed while planning intent")
            db.execute(
                """
                INSERT INTO operation_intents(
                  operation_id, run_id, decision_id, kind, target_id, is_economic,
                  request_hash, request_path, state, planned_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PLANNED', ?)
                """,
                (
                    operation_id,
                    run_id,
                    decision_id,
                    kind,
                    target_id,
                    int(is_economic),
                    request_digest,
                    request_path,
                    _timestamp(),
                ),
            )
            return db.execute(
                "SELECT * FROM operation_intents WHERE operation_id=?",
                (operation_id,),
            ).fetchone()

    @staticmethod
    def _validate_decision_lineage(db: sqlite3.Connection, *, decision_id: int, run_id: str) -> None:
        if isinstance(decision_id, bool) or not isinstance(decision_id, int) or decision_id <= 0:
            raise FailClosedError("A positive decision_id is required for every operation")
        row = db.execute(
            """
            SELECT d.run_id, COUNT(ds.snapshot_id) AS snapshot_count,
                   SUM(CASE WHEN s.id IS NULL OR s.sealed_at IS NULL OR s.run_id<>d.run_id THEN 1 ELSE 0 END)
                     AS invalid_snapshot_count
            FROM decisions d
            LEFT JOIN decision_snapshots ds ON ds.decision_id=d.id
            LEFT JOIN snapshots s ON s.id=ds.snapshot_id
            WHERE d.id=?
            GROUP BY d.id, d.run_id
            """,
            (decision_id,),
        ).fetchone()
        if row is None or row["run_id"] != run_id:
            raise FailClosedError("Operation decision must belong to the same run")
        if row["snapshot_count"] < 1 or row["invalid_snapshot_count"]:
            raise FailClosedError("Operation decision must reference sealed snapshots from the same run")

    def mark_intent_sent(self, operation_id: str) -> sqlite3.Row:
        with self.transaction() as db:
            row = db.execute(
                "SELECT * FROM operation_intents WHERE operation_id=?",
                (operation_id,),
            ).fetchone()
            if row is None:
                raise FailClosedError(f"Unknown operation {operation_id}")
            if row["state"] != "PLANNED":
                raise FailClosedError(f"Operation {operation_id} cannot be sent from {row['state']}")
            self._validate_decision_lineage(
                db,
                decision_id=row["decision_id"],
                run_id=row["run_id"],
            )
            if row["is_economic"]:
                blocker = db.execute(
                    """
                    SELECT operation_id FROM operation_intents
                    WHERE is_economic=1 AND state IN ('SENT', 'UNKNOWN') AND operation_id<>?
                    LIMIT 1
                    """,
                    (operation_id,),
                ).fetchone()
                if blocker is not None:
                    raise FailClosedError(
                        f"Economic write blocked by unresolved operation {blocker['operation_id']}",
                    )
            db.execute(
                "UPDATE operation_intents SET state='SENT', sent_at=? WHERE operation_id=?",
                (_timestamp(), operation_id),
            )
            return db.execute(
                "SELECT * FROM operation_intents WHERE operation_id=?",
                (operation_id,),
            ).fetchone()

    def resolve_intent(
        self,
        operation_id: str,
        state: str,
        result: Any,
        *,
        verification: Any | None = None,
    ) -> sqlite3.Row:
        if state not in {"SUCCEEDED", "FAILED", "UNKNOWN"}:
            raise ValueError(f"Invalid intent resolution: {state}")
        clean_result = redact(result)
        result_hash, result_path = self.store_blob(clean_result)
        verification_hash = verification_path = None
        if verification is not None:
            verification_hash, verification_path = self.store_blob(redact(verification))
        with self.transaction() as db:
            row = db.execute(
                "SELECT * FROM operation_intents WHERE operation_id=?",
                (operation_id,),
            ).fetchone()
            if row is None:
                raise FailClosedError(f"Unknown operation {operation_id}")
            current = row["state"]
            if current in {"SUCCEEDED", "FAILED"}:
                if current == state:
                    return row
                raise FailClosedError(f"Terminal operation {operation_id} cannot be changed")
            if current not in {"SENT", "UNKNOWN"}:
                raise FailClosedError(f"Operation {operation_id} cannot resolve from {current}")
            db.execute(
                """
                UPDATE operation_intents
                SET state=?, result_hash=?, result_path=?, verification_hash=?,
                    verification_path=?, resolved_at=?
                WHERE operation_id=?
                """,
                (
                    state,
                    result_hash,
                    result_path,
                    verification_hash,
                    verification_path,
                    _timestamp() if state != "UNKNOWN" else None,
                    operation_id,
                ),
            )
            return db.execute(
                "SELECT * FROM operation_intents WHERE operation_id=?",
                (operation_id,),
            ).fetchone()

    def get_intent(self, operation_id: str) -> sqlite3.Row | None:
        with self._lock:
            return self.connection.execute(
                "SELECT * FROM operation_intents WHERE operation_id=?",
                (operation_id,),
            ).fetchone()

    def unresolved_intents(self, *, is_economic: bool | None = None) -> list[sqlite3.Row]:
        sql = "SELECT * FROM operation_intents WHERE state IN ('SENT', 'UNKNOWN')"
        args: tuple[Any, ...] = ()
        if is_economic is not None:
            sql += " AND is_economic=?"
            args = (int(is_economic),)
        sql += " ORDER BY planned_at, operation_id"
        with self._lock:
            return list(self.connection.execute(sql, args).fetchall())

    def record_tool_call(
        self,
        *,
        run_id: str,
        sequence_no: int,
        server: str,
        tool_name: str,
        request: Any,
        status: str,
        response: Any | None = None,
        operation_id: str | None = None,
        error_code: str | None = None,
    ) -> int:
        request_hash, request_path = self.store_blob(redact(request))
        response_hash = response_path = None
        if response is not None:
            response_hash, response_path = self.store_blob(redact(response))
        with self.transaction() as db:
            cursor = db.execute(
                """
                INSERT INTO tool_calls(
                  run_id, operation_id, sequence_no, server, tool_name, started_at,
                  finished_at, status, request_hash, request_path, response_hash,
                  response_path, error_code
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    run_id,
                    operation_id,
                    sequence_no,
                    server,
                    tool_name,
                    _timestamp(),
                    _timestamp(),
                    status,
                    request_hash,
                    request_path,
                    response_hash,
                    response_path,
                    error_code,
                ),
            )
        return int(cursor.lastrowid)

    def record_outcome(self, decision_id: int, payload: Any, score: float | None = None) -> int:
        digest, relative = self.store_blob(redact(payload))
        with self.transaction() as db:
            cursor = db.execute(
                """
                INSERT INTO outcomes(decision_id, observed_at, payload_hash, payload_path, score)
                VALUES (?, ?, ?, ?, ?)
                """,
                (decision_id, _timestamp(), digest, relative, score),
            )
        return int(cursor.lastrowid)

    def recent_decisions(
        self,
        *,
        limit: int = 20,
        subject_type: str | None = None,
        subject_id: str | None = None,
    ) -> list[dict[str, Any]]:
        if not 1 <= limit <= 100:
            raise ValueError("limit must be in [1, 100]")
        clauses: list[str] = []
        args: list[Any] = []
        if subject_type is not None:
            clauses.append("d.subject_type=?")
            args.append(subject_type)
        if subject_id is not None:
            clauses.append("d.subject_id=?")
            args.append(subject_id)
        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        args.append(limit)
        with self._lock:
            rows = self.connection.execute(
                f"""
                SELECT d.*,
                  o.id AS outcome_id, o.observed_at AS outcome_observed_at,
                  o.payload_hash AS outcome_hash, o.payload_path AS outcome_path, o.score AS outcome_score
                FROM decisions d
                LEFT JOIN outcomes o ON o.id = (
                  SELECT latest.id FROM outcomes latest
                  WHERE latest.decision_id=d.id ORDER BY latest.observed_at DESC, latest.id DESC LIMIT 1
                )
                {where}
                ORDER BY d.decided_at DESC, d.id DESC
                LIMIT ?
                """,
                tuple(args),
            ).fetchall()
            result: list[dict[str, Any]] = []
            for row in rows:
                item = {
                    "decisionId": row["id"],
                    "runId": row["run_id"],
                    "decisionType": row["decision_type"],
                    "subjectType": row["subject_type"],
                    "subjectId": row["subject_id"],
                    "decidedAt": row["decided_at"],
                    "asOf": row["as_of"],
                    "strategyVersion": row["strategy_version"],
                    "alternatives": self.load_blob(row["alternatives_hash"], row["alternatives_path"]),
                    "choice": self.load_blob(row["choice_hash"], row["choice_path"]),
                    "rationale": row["rationale"],
                    "outcome": None,
                }
                if row["outcome_id"] is not None:
                    item["outcome"] = {
                        "observedAt": row["outcome_observed_at"],
                        "payload": self.load_blob(row["outcome_hash"], row["outcome_path"]),
                        "score": row["outcome_score"],
                    }
                result.append(item)
            return result

    def record_intervention(self, reason: str, details: Any, run_id: str | None = None) -> int:
        digest, relative = self.store_blob(redact(details))
        with self.transaction() as db:
            cursor = db.execute(
                """
                INSERT INTO interventions(run_id, occurred_at, reason, details_hash, details_path)
                VALUES (?, ?, ?, ?, ?)
                """,
                (run_id, _timestamp(), reason, digest, relative),
            )
        return int(cursor.lastrowid)


def _timestamp() -> str:
    return dt.datetime.now(UTC).isoformat()


def _aware_utc(value: dt.datetime) -> dt.datetime:
    if value.tzinfo is None:
        raise ValueError("timestamps must be timezone-aware")
    return value.astimezone(UTC)


def _default_migrations_path() -> Path:
    source_tree = Path(__file__).resolve().parents[3] / "migrations"
    if source_tree.is_dir():
        return source_tree
    packaged = Path(__file__).resolve().parents[1] / "migrations"
    return packaged
