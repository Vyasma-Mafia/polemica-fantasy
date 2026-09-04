from __future__ import annotations

import dataclasses
import os
from collections.abc import Mapping
from pathlib import Path


@dataclasses.dataclass(frozen=True)
class RunnerSettings:
    database_path: Path
    lock_path: Path
    run_timeout_seconds: int = 3300

    def __post_init__(self) -> None:
        if not self.database_path.expanduser().is_absolute():
            raise ValueError("database path must be absolute")
        if not self.lock_path.expanduser().is_absolute():
            raise ValueError("lock path must be absolute")
        if not 1 <= self.run_timeout_seconds < 3600:
            raise ValueError("run timeout must be in [1, 3600) seconds")

    @classmethod
    def from_environment(cls, environment: Mapping[str, str] | None = None) -> "RunnerSettings":
        env = environment if environment is not None else os.environ
        return cls(
            database_path=Path(
                env.get("POLEMICA_AGENT_DATABASE", "/var/lib/polemica-ai-agent/agent.sqlite3"),
            ),
            lock_path=Path(
                env.get("POLEMICA_AGENT_RUN_LOCK", "/var/lib/polemica-ai-agent/run.lock"),
            ),
            run_timeout_seconds=int(env.get("POLEMICA_AGENT_RUN_TIMEOUT_SECONDS", "3300")),
        )
