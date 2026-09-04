from __future__ import annotations

import os
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SYSTEMD = ROOT / "deploy" / "systemd"


def test_units_are_disabled_loopback_and_hardened() -> None:
    services = list(SYSTEMD.glob("*.service"))
    assert len(services) == 5
    users = set()
    for path in services:
        text = path.read_text()
        users.add(next(line for line in text.splitlines() if line.startswith("User=")))
        assert "ProtectSystem=strict" in text
        assert "ProtectProc=invisible" in text
        assert "ProcSubset=pid" in text
        assert "InaccessiblePaths=" in text and ".ssh" in text and "/proc/" in text
        assert "systemctl" not in text
    assert users == {"User=polemica-agent-broker", "User=codex"}
    assert all("User=polemica-agent-broker" in path.read_text() for path in SYSTEMD.glob("*-mcp.service"))
    for path in SYSTEMD.glob("*-mcp.service"):
        assert "MCP_BIND_HOST=127.0.0.1" in path.read_text()
        assert "EnvironmentFile=/etc/polemica-ai-agent/" in path.read_text()
        assert "Requires=polemica-agent-migrate.service" in path.read_text()
        assert "ReadWritePaths=/var/lib/polemica-ai-agent" in path.read_text()


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
    assert "ReadWritePaths=/var/lib/polemica-ai-agent-runner" in runner
    assert "3300" in runner


def test_system_installer_is_root_only_and_leaves_units_disabled() -> None:
    installer = ROOT / "deploy" / "install-system-disabled.sh"
    source = installer.read_text()
    assert '"$(id -u)" -ne 0' in source
    assert "systemctl daemon-reload" in source
    assert "systemctl enable" not in source
    assert "systemctl start" not in source
    assert "systemctl restart" not in source
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
