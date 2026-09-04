#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import getpass
import json
import os
import re
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable


TOKEN_PATTERN = re.compile(r"^pfa_[A-Za-z0-9_-]{43}$")
TARGET = Path("/etc/polemica-ai-agent/fantasy-mcp.env")
DEFAULT_ADMIN_ORIGIN = "https://admin.fantasy.maftourbot.ru"
DEFAULT_FANTASY_ORIGIN = "https://fantasy.maftourbot.ru"


class ProvisionError(RuntimeError):
    pass


def _origin(value: str) -> str:
    parsed = urllib.parse.urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc or parsed.path not in {"", "/"}:
        raise ProvisionError("API origin must be an HTTPS origin")
    if parsed.query or parsed.fragment or parsed.username or parsed.password:
        raise ProvisionError("API origin must not contain credentials, query, or fragment")
    return value.rstrip("/")


def _ensure_empty_target(target: Path) -> None:
    if target.is_symlink():
        raise ProvisionError("credential target must not be a symlink")
    if target.exists() and target.read_bytes().strip():
        raise ProvisionError("credential target is not empty; revoke/rotate explicitly")
    if not target.parent.is_dir():
        raise ProvisionError("credential target directory is absent")


def _request_credential(
    *,
    telegram_id: int,
    expires_at: str,
    admin_origin: str,
    username: str,
    password: str,
    opener: Callable[..., Any] = urllib.request.urlopen,
) -> dict[str, Any]:
    url = f"{_origin(admin_origin)}/api/v1/admin/users/{telegram_id}/api-credentials"
    body = json.dumps({"label": "codex-runtime", "expiresAt": expires_at}).encode()
    basic = base64.b64encode(f"{username}:{password}".encode()).decode("ascii")
    request = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={
            "Accept": "application/json",
            "Content-Type": "application/json",
            "Authorization": f"Basic {basic}",
        },
    )
    try:
        with opener(request, timeout=30) as response:
            raw = response.read(65_537)
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
        raise ProvisionError("credential API request failed") from exc
    if len(raw) > 65_536:
        raise ProvisionError("credential API response is unexpectedly large")
    try:
        value = json.loads(raw)
        token = value["token"]
        credential = value["credential"]
        credential_id = credential["id"]
        token_hint = credential["tokenHint"]
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        raise ProvisionError("credential API response has an invalid shape") from exc
    if not isinstance(token, str) or not TOKEN_PATTERN.fullmatch(token):
        raise ProvisionError("credential API returned an invalid token")
    if not isinstance(credential_id, int) or not isinstance(token_hint, str):
        raise ProvisionError("credential API returned invalid metadata")
    return {"token": token, "credential_id": credential_id, "token_hint": token_hint}


def _atomic_write_target(target: Path, *, token: str, fantasy_origin: str) -> None:
    _ensure_empty_target(target)
    content = (
        f"FANTASY_API_BASE_URL={_origin(fantasy_origin)}\n"
        f"FANTASY_BEARER_TOKEN={token}\n"
        "WRITE_ENABLED=false\n"
        "FANTASY_WRITE_ALLOWLIST=\n"
    ).encode()
    descriptor, temporary_name = tempfile.mkstemp(prefix=".fantasy-mcp.env.", dir=target.parent)
    temporary = Path(temporary_name)
    try:
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(content)
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
    parser = argparse.ArgumentParser(description="Issue and install one Fantasy Bearer credential")
    parser.add_argument("telegram_id", type=int)
    parser.add_argument("--admin-origin", default=DEFAULT_ADMIN_ORIGIN)
    parser.add_argument("--fantasy-origin", default=DEFAULT_FANTASY_ORIGIN)
    parser.add_argument("--expires-at")
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise SystemExit("provisioner must run as root")
    if args.telegram_id <= 0:
        raise SystemExit("telegram_id must be positive")
    _ensure_empty_target(TARGET)
    expires_at = args.expires_at or (
        datetime.now(timezone.utc) + timedelta(days=365)
    ).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    admin_username = input("Fantasy admin username: ").strip()
    admin_password = getpass.getpass("Fantasy admin password: ")
    if not admin_username or not admin_password:
        raise SystemExit("admin credentials are required")
    try:
        result = _request_credential(
            telegram_id=args.telegram_id,
            expires_at=expires_at,
            admin_origin=args.admin_origin,
            username=admin_username,
            password=admin_password,
        )
        _atomic_write_target(TARGET, token=result["token"], fantasy_origin=args.fantasy_origin)
    except ProvisionError as exc:
        raise SystemExit(str(exc)) from None
    print(
        f"Credential {result['credential_id']} installed; token hint "
        f"{result['token_hint']}; Fantasy writes remain disabled."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
