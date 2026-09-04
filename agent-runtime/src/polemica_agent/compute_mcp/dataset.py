from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path
from typing import Any, Mapping, Sequence

from polemica_agent.common.canonical import payload_hash
from polemica_agent.common.storage import FailClosedError


MAX_INPUT_BYTES = 8 * 1024 * 1024
MAX_ROWS = 20_000
ROLE_CODES = {"don": 0, "mafia": 1, "civilian": 2, "peace": 2, "sheriff": 3}
WIN_CODES = {"win", "success", "victory"}
LOSS_CODES = {"fail", "loss", "defeat"}


class DatasetError(FailClosedError):
    def __init__(self, code: str) -> None:
        super().__init__(code)
        self.code = code


def load_player_game_rows(
    records: Sequence[Mapping[str, Any]],
    *,
    research_cache: Path,
    player_ids: Sequence[int],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], str]:
    requested = _player_ids(player_ids)
    root = research_cache.resolve(strict=True)
    rows_by_identity: dict[tuple[int, int], dict[str, Any]] = {}
    used: list[dict[str, Any]] = []
    total_bytes = 0
    candidates = sorted(
        (record for record in records if record.get("source") == "profile-games-page"),
        key=_record_order,
    )
    for record in candidates:
        identity = _record_player_id(record.get("object_id"))
        if identity not in requested:
            continue
        raw, path = _verified_blob(record, root)
        total_bytes += len(raw)
        if total_bytes > MAX_INPUT_BYTES:
            raise DatasetError("COMPUTE_INPUT_TOO_LARGE")
        try:
            payload = json.loads(raw)
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise DatasetError("COMPUTE_INPUT_INVALID_JSON") from None
        source_rows = payload.get("rows") if isinstance(payload, Mapping) else None
        if not isinstance(source_rows, list):
            raise DatasetError("COMPUTE_DATASET_SCHEMA_MISMATCH")
        for source_row in source_rows:
            normalized = _normalize_row(identity, source_row)
            if normalized is None:
                continue
            key = (identity, normalized["gameId"])
            existing = rows_by_identity.get(key)
            if existing is not None and existing != normalized:
                raise DatasetError("COMPUTE_DUPLICATE_GAME_CONFLICT")
            rows_by_identity.setdefault(key, normalized)
            if len(rows_by_identity) > MAX_ROWS:
                raise DatasetError("COMPUTE_ROW_LIMIT")
        used.append({
            "source": "profile-games-page",
            "object_id": str(record["object_id"]),
            "source_version": record.get("source_version"),
            "parser_version": str(record["parser_version"]),
            "payload_hash": str(record["payload_hash"]),
            "byte_count": len(raw),
        })
        del path
    missing = requested.difference(row[0] for row in rows_by_identity)
    if missing:
        raise DatasetError("COMPUTE_PLAYER_DATA_MISSING")
    rows = [rows_by_identity[key] for key in sorted(rows_by_identity)]
    used.sort(key=lambda item: (item["source"], item["object_id"], item["payload_hash"]))
    input_hash = payload_hash(rows)
    return rows, used, input_hash


def _verified_blob(record: Mapping[str, Any], root: Path) -> tuple[bytes, Path]:
    try:
        path = Path(str(record["blob_path"]))
        expected = str(record["payload_hash"])
    except KeyError:
        raise DatasetError("COMPUTE_MANIFEST_INVALID") from None
    if not path.is_absolute() or path.is_symlink():
        raise DatasetError("COMPUTE_BLOB_PATH_INVALID")
    try:
        resolved = path.resolve(strict=True)
        resolved.relative_to(root)
    except (OSError, ValueError):
        raise DatasetError("COMPUTE_BLOB_PATH_INVALID") from None
    if not resolved.is_file():
        raise DatasetError("COMPUTE_BLOB_PATH_INVALID")
    try:
        raw = resolved.read_bytes()
    except OSError:
        raise DatasetError("COMPUTE_BLOB_UNAVAILABLE") from None
    if hashlib.sha256(raw).hexdigest() != expected:
        raise DatasetError("COMPUTE_BLOB_HASH_MISMATCH")
    return raw, resolved


def _normalize_row(player_id: int, value: Any) -> dict[str, Any] | None:
    if not isinstance(value, Mapping):
        return None
    game_id = value.get("id")
    if isinstance(game_id, bool) or not isinstance(game_id, int) or game_id <= 0:
        return None
    result = value.get("result")
    result_code = result.get("code") if isinstance(result, Mapping) else result
    win = None
    if isinstance(result_code, str):
        lowered = result_code.lower()
        if lowered in WIN_CODES:
            win = 1
        elif lowered in LOSS_CODES:
            win = 0
    role = value.get("role")
    role_type = role.get("type") if isinstance(role, Mapping) else role
    role_code = ROLE_CODES.get(role_type.lower()) if isinstance(role_type, str) else None
    return {
        "playerId": player_id,
        "gameId": game_id,
        "points": _number(value.get("points")),
        "mmr": _mmr(value.get("mmr")),
        "win": win,
        "roleCode": role_code,
    }


def _number(value: Any) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    converted = float(value)
    return converted if math.isfinite(converted) else None


def _mmr(value: Any) -> float | None:
    if isinstance(value, Mapping):
        value = value.get("value")
    return _number(value)


def _record_player_id(value: Any) -> int | None:
    if not isinstance(value, str):
        return None
    parts = value.split(":")
    if len(parts) != 3 or any(not part.isdigit() for part in parts):
        return None
    player_id = int(parts[0])
    return player_id if player_id > 0 else None


def _record_order(value: Mapping[str, Any]) -> tuple[str, str, str, str]:
    return (
        str(value.get("source", "")),
        str(value.get("object_id", "")),
        "" if value.get("source_version") is None else str(value.get("source_version")),
        str(value.get("payload_hash", "")),
    )


def _player_ids(values: Sequence[int]) -> set[int]:
    if not 1 <= len(values) <= 100:
        raise DatasetError("COMPUTE_PLAYER_LIMIT")
    if any(isinstance(value, bool) or not isinstance(value, int) or value <= 0 for value in values):
        raise DatasetError("COMPUTE_PLAYER_ID_INVALID")
    result = set(values)
    if len(result) != len(values):
        raise DatasetError("COMPUTE_PLAYER_ID_DUPLICATE")
    return result
