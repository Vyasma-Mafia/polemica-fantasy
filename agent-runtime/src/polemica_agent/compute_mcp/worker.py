"""Sequential AF_UNIX daemon for the isolated fixed-operation compute engine."""

from __future__ import annotations

import argparse
import os
import socket
import stat
from collections.abc import Mapping, Sequence
from typing import Any

from .engine import ComputeError, execute
from .protocol import ProtocolError, read_request, write_response


def handle_connection(connection: socket.socket) -> None:
    try:
        request = read_request(connection)
        if set(request) != {"operation", "payload"}:
            raise ProtocolError("INVALID_REQUEST")
        operation = request["operation"]
        payload = request["payload"]
        if not isinstance(operation, str) or not isinstance(payload, Mapping):
            raise ProtocolError("INVALID_REQUEST")
        response: dict[str, Any] = {"ok": True, "result": execute(operation, payload)}
    except ComputeError as error:
        response = {"ok": False, "error": {"code": error.code}}
    except ProtocolError as error:
        response = {"ok": False, "error": {"code": error.code}}
    except Exception:
        response = {"ok": False, "error": {"code": "INTERNAL_ERROR"}}
    try:
        write_response(connection, response)
    except (OSError, ProtocolError):
        return


def serve(
    socket_path: str,
    *,
    connection_timeout_seconds: float = 20.0,
    max_connections: int | None = None,
) -> None:
    if not 0 < connection_timeout_seconds <= 20:
        raise ValueError("connection timeout must be in (0, 20]")
    if max_connections is not None and max_connections <= 0:
        raise ValueError("max_connections must be positive")
    listener = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    bound_identity: tuple[int, int] | None = None
    handled = 0
    try:
        listener.bind(socket_path)
        metadata = os.lstat(socket_path)
        bound_identity = (metadata.st_dev, metadata.st_ino)
        listener.listen(16)
        while True:
            connection, _ = listener.accept()
            with connection:
                connection.settimeout(connection_timeout_seconds)
                handle_connection(connection)
            handled += 1
            if max_connections is not None and handled >= max_connections:
                return
    finally:
        listener.close()
        _remove_own_socket(socket_path, bound_identity)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Polemica fixed-operation compute worker")
    parser.add_argument("--socket", required=True, help="AF_UNIX socket path owned by this service")
    args = parser.parse_args(argv)
    serve(args.socket)
    return 0


def _remove_own_socket(socket_path: str, identity: tuple[int, int] | None) -> None:
    if identity is None:
        return
    try:
        metadata = os.lstat(socket_path)
        if stat.S_ISSOCK(metadata.st_mode) and (metadata.st_dev, metadata.st_ino) == identity:
            os.unlink(socket_path)
    except FileNotFoundError:
        return


if __name__ == "__main__":
    raise SystemExit(main())
