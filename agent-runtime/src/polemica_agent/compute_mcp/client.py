from __future__ import annotations

import socket
from pathlib import Path
from typing import Any, Mapping

from .protocol import ProtocolError, read_response, write_request


class ComputeWorkerError(RuntimeError):
    def __init__(self, code: str) -> None:
        super().__init__(code)
        self.code = code


class ComputeWorkerClient:
    def __init__(self, socket_path: Path, *, timeout_seconds: float = 15.0) -> None:
        if not socket_path.is_absolute() or len(str(socket_path)) > 100:
            raise ValueError("compute worker socket must be a short absolute path")
        if not 0 < timeout_seconds <= 15:
            raise ValueError("compute worker timeout must be in (0, 15]")
        self.socket_path = socket_path
        self.timeout_seconds = timeout_seconds

    def run(self, operation: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        try:
            with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
                connection.settimeout(self.timeout_seconds)
                connection.connect(str(self.socket_path))
                write_request(connection, {"operation": operation, "payload": dict(payload)})
                response = read_response(connection)
        except socket.timeout:
            raise ComputeWorkerError("COMPUTE_WORKER_TIMEOUT") from None
        except (OSError, ProtocolError):
            raise ComputeWorkerError("COMPUTE_WORKER_UNAVAILABLE") from None
        if not isinstance(response, Mapping):
            raise ComputeWorkerError("COMPUTE_WORKER_PROTOCOL")
        if response.get("ok") is not True:
            error = response.get("error")
            code = error.get("code") if isinstance(error, Mapping) else None
            if not isinstance(code, str):
                code = "COMPUTE_WORKER_FAILED"
            raise ComputeWorkerError(code)
        result = response.get("result")
        if not isinstance(result, Mapping):
            raise ComputeWorkerError("COMPUTE_WORKER_PROTOCOL")
        return dict(result)
