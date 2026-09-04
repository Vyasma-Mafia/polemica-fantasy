from __future__ import annotations

import io
import json
import os
import socket
import struct
import threading
import time
import uuid
from pathlib import Path

import pytest

from polemica_agent.compute_mcp.protocol import (
    ProtocolError,
    REQUEST_LIMIT,
    canonical_bytes,
    read_request,
    read_response,
    write_request,
)
from polemica_agent.compute_mcp.client import ComputeWorkerClient
from polemica_agent.compute_mcp.worker import handle_connection, serve


def test_protocol_round_trip_is_canonical_and_length_prefixed() -> None:
    stream = io.BytesIO()
    write_request(stream, {"z": 1, "a": "привет"})
    raw = stream.getvalue()
    size = struct.unpack(">I", raw[:4])[0]
    assert size == len(raw) - 4
    assert raw[4:] == canonical_bytes({"z": 1, "a": "привет"})
    stream.seek(0)
    assert read_request(stream) == {"a": "привет", "z": 1}


def test_request_limit_is_checked_before_body_read() -> None:
    stream = io.BytesIO(struct.pack(">I", REQUEST_LIMIT + 1))
    with pytest.raises(ProtocolError, match="INVALID_MESSAGE_SIZE"):
        read_request(stream)


@pytest.mark.parametrize(
    "body",
    [b"{", b"[1]", b'{"value":NaN}', b'\xff'],
)
def test_invalid_or_non_object_json_is_rejected(body: bytes) -> None:
    stream = io.BytesIO(struct.pack(">I", len(body)) + body)
    with pytest.raises(ProtocolError):
        read_request(stream)


def test_truncated_header_and_body_are_rejected() -> None:
    with pytest.raises(ProtocolError, match="UNEXPECTED_EOF"):
        read_request(io.BytesIO(b"\x00"))
    with pytest.raises(ProtocolError, match="UNEXPECTED_EOF"):
        read_request(io.BytesIO(struct.pack(">I", 3) + b"{}"))


def test_worker_handles_one_request_and_sanitizes_contract_error() -> None:
    client, server = socket.socketpair()
    try:
        write_request(
            client,
            {
                "operation": "describe_player_points",
                "payload": {
                    "rows": [{"playerId": 1, "points": 2, "mmr": 3, "win": 1, "roleCode": 0}],
                    "playerIds": [1],
                    "quantiles": [0.5],
                },
            },
        )
        handle_connection(server)
        response = read_response(client)
        assert response["ok"] is True
        assert response["result"]["players"][0]["mean"] == 2.0
    finally:
        client.close()
        server.close()

    client, server = socket.socketpair()
    try:
        write_request(client, {"operation": "eval", "payload": {"code": "open('/etc/passwd')"}})
        handle_connection(server)
        response = read_response(client)
        assert response == {"ok": False, "error": {"code": "UNKNOWN_OPERATION"}}
        assert "/etc/passwd" not in json.dumps(response)
    finally:
        client.close()
        server.close()


def test_worker_rejects_extra_envelope_fields_without_echoing_them() -> None:
    client, server = socket.socketpair()
    try:
        write_request(client, {"operation": "describe_player_points", "payload": {}, "secret": "Bearer x"})
        handle_connection(server)
        response = read_response(client)
        assert response == {"ok": False, "error": {"code": "INVALID_REQUEST"}}
        assert "Bearer" not in json.dumps(response)
    finally:
        client.close()
        server.close()


def test_real_unix_worker_and_client_round_trip() -> None:
    socket_path = f"/tmp/polemica-compute-{uuid.uuid4().hex}.sock"
    thread = threading.Thread(
        target=serve, args=(socket_path,), kwargs={"max_connections": 1}, daemon=True,
    )
    thread.start()
    _wait_for_socket(socket_path)

    result = ComputeWorkerClient(Path(socket_path)).run(
        "describe_player_points",
        {
            "rows": [{"playerId": 1, "points": 2, "mmr": 3, "win": 1, "roleCode": 0}],
            "playerIds": [1],
            "quantiles": [0.5],
        },
    )

    thread.join(timeout=1)
    assert not thread.is_alive()
    assert result["players"][0]["mean"] == 2.0
    assert not os.path.exists(socket_path)


def test_real_worker_times_out_idle_peer() -> None:
    socket_path = f"/tmp/polemica-compute-{uuid.uuid4().hex}.sock"
    thread = threading.Thread(
        target=serve,
        args=(socket_path,),
        kwargs={"max_connections": 1, "connection_timeout_seconds": 0.05},
        daemon=True,
    )
    thread.start()
    _wait_for_socket(socket_path)
    peer = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    try:
        peer.connect(socket_path)
        thread.join(timeout=1)
        assert not thread.is_alive()
    finally:
        peer.close()
    assert not os.path.exists(socket_path)


def _wait_for_socket(socket_path: str) -> None:
    deadline = time.monotonic() + 1
    while not os.path.exists(socket_path):
        if time.monotonic() >= deadline:
            raise AssertionError("worker socket was not created")
        time.sleep(0.005)
