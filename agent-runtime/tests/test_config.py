from pathlib import Path

import pytest

from polemica_agent.common.config import RunnerSettings


def test_runner_defaults_are_absolute_and_under_one_hour() -> None:
    settings = RunnerSettings.from_environment({})
    assert settings.database_path.is_absolute()
    assert settings.lock_path.is_absolute()
    assert settings.run_timeout_seconds == 3300


@pytest.mark.parametrize("timeout", [0, 3600, 7200])
def test_runner_rejects_invalid_timeout(timeout: int) -> None:
    with pytest.raises(ValueError, match="timeout"):
        RunnerSettings(Path("/tmp/db"), Path("/tmp/lock"), timeout)


def test_runner_rejects_relative_paths() -> None:
    with pytest.raises(ValueError, match="absolute"):
        RunnerSettings(Path("db"), Path("/tmp/lock"))
