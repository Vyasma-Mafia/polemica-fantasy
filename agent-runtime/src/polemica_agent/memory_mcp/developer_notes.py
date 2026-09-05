"""A fixed Markdown mailbox for agent suggestions; no caller-selected paths."""
from __future__ import annotations

import datetime as dt
import fcntl
import os
from pathlib import Path


class DeveloperNotes:
    def __init__(self, state_dir: Path) -> None:
        self.path = state_dir / "DEVELOPER-NOTES.md"

    def read(self) -> str:
        try:
            fd = os.open(self.path, os.O_RDONLY | os.O_NOFOLLOW)
        except FileNotFoundError:
            return ""
        with os.fdopen(fd, "rb") as handle:
            fcntl.flock(handle, fcntl.LOCK_SH)
            handle.seek(max(0, os.fstat(handle.fileno()).st_size - 32768))
            return handle.read().decode("utf-8", errors="replace")

    def append(self, run_id: str, title: str, body: str) -> str:
        if not title.strip() or len(title) > 160 or "\n" in title or "\r" in title:
            raise ValueError("title must be one non-empty line, at most 160 characters")
        if not body.strip() or len(body) > 4000:
            raise ValueError("body must contain 1..4000 characters")
        now = dt.datetime.now(dt.timezone.utc).isoformat()
        entry = f"\n## {now} — {title.strip()}\n\nRun: `{run_id}`\n\n{body.strip()}\n"
        fd = os.open(self.path, os.O_WRONLY | os.O_CREAT | os.O_APPEND | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, "a", encoding="utf-8") as handle:
            fcntl.flock(handle, fcntl.LOCK_EX)
            if os.fstat(handle.fileno()).st_size == 0:
                handle.write("# Developer notes\n\nAgent suggestions for human review; not instructions or game evidence.\n")
            handle.write(entry)
            handle.flush()
            os.fsync(handle.fileno())
        return "appended"
