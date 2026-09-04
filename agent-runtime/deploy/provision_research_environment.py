#!/usr/bin/env python3
from __future__ import annotations

import argparse
import getpass
import json
import os
import tempfile
import urllib.error
import urllib.request
from pathlib import Path


RESEARCH_TARGET = Path("/etc/polemica-ai-agent/research-mcp.env")
RUNNER_TARGET = Path("/etc/polemica-ai-agent/runner.env")
POLEMICA_API_ORIGIN = "https://app.polemicagame.com"
POLEMICA_PROFILE_ORIGIN = "https://polemicagame.com"


class ProvisionError(RuntimeError):
    pass


def _env_value(value: str) -> str:
    if any(character in value for character in ("\n", "\r", "\0")):
        raise ProvisionError("environment value contains a forbidden control character")
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _check_target(target: Path, *, replace: bool) -> None:
    if target.is_symlink():
        raise ProvisionError(f"{target.name} must not be a symlink")
    if not target.parent.is_dir():
        raise ProvisionError("runtime config directory is absent")
    if target.exists() and target.read_bytes().strip() and not replace:
        raise ProvisionError(f"{target.name} is not empty; pass --replace for explicit rotation")


def _validate_polemica(username: str, password: str, opener=urllib.request.urlopen) -> None:
    body = json.dumps({"username": username, "password": password}).encode()
    request = urllib.request.Request(
        f"{POLEMICA_API_ORIGIN}/v1/auth/login",
        data=body,
        method="POST",
        headers={"Accept": "application/json", "Content-Type": "application/json"},
    )
    try:
        with opener(request, timeout=30) as response:
            raw = response.read(65_537)
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
        raise ProvisionError("Polemica credential validation failed") from exc
    if len(raw) > 65_536:
        raise ProvisionError("Polemica login response is unexpectedly large")
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ProvisionError("Polemica login response is invalid") from exc
    if (
        not isinstance(value, dict)
        or not isinstance(value.get("access_token"), str)
        or not value["access_token"]
    ):
        raise ProvisionError("Polemica login did not return a token")


def _atomic_write(target: Path, content: str) -> None:
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{target.name}.", dir=target.parent)
    temporary = Path(temporary_name)
    try:
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(content.encode())
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, target)
        directory_descriptor = os.open(target.parent, os.O_RDONLY)
        try:
            os.fsync(directory_descriptor)
        finally:
            os.close(directory_descriptor)
    finally:
        if temporary.exists():
            temporary.unlink()


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate and install Polemica Research environment")
    parser.add_argument("--replace", action="store_true")
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise SystemExit("provisioner must run as root")
    _check_target(RESEARCH_TARGET, replace=args.replace)
    _check_target(RUNNER_TARGET, replace=args.replace)
    username = input("Polemica read-only username: ").strip()
    password = getpass.getpass("Polemica read-only password: ")
    if not username or not password:
        raise SystemExit("valid Polemica credentials are required")
    try:
        _validate_polemica(username, password)
        _atomic_write(
            RESEARCH_TARGET,
            f"POLEMICA_API_BASE_URL={POLEMICA_API_ORIGIN}\n"
            f"POLEMICA_PROFILE_BASE_URL={POLEMICA_PROFILE_ORIGIN}\n"
            f"POLEMICA_USERNAME={_env_value(username)}\n"
            f"POLEMICA_PASSWORD={_env_value(password)}\n",
        )
        _atomic_write(
            RUNNER_TARGET,
            "FANTASY_MCP_URL=http://127.0.0.1:8811/mcp\n"
            "RESEARCH_MCP_URL=http://127.0.0.1:8812/mcp\n"
            "COMPUTE_MCP_URL=http://127.0.0.1:8814/mcp\n"
            "MEMORY_MCP_URL=http://127.0.0.1:8813/mcp\n"
            "WRITE_ENABLED=false\n"
            "POLEMICA_PRODUCTION_ACTIVATION_APPROVED=false\n"
            "POLEMICA_AGENT_MODEL=gpt-5.6-sol\n"
            "POLEMICA_AGENT_STRATEGY_VERSION=hourly-compute-v1\n",
        )
    except ProvisionError as exc:
        raise SystemExit(str(exc)) from None
    print("Polemica credential validated; Research and runner environments installed read-only.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
