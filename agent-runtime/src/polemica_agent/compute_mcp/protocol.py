"""Length-prefixed canonical JSON protocol used by the isolated worker."""

from __future__ import annotations

import json
import struct
from collections.abc import Mapping
from typing import Any, BinaryIO


REQUEST_LIMIT = 8 * 1024 * 1024
RESPONSE_LIMIT = 1024 * 1024
_HEADER_SIZE = 4


class ProtocolError(ValueError):
    def __init__(self, code: str) -> None:
        self.code = code
        super().__init__(code)


def read_request(stream: BinaryIO) -> dict[str, Any]:
    return _read_message(stream, REQUEST_LIMIT)


def write_request(stream: BinaryIO, value: Mapping[str, Any]) -> None:
    _write_message(stream, value, REQUEST_LIMIT)


def read_response(stream: BinaryIO) -> dict[str, Any]:
    return _read_message(stream, RESPONSE_LIMIT)


def write_response(stream: BinaryIO, value: Mapping[str, Any]) -> None:
    _write_message(stream, value, RESPONSE_LIMIT)


def canonical_bytes(value: Mapping[str, Any]) -> bytes:
    try:
        return json.dumps(
            value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
        ).encode("utf-8")
    except (TypeError, ValueError) as error:
        raise ProtocolError("INVALID_JSON_VALUE") from error


def _read_message(stream: BinaryIO, limit: int) -> dict[str, Any]:
    header = _read_exact(stream, _HEADER_SIZE)
    size = struct.unpack(">I", header)[0]
    if size == 0 or size > limit:
        raise ProtocolError("INVALID_MESSAGE_SIZE")
    raw = _read_exact(stream, size)
    try:
        value = json.loads(raw.decode("utf-8"), parse_constant=_reject_constant)
    except (UnicodeDecodeError, json.JSONDecodeError, ProtocolError) as error:
        if isinstance(error, ProtocolError):
            raise
        raise ProtocolError("INVALID_JSON") from error
    if not isinstance(value, dict):
        raise ProtocolError("INVALID_MESSAGE")
    return value


def _write_message(stream: BinaryIO, value: Mapping[str, Any], limit: int) -> None:
    if not isinstance(value, Mapping):
        raise ProtocolError("INVALID_MESSAGE")
    raw = canonical_bytes(value)
    if not raw or len(raw) > limit:
        raise ProtocolError("MESSAGE_TOO_LARGE")
    framed = struct.pack(">I", len(raw)) + raw
    if hasattr(stream, "sendall"):
        stream.sendall(framed)
    else:
        stream.write(framed)


def _read_exact(stream: BinaryIO, size: int) -> bytes:
    chunks: list[bytes] = []
    remaining = size
    while remaining:
        chunk = stream.recv(remaining) if hasattr(stream, "recv") else stream.read(remaining)
        if not chunk:
            raise ProtocolError("UNEXPECTED_EOF")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def _reject_constant(_: str) -> None:
    raise ProtocolError("INVALID_JSON")
