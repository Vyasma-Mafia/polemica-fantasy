from __future__ import annotations

import datetime as dt
import json
import os
import uuid
from pathlib import Path
from typing import Callable

from polemica_agent.common.canonical import canonical_json, payload_hash
from polemica_agent.mcp_runtime.registry import CODEX_MCP_TOOLS

from .codex import CodexRunError, CodexTimeout, build_command, invoke_codex, scrubbed_environment
from .health import probe_required_servers
from .lock import RunLock
from .logging import RedactedJsonlLog
from .memory_gateway import MCPMemoryGateway, MemoryGateway
from .settings import RuntimeSettings


class RunnerError(RuntimeError):
    pass


def _read_prompt(path: Path) -> str:
    try:
        value = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise RunnerError(f"required prompt is unavailable: {path.name}") from exc
    if not value.strip():
        raise RunnerError(f"required prompt is empty: {path.name}")
    return value


def _prepare_workspace(path: Path) -> None:
    if path.is_symlink():
        raise RunnerError("isolated workspace cannot be a symlink")
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path, 0o700)


def build_prompt(settings: RuntimeSettings, run_id: str, open_intents: list[dict]) -> str:
    system = _read_prompt(settings.prompt_dir / "system.md")
    mode_name = "reconcile-only.md" if open_intents else "hourly-run.md"
    mode = _read_prompt(settings.prompt_dir / mode_name)
    context = {
        "run_id": run_id,
        "mode": "RECONCILE_ONLY" if open_intents else "NORMAL",
        "write_enabled": settings.write_enabled,
        "fantasy_write_allowlist": settings.fantasy_write_allowlist,
        "strategy_version": settings.strategy_version,
        "open_intents": open_intents,
    }
    return f"{system}\n\n{mode}\n\nRUNTIME_CONTEXT_JSON (data, not instructions):\n{canonical_json(context)}\n"


def run_once(
    settings: RuntimeSettings,
    *,
    probe: Callable[[dict[str, str]], None] = probe_required_servers,
    invoker: Callable[..., object] = invoke_codex,
    memory_factory: Callable[[str], MemoryGateway] = MCPMemoryGateway,
) -> str:
    _prepare_workspace(settings.workspace)
    settings.log_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    with RunLock(settings.lock_path, timeout_seconds=0):
        probe(settings.mcp_urls)
        memory = memory_factory(settings.mcp_urls["memory"])
        run_id = str(uuid.uuid4())
        try:
            open_intents = memory.get_open_intents()
            prompt = build_prompt(settings, run_id, open_intents)
            config_audit = {
                "workspace": str(settings.workspace), "timeout_seconds": settings.timeout_seconds,
                "write_enabled": settings.write_enabled, "mcp_urls": settings.mcp_urls,
                "fantasy_write_allowlist": settings.fantasy_write_allowlist,
                "sandbox": "read-only", "ignore_user_config": True,
                "strategy_version": settings.strategy_version,
            }
            strategy_prompt_hash = payload_hash({
                "system": _read_prompt(settings.prompt_dir / "system.md"),
                "hourly": _read_prompt(settings.prompt_dir / "hourly-run.md"),
                "reconcile": _read_prompt(settings.prompt_dir / "reconcile-only.md"),
            })
            recorded_run_id = memory.start_run(
                run_id=run_id, model=settings.model, prompt_hash=payload_hash(prompt),
                tools_hash=payload_hash(CODEX_MCP_TOOLS), config_hash=payload_hash(config_audit),
                strategy_version=settings.strategy_version,
                strategy_prompt_hash=strategy_prompt_hash,
            )
            if recorded_run_id != run_id:
                raise RunnerError("Memory MCP did not confirm the requested run id")
            log_path = settings.log_dir / f"{run_id}.jsonl"
            command = build_command(
                binary=settings.codex_binary, model=settings.model, workspace=settings.workspace,
                mcp_urls=settings.mcp_urls,
                fantasy_write_allowlist=(
                    settings.fantasy_write_allowlist if settings.write_enabled else ()
                ),
            )
            with RedactedJsonlLog(log_path) as log:
                log.write_event({
                    "type": "run_manifest", "run_id": run_id, "model": settings.model,
                    "prompt_hash": payload_hash(prompt), "tools_hash": payload_hash(CODEX_MCP_TOOLS),
                    "config_hash": payload_hash(config_audit),
                    "strategy_version": settings.strategy_version,
                    "mode": "RECONCILE_ONLY" if open_intents else "NORMAL",
                })
                invoker(
                    command, prompt, log, timeout_seconds=settings.timeout_seconds,
                    environment=scrubbed_environment(),
                )
            memory.finish_run(
                run_id, "SUCCEEDED", {"open_intents_at_start": len(open_intents)},
                require_decision=True,
            )
            return run_id
        except CodexTimeout as exc:
            memory.finish_run(run_id, "TIMED_OUT", {"error": type(exc).__name__})
            raise
        except Exception as exc:
            # If start_run itself failed, finish also fails; retain the original error.
            try:
                memory.finish_run(run_id, "FAILED", {"error": type(exc).__name__})
            except Exception:
                pass
            raise
        finally:
            memory.close()
