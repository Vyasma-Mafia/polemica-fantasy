from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path

import pytest


SCRIPT = Path(__file__).resolve().parents[2] / "deploy" / "provision_research_environment.py"
SPEC = importlib.util.spec_from_file_location("research_provisioner", SCRIPT)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class Response:
    def __enter__(self): return self
    def __exit__(self, *_args): return None
    def read(self, _limit): return json.dumps({"access_token": "temporary-upstream-token"}).encode()


def test_validation_uses_fixed_https_login_without_leaking_credentials() -> None:
    captured = {}

    def opener(request, timeout):
        captured["url"] = request.full_url
        captured["body"] = json.loads(request.data)
        captured["timeout"] = timeout
        return Response()

    module._validate_polemica("reader", "secret", opener=opener)
    assert captured == {
        "url": "https://app.polemicagame.com/v1/auth/login",
        "body": {"username": "reader", "password": "secret"},
        "timeout": 30,
    }


def test_atomic_write_is_mode_600(tmp_path: Path) -> None:
    target = tmp_path / "runner.env"
    module._atomic_write(target, "WRITE_ENABLED=false\n")
    assert target.read_text() == "WRITE_ENABLED=false\n"
    assert os.stat(target).st_mode & 0o777 == 0o600


def test_nonempty_target_requires_explicit_replace(tmp_path: Path) -> None:
    target = tmp_path / "research-mcp.env"
    target.write_text("POLEMICA_PASSWORD=existing")
    with pytest.raises(module.ProvisionError, match="--replace"):
        module._check_target(target, replace=False)
    module._check_target(target, replace=True)


def test_environment_values_are_systemd_quoted() -> None:
    assert module._env_value('reader # "one" \\ two') == '"reader # \\"one\\" \\\\ two"'
    with pytest.raises(module.ProvisionError, match="control character"):
        module._env_value("line-one\nline-two")


def test_script_does_not_print_secret_values() -> None:
    text = SCRIPT.read_text()
    assert "print(password" not in text
    assert "print(username" not in text


def test_runner_environment_includes_compute_and_new_strategy_version() -> None:
    text = SCRIPT.read_text()
    assert '"COMPUTE_MCP_URL=http://127.0.0.1:8814/mcp\\n"' in text
    assert '"POLEMICA_AGENT_STRATEGY_VERSION=hourly-compute-v1\\n"' in text
    assert "POLEMICA_AGENT_STRATEGY_VERSION=hourly-v1" not in text
