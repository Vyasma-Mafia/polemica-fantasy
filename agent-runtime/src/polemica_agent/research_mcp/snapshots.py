"""COLLECT -> SEAL coordination independent of the shared memory schema."""

from __future__ import annotations

import threading
import uuid
from dataclasses import dataclass, replace
from datetime import datetime
from typing import Callable, Protocol, Sequence

from .cache import RawPayloadRecord
from .errors import ContractError, SnapshotSealedError
from .types import isoformat, utc_now
from .types import PartialError


@dataclass(frozen=True)
class Snapshot:
    snapshot_id: str | int
    run_id: str
    state: str
    created_at: str
    as_of: str | None = None
    records: tuple[RawPayloadRecord, ...] = ()
    completeness: str = "COMPLETE"
    error_count: int = 0
    collection_id: str | None = None


class SnapshotJournal(Protocol):
    """Adapter implemented by Memory MCP without coupling to its storage schema."""

    def create_collecting(self, run_id: str, snapshot_id: str, created_at: str) -> Snapshot: ...
    def get(self, snapshot_id: str) -> Snapshot: ...
    def attach(self, snapshot_id: str, record: RawPayloadRecord) -> Snapshot: ...
    def seal(self, snapshot_id: str, as_of: str) -> Snapshot: ...
    def observe_result(
        self, snapshot_id: str, *, complete: bool, sample_size: int,
        errors: Sequence[PartialError],
    ) -> None: ...


class InMemorySnapshotJournal:
    """Deterministic reference adapter used by tests and local fixtures."""

    def __init__(self) -> None:
        self._items: dict[str, Snapshot] = {}
        self._lock = threading.RLock()

    def create_collecting(self, run_id: str, snapshot_id: str, created_at: str) -> Snapshot:
        with self._lock:
            if snapshot_id in self._items:
                raise ContractError("snapshot_id already exists")
            item = Snapshot(snapshot_id, run_id, "COLLECTING", created_at)
            self._items[snapshot_id] = item
            return item

    def get(self, snapshot_id: str) -> Snapshot:
        with self._lock:
            try:
                return self._items[snapshot_id]
            except KeyError:
                raise ContractError("snapshot not found") from None

    def attach(self, snapshot_id: str, record: RawPayloadRecord) -> Snapshot:
        with self._lock:
            current = self.get(snapshot_id)
            if current.state != "COLLECTING":
                raise SnapshotSealedError("sealed snapshot cannot accept payloads")
            if any(existing.payload_hash == record.payload_hash for existing in current.records):
                return current
            updated = replace(current, records=current.records + (record,))
            self._items[snapshot_id] = updated
            return updated

    def seal(self, snapshot_id: str, as_of: str) -> Snapshot:
        with self._lock:
            current = self.get(snapshot_id)
            if current.state == "SEALED":
                if current.as_of != as_of:
                    raise ContractError("snapshot is already sealed with a different as_of")
                return current
            updated = replace(current, state="SEALED", as_of=as_of)
            self._items[snapshot_id] = updated
            return updated

    def observe_result(
        self, snapshot_id: str, *, complete: bool, sample_size: int,
        errors: Sequence[PartialError],
    ) -> None:
        del sample_size
        with self._lock:
            current = self.get(snapshot_id)
            if current.state != "COLLECTING":
                raise SnapshotSealedError("result cannot mutate sealed snapshot")
            if not complete or errors:
                self._items[snapshot_id] = replace(
                    current, completeness="PARTIAL",
                    error_count=current.error_count + max(1, len(errors)),
                )


class SnapshotCoordinator:
    def __init__(self, journal: SnapshotJournal, *, clock: Callable[[], datetime] = utc_now) -> None:
        self.journal = journal
        self.clock = clock

    def begin(self, run_id: str, *, snapshot_id: str | None = None) -> Snapshot:
        _uuid(run_id, "run_id")
        sid = snapshot_id or str(uuid.uuid4())
        _uuid(sid, "snapshot_id")
        return self.journal.create_collecting(run_id, sid, isoformat(self.clock()))

    def require_collecting(self, snapshot_id: str) -> Snapshot:
        _uuid(snapshot_id, "snapshot_id")
        snapshot = self.journal.get(snapshot_id)
        if snapshot.state != "COLLECTING":
            raise SnapshotSealedError("research fetch is forbidden after snapshot seal")
        return snapshot

    def attach(self, snapshot_id: str, record: RawPayloadRecord) -> Snapshot:
        self.require_collecting(snapshot_id)
        # The journal must check state atomically too; seal may race this call.
        return self.journal.attach(snapshot_id, record)

    def observe_result(
        self, snapshot_id: str, *, complete: bool, sample_size: int,
        errors: Sequence[PartialError],
    ) -> None:
        self.require_collecting(snapshot_id)
        self.journal.observe_result(
            snapshot_id, complete=complete, sample_size=sample_size, errors=errors
        )

    def seal(self, snapshot_id: str) -> Snapshot:
        self.require_collecting(snapshot_id)
        return self.journal.seal(snapshot_id, isoformat(self.clock()))


def _uuid(value: str, name: str) -> None:
    try:
        uuid.UUID(value)
    except (ValueError, AttributeError, TypeError):
        raise ContractError(f"{name} must be a UUID") from None
