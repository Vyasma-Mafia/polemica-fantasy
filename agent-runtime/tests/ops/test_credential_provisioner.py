from __future__ import annotations

import importlib.util
import os
from pathlib import Path

import pytest


SCRIPT = Path(__file__).resolve().parents[2] / "deploy" / "provision_fantasy_credential.py"
SPEC = importlib.util.spec_from_file_location("credential_provisioner", SCRIPT)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


def test_atomic_target_is_root_style_and_read_only(tmp_path: Path) -> None:
    target = tmp_path / "fantasy-mcp.env"
    target.touch(mode=0o600)
    token = "pfa_" + "a" * 43
    module._atomic_write_target(target, token=token, fantasy_origin="https://fantasy.example")
    assert target.read_text() == (
        "FANTASY_API_BASE_URL=https://fantasy.example\n"
        f"FANTASY_BEARER_TOKEN={token}\n"
        "WRITE_ENABLED=false\n"
        "FANTASY_WRITE_ALLOWLIST=\n"
    )
    assert os.stat(target).st_mode & 0o777 == 0o600


def test_nonempty_or_symlink_target_is_rejected(tmp_path: Path) -> None:
    target = tmp_path / "fantasy-mcp.env"
    target.write_text("existing-secret")
    with pytest.raises(module.ProvisionError, match="not empty"):
        module._ensure_empty_target(target)
    target.unlink()
    actual = tmp_path / "actual"
    actual.touch()
    target.symlink_to(actual)
    with pytest.raises(module.ProvisionError, match="symlink"):
        module._ensure_empty_target(target)


def test_origin_rejects_embedded_credentials() -> None:
    with pytest.raises(module.ProvisionError):
        module._origin("https://user:password@example.test")


def test_script_never_prints_full_token() -> None:
    text = SCRIPT.read_text()
    assert "print(token" not in text
    assert "result['token']}" not in text
