from __future__ import annotations

import os
import signal
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence

from polemica_agent.mcp_runtime.registry import (
    CODEX_MCP_TOOLS, FANTASY_RECOVERY_TOOLS, FANTASY_WRITE_TOOLS,
)

from .logging import RedactedJsonlLog


class CodexRunError(RuntimeError):
    pass


class CodexTimeout(CodexRunError):
    pass


@dataclass(frozen=True)
class CodexResult:
    returncode: int
    command: tuple[str, ...]


def build_command(
    *, binary: str, model: str, workspace: Path, mcp_urls: Mapping[str, str],
    fantasy_write_allowlist: Sequence[str] = (),
) -> list[str]:
    unknown_writes = set(fantasy_write_allowlist) - set(FANTASY_WRITE_TOOLS)
    if unknown_writes:
        raise ValueError("unknown Fantasy write tool cannot be exposed to Codex")
    command = [
        binary, "exec", "--ignore-user-config", "--strict-config", "--ephemeral",
        "--ignore-rules", "--skip-git-repo-check", "--json", "--color", "never",
        "--sandbox", "read-only",
        "--model", model, "--cd", str(workspace),
        "--disable", "shell_tool", "--disable", "unified_exec",
        "--disable", "shell_snapshot", "--disable", "skill_mcp_dependency_install",
        "--config", 'approval_policy="never"',
        "--config", 'shell_environment_policy.inherit="none"',
        "--config", "agents.enabled=false",
        "--config", 'web_search="disabled"',
        "--config", 'history.persistence="none"',
    ]
    for kind in ("fantasy", "research", "compute", "memory"):
        url = mcp_urls[kind]
        names = CODEX_MCP_TOOLS[kind]
        if kind == "fantasy":
            approved = set(fantasy_write_allowlist)
            names = tuple(
                name for name in names
                if name not in FANTASY_WRITE_TOOLS or name in approved
            )
        tools = ",".join(f'"{name}"' for name in names)
        command.extend(("--config", f'mcp_servers.{kind}.url="{url}"'))
        command.extend(("--config", f"mcp_servers.{kind}.required=true"))
        command.extend(("--config", f"mcp_servers.{kind}.startup_timeout_sec=15"))
        command.extend(("--config", f"mcp_servers.{kind}.tool_timeout_sec=60"))
        command.extend(("--config", f"mcp_servers.{kind}.enabled_tools=[{tools}]"))
        if kind == "fantasy":
            for name in fantasy_write_allowlist:
                command.extend((
                    "--config",
                    f'mcp_servers.fantasy.tools.{name}.approval_mode="approve"',
                ))
            for name in FANTASY_RECOVERY_TOOLS:
                command.extend((
                    "--config",
                    f'mcp_servers.fantasy.tools.{name}.approval_mode="approve"',
                ))
    command.append("-")
    return command


def scrubbed_environment(source: Mapping[str, str] | None = None) -> dict[str, str]:
    source = source or os.environ
    keep = {"PATH", "CODEX_HOME", "LANG", "LC_ALL", "SSL_CERT_FILE", "SSL_CERT_DIR"}
    return {key: value for key, value in source.items() if key in keep}


def invoke_codex(
    command: Sequence[str], prompt: str, log: RedactedJsonlLog, *, timeout_seconds: int,
    environment: Mapping[str, str] | None = None,
) -> CodexResult:
    process = subprocess.Popen(
        list(command), stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding="utf-8", env=dict(environment or scrubbed_environment()),
        start_new_session=True,
    )
    assert process.stdin is not None and process.stdout is not None
    process.stdin.write(prompt)
    process.stdin.close()
    try:
        # communicate() cannot be used after manually closing stdin on all supported Pythons.
        import selectors
        import time
        selector = selectors.DefaultSelector()
        selector.register(process.stdout, selectors.EVENT_READ)
        deadline = time.monotonic() + timeout_seconds
        while process.poll() is None:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise CodexTimeout(f"codex exceeded {timeout_seconds}s")
            for key, _ in selector.select(min(1.0, remaining)):
                line = key.fileobj.readline()
                if line:
                    log.write_line(line)
        for line in process.stdout:
            log.write_line(line)
    except CodexTimeout:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
        raise
    if process.returncode != 0:
        raise CodexRunError(f"codex exited with {process.returncode}")
    return CodexResult(process.returncode, tuple(command))
