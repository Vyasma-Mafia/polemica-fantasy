from __future__ import annotations

import fcntl
import os
import time
from pathlib import Path
from types import TracebackType


class RunLock:
    """Absolute-path, process-wide non-overlap lock for an hourly runner."""

    def __init__(self, path: Path, *, timeout_seconds: float = 0) -> None:
        path = path.expanduser()
        if not path.is_absolute():
            raise ValueError("lock path must be absolute")
        if timeout_seconds < 0 or timeout_seconds >= 3600:
            raise ValueError("lock timeout must be in [0, 3600) seconds")
        self.path = path
        self.timeout_seconds = timeout_seconds
        self._handle = None

    def acquire(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        deadline = time.monotonic() + self.timeout_seconds
        handle = self.path.open("a+", encoding="utf-8")
        while True:
            try:
                fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    handle.close()
                    raise TimeoutError(f"runner lock is already held: {self.path}")
                time.sleep(min(0.05, max(0, deadline - time.monotonic())))
        handle.seek(0)
        handle.truncate()
        handle.write(f"{os.getpid()}\n")
        handle.flush()
        os.fsync(handle.fileno())
        self._handle = handle

    def release(self) -> None:
        if self._handle is None:
            return
        fcntl.flock(self._handle.fileno(), fcntl.LOCK_UN)
        self._handle.close()
        self._handle = None

    def __enter__(self) -> "RunLock":
        self.acquire()
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        self.release()
