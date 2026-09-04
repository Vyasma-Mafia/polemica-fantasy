from __future__ import annotations

import os
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SYSTEMD = ROOT / "deploy" / "systemd"


def test_units_are_disabled_loopback_and_hardened() -> None:
    services = list(SYSTEMD.glob("*.service"))
    assert len(services) == 7
    users = set()
    for path in services:
        text = path.read_text()
        users.add(next(line for line in text.splitlines() if line.startswith("User=")))
        assert "ProtectSystem=strict" in text
        assert "ProtectProc=invisible" in text
        assert "ProcSubset=pid" in text
        assert "InaccessiblePaths=" in text and "-/root/.ssh" in text and "-/proc/" in text
        assert "systemctl" not in text
    assert users == {
        "User=polemica-agent-broker", "User=polemica-agent-compute", "User=codex",
    }
    assert all("User=polemica-agent-broker" in path.read_text() for path in SYSTEMD.glob("*-mcp.service"))
    for path in SYSTEMD.glob("*-mcp.service"):
        assert "MCP_BIND_HOST=127.0.0.1" in path.read_text()
        assert "EnvironmentFile=/etc/polemica-ai-agent/" in path.read_text()
        assert "Requires=polemica-agent-migrate.service" in path.read_text()
        assert "ReadWritePaths=/var/lib/polemica-ai-agent" in path.read_text()


def test_compute_worker_is_networkless_bounded_and_has_no_broker_state() -> None:
    text = (SYSTEMD / "polemica-agent-compute-worker.service").read_text()
    required = {
        "User=polemica-agent-compute",
        "Group=polemica-agent-compute",
        "RuntimeDirectory=polemica-agent-compute",
        "RuntimeDirectoryMode=0750",
        "PrivateNetwork=true",
        "RestrictAddressFamilies=AF_UNIX",
        "MemoryMax=256M",
        "MemorySwapMax=0",
        "CPUQuota=100%",
        "TasksMax=4",
        "LimitNOFILE=64",
        "MemoryDenyWriteExecute=true",
    }
    assert required.issubset(set(text.splitlines()))
    assert (
        "ExecStart=/opt/polemica-ai-agent/venv/bin/polemica-agent-compute-worker "
        "--socket /run/polemica-agent-compute/worker.sock"
    ) in text
    assert "EnvironmentFile=" not in text
    assert "POLEMICA_AGENT_DATABASE" not in text
    assert "ReadWritePaths=/var/lib/polemica-ai-agent" not in text
    assert "-/var/lib/polemica-ai-agent" in text
    assert "[Install]" not in text.splitlines()


def test_compute_gateway_is_loopback_only_and_has_no_upstream_credentials() -> None:
    text = (SYSTEMD / "polemica-agent-compute-mcp.service").read_text()
    assert "User=polemica-agent-broker" in text
    assert "SupplementaryGroups=polemica-agent-compute" in text
    assert "EnvironmentFile=/etc/polemica-ai-agent/compute-mcp.env" in text
    assert "MCP_BIND_HOST=127.0.0.1 MCP_BIND_PORT=8814" in text
    assert "POLEMICA_AGENT_DATABASE=/var/lib/polemica-ai-agent/agent.sqlite3" in text
    assert "POLEMICA_COMPUTE_WORKER_SOCKET=/run/polemica-agent-compute/worker.sock" in text
    assert "Requires=polemica-agent-migrate.service polemica-agent-compute-worker.service" in text
    assert "IPAddressDeny=any" in text
    assert "IPAddressAllow=localhost" in text
    assert "ReadWritePaths=/var/lib/polemica-ai-agent" in text
    assert "ReadOnlyPaths=/var/lib/polemica-ai-agent/research-cache" in text
    assert "ReadOnlyPaths=/run/polemica-agent-compute" in text
    assert "FANTASY_BEARER_TOKEN" not in text
    assert "POLEMICA_USERNAME" not in text
    assert "POLEMICA_PASSWORD" not in text


def test_timer_and_runner_are_inert_by_default() -> None:
    timer = (SYSTEMD / "polemica-agent-run.timer").read_text()
    runner = (SYSTEMD / "polemica-agent-run.service").read_text()
    assert "OnCalendar=hourly" in timer
    assert "[Install]" in timer and "WantedBy=timers.target" in timer
    assert "WRITE_ENABLED=" not in runner
    assert "CODEX_BINARY=/home/codex/.local/bin/codex" in runner
    assert "POLEMICA_AGENT_RUN_LOCK=/var/lib/polemica-ai-agent-runner/run.lock" in runner
    assert "ExecStart=/opt/polemica-ai-agent/venv/bin/polemica-agent-run" in runner
    assert "POLEMICA_AGENT_DATABASE" not in runner
    assert "CODEX_HOME=/var/lib/polemica-ai-agent-runner/codex-home" in runner
    assert "ReadWritePaths=/var/lib/polemica-ai-agent-runner" in runner
    assert "ReadWritePaths=/var/lib/polemica-ai-agent-runner /home/codex/.codex" not in runner
    assert "BindPaths=/home/codex/.codex/auth.json:/var/lib/polemica-ai-agent-runner/codex-home/auth.json" in runner
    assert "3300" in runner
    assert "polemica-agent-compute-mcp.service" in runner


def test_system_installer_is_root_only_and_leaves_units_disabled() -> None:
    installer = ROOT / "deploy" / "install-system-disabled.sh"
    source = installer.read_text()
    assert '"$(id -u)" -ne 0' in source
    assert "systemctl daemon-reload" in source
    assert "systemctl enable" not in source
    assert "systemctl start" not in source
    assert "systemctl restart" not in source
    assert 'chmod 0755 "$install_prefix/deploy/preflight.sh"' in source
    assert '"$runner_state/codex-home/auth.json"' in source
    assert "compute_user=polemica-agent-compute" in source
    assert 'usermod -a -G "$compute_group" "$broker_user"' in source
    assert "compute-mcp.env" in source
    assert "polemica-agent-compute-worker.service" in source
    assert "polemica-agent-compute-mcp.service" in source
    assert "FANTASY_BEARER_TOKEN" not in source
    assert "POLEMICA_PASSWORD" not in source
    subprocess.run(["sh", "-n", str(installer)], check=True)


def test_installer_only_stages_files(tmp_path: Path) -> None:
    installer = ROOT / "deploy" / "install-disabled.sh"
    result = subprocess.run([str(installer), str(tmp_path / "stage")], check=True, text=True, capture_output=True)
    staged = tmp_path / "stage"
    assert (staged / "systemd" / "polemica-agent-run.timer").exists()
    assert not list(staged.rglob("*.env"))
    assert "No users, secrets, system units, timers, cron entries, services, or network calls" in result.stdout
    source = installer.read_text()
    assert "systemctl" not in source
    assert "sudo" not in source
