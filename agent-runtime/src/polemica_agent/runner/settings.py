from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


class SettingsError(RuntimeError):
    pass


@dataclass(frozen=True)
class RuntimeSettings:
    workspace: Path
    log_dir: Path
    lock_path: Path
    prompt_dir: Path
    model: str
    timeout_seconds: int
    write_enabled: bool
    mcp_urls: dict[str, str]
    codex_binary: str = "codex"

    @classmethod
    def from_env(cls) -> "RuntimeSettings":
        root = Path(os.environ.get("POLEMICA_AGENT_RUNTIME_ROOT", "/var/lib/polemica-ai-agent"))
        values = cls(
            workspace=Path(os.environ.get("POLEMICA_AGENT_WORKSPACE", str(root / "workspace"))),
            log_dir=Path(os.environ.get("POLEMICA_AGENT_LOG_DIR", str(root / "logs"))),
            lock_path=Path(os.environ.get("POLEMICA_AGENT_RUN_LOCK", str(root / "run.lock"))),
            prompt_dir=Path(os.environ.get("POLEMICA_AGENT_PROMPT_DIR", "/opt/polemica-ai-agent/prompts")),
            model=os.environ.get("POLEMICA_AGENT_MODEL", "gpt-5.6-sol"),
            timeout_seconds=int(os.environ.get("POLEMICA_AGENT_RUN_TIMEOUT_SECONDS", "3300")),
            write_enabled=os.environ.get("WRITE_ENABLED", "false").lower() == "true",
            mcp_urls={
                "fantasy": os.environ.get("FANTASY_MCP_URL", "http://127.0.0.1:8811/mcp"),
                "research": os.environ.get("RESEARCH_MCP_URL", "http://127.0.0.1:8812/mcp"),
                "memory": os.environ.get("MEMORY_MCP_URL", "http://127.0.0.1:8813/mcp"),
            },
            codex_binary=os.environ.get("CODEX_BINARY", "codex"),
        )
        values.validate()
        return values

    def validate(self) -> None:
        for path in (self.workspace, self.log_dir, self.lock_path, self.prompt_dir):
            if not path.is_absolute():
                raise SettingsError("runtime paths must be absolute")
        if not 1 <= self.timeout_seconds <= 3300:
            raise SettingsError("timeout must be in 1..3300 seconds")
        if set(self.mcp_urls) != {"fantasy", "research", "memory"}:
            raise SettingsError("all three MCP servers are required")
        for url in self.mcp_urls.values():
            parsed = urlparse(url)
            if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "::1", "localhost"}:
                raise SettingsError("MCP URLs must use loopback HTTP")
