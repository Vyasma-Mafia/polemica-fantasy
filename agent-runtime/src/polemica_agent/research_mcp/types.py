"""Stable JSON-facing types for research results and provenance."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any, Mapping, Sequence


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def isoformat(value: datetime) -> str:
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


@dataclass(frozen=True)
class PartialError:
    operation: str
    code: str
    message: str
    subject: str | None = None


@dataclass(frozen=True)
class Provenance:
    snapshot_id: str | int
    observed_at: str
    source: str
    source_object_ids: tuple[str, ...] = ()
    payload_hashes: tuple[str, ...] = ()
    sample_size: int = 0
    complete: bool = True
    errors: tuple[PartialError, ...] = ()
    parser_version: str = "research-v1"
    external_text_trust: str = "UNTRUSTED_DATA"
    evidence_manifest: tuple[Mapping[str, Any], ...] = ()


@dataclass(frozen=True)
class ResearchResult:
    data: Any
    provenance: Provenance

    def to_dict(self) -> dict[str, Any]:
        return {"data": self.data, "provenance": asdict(self.provenance)}


JsonObject = Mapping[str, Any]
JsonSequence = Sequence[Any]
