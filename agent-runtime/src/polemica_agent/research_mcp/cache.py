"""Immutable content-addressed cache for raw Polemica payloads."""

from __future__ import annotations

import hashlib
import json
import os
import tempfile
import threading
from dataclasses import asdict, dataclass, replace
from datetime import datetime
from pathlib import Path
from typing import Any, Callable

from .errors import ContractError
from .types import isoformat, utc_now


def canonical_json_bytes(payload: Any) -> bytes:
    return json.dumps(
        payload,
        ensure_ascii=False,
        allow_nan=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


@dataclass(frozen=True)
class RawPayloadRecord:
    source: str
    object_id: str
    source_version: str | None
    payload_hash: str
    blob_path: str
    first_seen_at: str
    fetched_at: str
    parser_version: str
    correction_index: int = 1


class RawPayloadCache:
    """Append-only filesystem cache.

    A correction with the same source id/version but different content creates a
    second record. Existing blobs and metadata are never overwritten.
    """

    def __init__(
        self,
        root: str | Path,
        *,
        clock: Callable[[], datetime] = utc_now,
        parser_version: str = "research-v1",
    ) -> None:
        self.root = Path(root)
        self.clock = clock
        self.parser_version = parser_version
        self._lock = threading.RLock()
        (self.root / "blobs").mkdir(parents=True, exist_ok=True)
        (self.root / "records").mkdir(parents=True, exist_ok=True)
        os.chmod(self.root / "blobs", 0o700)
        os.chmod(self.root / "records", 0o700)

    def store(
        self,
        *,
        source: str,
        object_id: str,
        payload: Any,
        source_version: str | int | None = None,
    ) -> RawPayloadRecord:
        source = _bounded_label(source, "source")
        object_id = _bounded_label(object_id, "object_id")
        version = None if source_version is None else _bounded_label(str(source_version), "source_version")
        raw = canonical_json_bytes(payload)
        digest = hashlib.sha256(raw).hexdigest()
        identity = hashlib.sha256(f"{source}\0{object_id}\0{version or ''}".encode()).hexdigest()
        blob = self.root / "blobs" / f"{digest}.json"
        record_path = self.root / "records" / identity / f"{digest}.json"
        now = isoformat(self.clock())

        with self._lock:
            if record_path.exists():
                record = _load_record(record_path)
                _verify_record_location(self.root, record)
                _verify_blob(blob, record.payload_hash)
                if record.payload_hash != digest:
                    raise ContractError("cached record identity has an invalid payload hash")
                return replace(record, fetched_at=now)
            existing_versions = self.versions(
                source=source, object_id=object_id, source_version=version
            )
            _write_once(blob, raw, expected_hash=digest)
            record = RawPayloadRecord(
                source=source,
                object_id=object_id,
                source_version=version,
                payload_hash=digest,
                blob_path=str(blob),
                first_seen_at=now,
                fetched_at=now,
                parser_version=self.parser_version,
                correction_index=len(existing_versions) + 1,
            )
            record_path.parent.mkdir(parents=True, exist_ok=True)
            os.chmod(record_path.parent, 0o700)
            _write_once(record_path, canonical_json_bytes(asdict(record)))
            return record

    def load(self, payload_hash: str) -> Any:
        if len(payload_hash) != 64 or any(c not in "0123456789abcdef" for c in payload_hash):
            raise ContractError("payload_hash must be lowercase SHA-256")
        path = self.root / "blobs" / f"{payload_hash}.json"
        raw = _verify_blob(path, payload_hash)
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            raise ContractError("cached payload is not valid JSON") from None

    def versions(self, *, source: str, object_id: str, source_version: str | int | None = None) -> list[RawPayloadRecord]:
        version = None if source_version is None else str(source_version)
        identity = hashlib.sha256(f"{source}\0{object_id}\0{version or ''}".encode()).hexdigest()
        directory = self.root / "records" / identity
        if not directory.exists():
            return []
        records = [_load_record(path) for path in sorted(directory.glob("*.json"))]
        for record in records:
            _verify_record_location(self.root, record)
            _verify_blob(Path(record.blob_path), record.payload_hash)
        return sorted(records, key=lambda record: (record.correction_index, record.payload_hash))


def _bounded_label(value: str, name: str) -> str:
    if not value or len(value) > 256 or "\x00" in value:
        raise ContractError(f"{name} must contain 1..256 non-NUL characters")
    return value


def _write_once(path: Path, data: bytes, *, expected_hash: str | None = None) -> None:
    if path.exists():
        existing = path.read_bytes()
        if existing != data:
            raise ContractError("immutable cache path contains different bytes")
        if expected_hash is not None and hashlib.sha256(existing).hexdigest() != expected_hash:
            raise ContractError("immutable cache hash mismatch")
        return
    fd, temporary_name = tempfile.mkstemp(prefix=".cache-", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, 0o600)
        # A single Research broker owns the cache. Atomic rename makes a partial
        # final file impossible; the content-addressed destination is verified below.
        os.replace(temporary, path)
        directory_fd = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
    except BaseException:
        raise
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass
    if expected_hash is not None:
        _verify_blob(path, expected_hash)


def _verify_blob(path: Path, expected_hash: str) -> bytes:
    try:
        raw = path.read_bytes()
    except OSError:
        raise ContractError("payload is not present in raw cache") from None
    if hashlib.sha256(raw).hexdigest() != expected_hash:
        raise ContractError("cached payload hash mismatch")
    return raw


def _load_record(path: Path) -> RawPayloadRecord:
    try:
        raw = path.read_bytes()
        value = json.loads(raw)
        if isinstance(value, dict):
            value.setdefault("correction_index", 1)
        record = RawPayloadRecord(**value)
    except (OSError, json.JSONDecodeError, TypeError):
        raise ContractError("cached provenance record is invalid") from None
    if path.stem != record.payload_hash:
        raise ContractError("cached provenance filename/hash mismatch")
    return record


def _verify_record_location(root: Path, record: RawPayloadRecord) -> None:
    expected = (root / "blobs" / f"{record.payload_hash}.json").resolve()
    if Path(record.blob_path).resolve() != expected:
        raise ContractError("cached provenance blob path escapes the cache")
