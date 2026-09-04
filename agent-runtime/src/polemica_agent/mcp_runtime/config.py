from __future__ import annotations

import os
from dataclasses import dataclass
from urllib.parse import urlparse


class MCPConfigError(RuntimeError):
    pass


@dataclass(frozen=True)
class ServerBinding:
    host: str
    port: int

    @classmethod
    def from_env(cls) -> "ServerBinding":
        host = os.environ.get("MCP_BIND_HOST", "127.0.0.1")
        port = int(os.environ.get("MCP_BIND_PORT", "8000"))
        if host not in {"127.0.0.1", "::1", "localhost"}:
            raise MCPConfigError("MCP server must bind to loopback")
        if not 1024 <= port <= 65535:
            raise MCPConfigError("MCP_BIND_PORT must be in 1024..65535")
        return cls(host, port)


def required_https_origin(name: str) -> str:
    value = os.environ.get(name, "")
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc or parsed.path not in {"", "/"}:
        raise MCPConfigError(f"{name} must be an HTTPS origin")
    return value.rstrip("/")
