from __future__ import annotations

import json
import os
import sqlite3
import hashlib
import uuid
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator


SCHEMA_VERSION = 2


def utcnow() -> str:
    return datetime.now(timezone.utc).isoformat()


class Inbox:
    def __init__(self, path: Path):
        self.path = path
        self.connection = sqlite3.connect(path, timeout=5, isolation_level=None)
        self.connection.row_factory = sqlite3.Row
        self.connection.execute("PRAGMA journal_mode=WAL")
        self.connection.execute("PRAGMA synchronous=FULL")
        self.connection.execute("PRAGMA foreign_keys=ON")
        self.connection.execute("PRAGMA busy_timeout=5000")
        self._migrate()
        self.secure_files()

    def close(self) -> None:
        self.connection.close()
        self.secure_files()

    def secure_files(self) -> None:
        for suffix in ("", "-wal", "-shm"):
            candidate = Path(f"{self.path}{suffix}")
            if candidate.exists():
                os.chmod(candidate, 0o600)

    def _migrate(self) -> None:
        version = self.connection.execute("PRAGMA user_version").fetchone()[0]
        legacy = self.connection.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='schema_meta'").fetchone()
        if version == 0 and legacy:
            raise RuntimeError("Legacy shadow inbox schema detected; preserve it for audit and start with a new shadow-inbox.sqlite")
        if version > SCHEMA_VERSION:
            raise RuntimeError(f"Unsupported shadow inbox schema version: {version}")
        if version == 0:
            self.connection.execute("BEGIN EXCLUSIVE")
            try:
                statements = (
                    "CREATE TABLE messages(channel_peer_id INTEGER NOT NULL,message_id INTEGER NOT NULL,latest_revision INTEGER NOT NULL,latest_source_version TEXT NOT NULL,classification TEXT NOT NULL,observed_at TEXT NOT NULL,PRIMARY KEY(channel_peer_id,message_id))",
                    "CREATE TABLE revisions(id INTEGER PRIMARY KEY AUTOINCREMENT,channel_peer_id INTEGER NOT NULL,message_id INTEGER NOT NULL,revision_no INTEGER NOT NULL,fingerprint TEXT NOT NULL,source_version TEXT NOT NULL,message_date TEXT,edit_date TEXT,grouped_id INTEGER,media_kind TEXT,media_id TEXT,text TEXT NOT NULL,classification TEXT NOT NULL,league TEXT,reason TEXT NOT NULL,is_latest INTEGER NOT NULL,observed_at TEXT NOT NULL,UNIQUE(channel_peer_id,message_id,revision_no),UNIQUE(channel_peer_id,message_id,fingerprint),FOREIGN KEY(channel_peer_id,message_id) REFERENCES messages(channel_peer_id,message_id))",
                    "CREATE TABLE outbox(id INTEGER PRIMARY KEY AUTOINCREMENT,event_key TEXT NOT NULL UNIQUE,channel_peer_id INTEGER NOT NULL,message_id INTEGER NOT NULL,revision_no INTEGER NOT NULL,event_type TEXT NOT NULL,payload TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'PENDING',attempts INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL,available_at TEXT NOT NULL,lease_until TEXT,delivered_at TEXT,last_error TEXT,FOREIGN KEY(channel_peer_id,message_id) REFERENCES messages(channel_peer_id,message_id))",
                    "CREATE TABLE state(key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at TEXT NOT NULL)",
                    "PRAGMA user_version=1",
                )
                for statement in statements:
                    self.connection.execute(statement)
                self.connection.execute("COMMIT")
            except BaseException:
                self.connection.execute("ROLLBACK")
                raise
            version = 1
        if version == 1:
            self.connection.execute("BEGIN EXCLUSIVE")
            try:
                self.connection.execute(
                    "CREATE TABLE backend_outbox(id INTEGER PRIMARY KEY AUTOINCREMENT,event_key TEXT NOT NULL UNIQUE,delivery_id TEXT NOT NULL UNIQUE,payload TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'PENDING',attempts INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL,available_at TEXT NOT NULL,lease_until TEXT,delivered_at TEXT,last_error TEXT)"
                )
                self.connection.execute(f"PRAGMA user_version={SCHEMA_VERSION}")
                self.connection.execute("COMMIT")
            except BaseException:
                self.connection.execute("ROLLBACK")
                raise
        version = self.connection.execute("PRAGMA user_version").fetchone()[0]
        if version != SCHEMA_VERSION:
            raise RuntimeError(f"Unsupported shadow inbox schema version: {version}")
        violations = self.connection.execute("PRAGMA foreign_key_check").fetchall()
        if violations:
            raise RuntimeError("Shadow inbox contains foreign-key violations")

    @contextmanager
    def transaction(self) -> Iterator[sqlite3.Connection]:
        self.connection.execute("BEGIN IMMEDIATE")
        try:
            yield self.connection
            self.connection.execute("COMMIT")
        except BaseException:
            self.connection.execute("ROLLBACK")
            raise
        finally:
            self.secure_files()

    def get_state(self, key: str) -> str | None:
        row = self.connection.execute("SELECT value FROM state WHERE key=?", (key,)).fetchone()
        return row[0] if row else None

    def set_state(self, db: sqlite3.Connection, key: str, value: object) -> None:
        db.execute("INSERT INTO state(key,value,updated_at) VALUES(?,?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=excluded.updated_at", (key, str(value), utcnow()))

    def saved_message_ids(self, peer_id: int, before_id: int | None, limit: int) -> list[int]:
        if before_id is None:
            rows = self.connection.execute("SELECT message_id FROM messages WHERE channel_peer_id=? ORDER BY message_id DESC LIMIT ?", (peer_id,limit)).fetchall()
        else:
            rows = self.connection.execute("SELECT message_id FROM messages WHERE channel_peer_id=? AND message_id<? ORDER BY message_id DESC LIMIT ?", (peer_id,before_id,limit)).fetchall()
        return [int(row[0]) for row in rows]

    def latest_classification(self, peer_id: int, message_id: int) -> str | None:
        row = self.connection.execute("SELECT classification FROM messages WHERE channel_peer_id=? AND message_id=?", (peer_id,message_id)).fetchone()
        return str(row[0]) if row else None

    def inspect_summary(self) -> dict[str, object]:
        classifications = {str(row[0]): int(row[1]) for row in self.connection.execute("SELECT classification,COUNT(*) FROM messages GROUP BY classification")}
        outbox = {str(row[0]): int(row[1]) for row in self.connection.execute("SELECT status,COUNT(*) FROM outbox GROUP BY status")}
        identity_pinned = bool(
            self.get_state("pinned_channel_peer_id")
            and self.get_state("pinned_account_user_id")
        )
        return {
            "mode": self.get_state("mode"),
            "identityPinned": identity_pinned,
            "messagesByClassification": classifications,
            "revisionCount": self.connection.execute("SELECT COUNT(*) FROM revisions").fetchone()[0],
            "outboxByStatus": outbox,
            **self.quick_health(),
        }

    def persist(self, *, peer_id: int, message_id: int, source_version: str,
                message_date: str | None, edit_date: str | None, grouped_id: int | None,
                media_kind: str | None, media_id: str | None, text: str, fingerprint: str,
                classification: str, league: str | None, reason: str, silent: bool,
                watermark: int | None = None, backend_delivery: bool = False) -> str:
        with self.transaction() as db:
            previous = db.execute("SELECT latest_revision,latest_source_version,classification FROM messages WHERE channel_peer_id=? AND message_id=?", (peer_id,message_id)).fetchone()
            duplicate = db.execute("SELECT revision_no FROM revisions WHERE channel_peer_id=? AND message_id=? AND fingerprint=?", (peer_id,message_id,fingerprint)).fetchone()
            if duplicate:
                if previous is not None and source_version > previous["latest_source_version"]:
                    db.execute("UPDATE messages SET latest_source_version=?,observed_at=? WHERE channel_peer_id=? AND message_id=?", (source_version,utcnow(),peer_id,message_id))
                if watermark is not None:
                    self.set_state(db, "watermark", watermark)
                return "DUPLICATE"
            revision_no = db.execute("SELECT COALESCE(MAX(revision_no),0)+1 FROM revisions WHERE channel_peer_id=? AND message_id=?", (peer_id,message_id)).fetchone()[0]
            # Telegram timestamps have finite precision; for equal timestamps the
            # last observation wins, while a strictly older version is audit-only.
            stale = previous is not None and source_version < previous["latest_source_version"]
            if previous is None:
                db.execute("INSERT INTO messages(channel_peer_id,message_id,latest_revision,latest_source_version,classification,observed_at) VALUES(?,?,?,?,?,?)", (peer_id,message_id,0,"", "IGNORE",utcnow()))
            db.execute("INSERT INTO revisions(channel_peer_id,message_id,revision_no,fingerprint,source_version,message_date,edit_date,grouped_id,media_kind,media_id,text,classification,league,reason,is_latest,observed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (peer_id,message_id,revision_no,fingerprint,source_version,message_date,edit_date,grouped_id,media_kind,media_id,text,classification,league,reason,0 if stale else 1,utcnow()))
            if stale:
                if watermark is not None:
                    self.set_state(db, "watermark", watermark)
                return "STALE"
            db.execute("UPDATE revisions SET is_latest=0 WHERE channel_peer_id=? AND message_id=? AND revision_no<>?", (peer_id,message_id,revision_no))
            db.execute("UPDATE messages SET latest_revision=?,latest_source_version=?,classification=?,observed_at=? WHERE channel_peer_id=? AND message_id=?", (revision_no,source_version,classification,utcnow(),peer_id,message_id))
            event_type = None
            if not silent:
                delivered_before = db.execute("SELECT 1 FROM outbox WHERE channel_peer_id=? AND message_id=? AND status='DELIVERED' LIMIT 1", (peer_id,message_id)).fetchone() is not None
                db.execute("UPDATE outbox SET status='SUPERSEDED' WHERE channel_peer_id=? AND message_id=? AND status='PENDING'", (peer_id,message_id))
                if not delivered_before and classification != "IGNORE":
                    event_type = "CANDIDATE"
                elif delivered_before and previous and previous["classification"] != "IGNORE" and classification == "IGNORE":
                    event_type = "RETRACTED"
                elif delivered_before and previous and classification != "IGNORE":
                    event_type = "REVISED"
            if event_type and not backend_delivery:
                event_key = f"tg:{peer_id}:{message_id}:r{revision_no}:{event_type}"
                payload = json.dumps({"eventKey":event_key,"eventType":event_type,"classification":classification,"league":league,"messageId":message_id,"revision":revision_no,"sourceUrl":f"https://t.me/polemica_closed_league/{message_id}"}, ensure_ascii=False, sort_keys=True)
                created = utcnow()
                db.execute("INSERT INTO outbox(event_key,channel_peer_id,message_id,revision_no,event_type,payload,created_at,available_at) VALUES(?,?,?,?,?,?,?,?)", (event_key,peer_id,message_id,revision_no,event_type,payload,created,created))
            if backend_delivery and not silent and (classification in ("ANNOUNCEMENT", "RESULT") or (previous and previous["classification"] in ("ANNOUNCEMENT", "RESULT"))):
                backend_event_key = f"tg:{peer_id}:{message_id}:r{revision_no}"
                raw_hash = hashlib.sha256(text.encode("utf-8")).hexdigest()
                posted_at = message_date or edit_date or utcnow()
                backend_payload = json.dumps({
                    "sourceChannelPeerId": peer_id,
                    "messageId": message_id,
                    "revision": revision_no,
                    "sourceVersion": source_version,
                    "postedAt": posted_at,
                    "editedAt": edit_date,
                    "contentHash": raw_hash,
                    "rawText": text,
                    "classification": classification,
                    "league": league,
                }, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
                created = utcnow()
                db.execute(
                    "INSERT INTO backend_outbox(event_key,delivery_id,payload,created_at,available_at) VALUES(?,?,?,?,?) ON CONFLICT(event_key) DO NOTHING",
                    (backend_event_key, str(uuid.uuid4()), backend_payload, created, created),
                )
            if watermark is not None:
                self.set_state(db, "watermark", watermark)
            return event_type or "STORED"

    def lease_outbox(self, seconds: int = 60) -> sqlite3.Row | None:
        with self.transaction() as db:
            now = utcnow()
            row = db.execute("SELECT * FROM outbox WHERE status='PENDING' AND available_at<=? AND (lease_until IS NULL OR lease_until<=?) ORDER BY id LIMIT 1", (now,now)).fetchone()
            if not row:
                return None
            lease = datetime.fromtimestamp(datetime.now(timezone.utc).timestamp()+seconds, timezone.utc).isoformat()
            db.execute("UPDATE outbox SET lease_until=?,attempts=attempts+1 WHERE id=?", (lease,row["id"]))
            return db.execute("SELECT * FROM outbox WHERE id=?", (row["id"],)).fetchone()

    def finish_outbox(self, outbox_id: int, *, delivered: bool, terminal: bool=False, error: str|None=None, retry_at: str|None=None) -> None:
        with self.transaction() as db:
            if delivered:
                db.execute("UPDATE outbox SET status='DELIVERED',delivered_at=?,lease_until=NULL,last_error=NULL WHERE id=?", (utcnow(),outbox_id))
            elif terminal:
                db.execute("UPDATE outbox SET status='FAILED',lease_until=NULL,last_error=? WHERE id=?", (error,outbox_id))
            else:
                db.execute("UPDATE outbox SET lease_until=NULL,available_at=?,last_error=? WHERE id=?", (retry_at or utcnow(),error,outbox_id))

    def lease_backend_outbox(self, seconds: int = 60) -> sqlite3.Row | None:
        with self.transaction() as db:
            now = utcnow()
            row = db.execute("SELECT * FROM backend_outbox WHERE status='PENDING' AND available_at<=? AND (lease_until IS NULL OR lease_until<=?) ORDER BY id LIMIT 1", (now,now)).fetchone()
            if not row:
                return None
            lease = datetime.fromtimestamp(datetime.now(timezone.utc).timestamp()+seconds, timezone.utc).isoformat()
            db.execute("UPDATE backend_outbox SET lease_until=?,attempts=attempts+1 WHERE id=?", (lease,row["id"]))
            return db.execute("SELECT * FROM backend_outbox WHERE id=?", (row["id"],)).fetchone()

    def finish_backend_outbox(self, outbox_id: int, *, delivered: bool, terminal: bool=False, error: str|None=None, retry_at: str|None=None) -> None:
        with self.transaction() as db:
            if delivered:
                db.execute("UPDATE backend_outbox SET status='DELIVERED',delivered_at=?,lease_until=NULL,last_error=NULL WHERE id=?", (utcnow(),outbox_id))
            elif terminal:
                db.execute("UPDATE backend_outbox SET status='FAILED',lease_until=NULL,last_error=? WHERE id=?", (error,outbox_id))
            else:
                db.execute("UPDATE backend_outbox SET lease_until=NULL,available_at=?,last_error=? WHERE id=?", (retry_at or utcnow(),error,outbox_id))

    def quick_health(self) -> dict[str, object]:
        check = self.connection.execute("PRAGMA quick_check").fetchone()[0]
        version = self.connection.execute("PRAGMA user_version").fetchone()[0]
        pending = self.connection.execute("SELECT COUNT(*),MIN(created_at) FROM outbox WHERE status='PENDING'").fetchone()
        failed = self.connection.execute("SELECT COUNT(*) FROM outbox WHERE status='FAILED'").fetchone()[0]
        backend_pending = self.connection.execute("SELECT COUNT(*),MIN(created_at) FROM backend_outbox WHERE status='PENDING'").fetchone()
        backend_failed = self.connection.execute("SELECT COUNT(*) FROM backend_outbox WHERE status='FAILED'").fetchone()[0]
        return {"quickCheck":check,"schemaVersion":version,"pendingOutbox":pending[0],"oldestPendingAt":pending[1],"failedOutbox":failed,"pendingBackendOutbox":backend_pending[0],"oldestBackendPendingAt":backend_pending[1],"failedBackendOutbox":backend_failed,"lastPollAt":self.get_state("last_poll_at"),"heartbeatAt":self.get_state("heartbeat_at"),"floodWaitUntil":self.get_state("flood_wait_until"),"watermark":self.get_state("watermark")}
