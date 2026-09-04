from __future__ import annotations

import multiprocessing
from pathlib import Path

import pytest

from polemica_agent.runner.lock import RunLock


def _try_lock(path: str, queue: multiprocessing.Queue) -> None:  # type: ignore[type-arg]
    try:
        with RunLock(Path(path), timeout_seconds=0.1):
            queue.put("acquired")
    except TimeoutError:
        queue.put("blocked")


def test_lock_requires_absolute_path_and_sub_hour_timeout() -> None:
    with pytest.raises(ValueError, match="absolute"):
        RunLock(Path("relative.lock"))
    with pytest.raises(ValueError, match="3600"):
        RunLock(Path("/tmp/test.lock"), timeout_seconds=3600)


def test_lock_excludes_another_process(tmp_path: Path) -> None:
    path = (tmp_path / "runner.lock").resolve()
    queue: multiprocessing.Queue = multiprocessing.Queue()
    with RunLock(path):
        process = multiprocessing.Process(target=_try_lock, args=(str(path), queue))
        process.start()
        process.join(timeout=3)
        assert process.exitcode == 0
        assert queue.get(timeout=1) == "blocked"
    with RunLock(path, timeout_seconds=0.1):
        assert path.read_text(encoding="utf-8").strip().isdigit()
