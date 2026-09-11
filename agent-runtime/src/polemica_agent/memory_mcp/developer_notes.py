"""A fixed Markdown mailbox for agent suggestions; no caller-selected paths."""
from __future__ import annotations

import datetime as dt
import fcntl
import hashlib
import os
from pathlib import Path


class DeveloperNotes:
    def __init__(self, state_dir: Path) -> None:
        self.path = state_dir / "DEVELOPER-NOTES.md"

    def read(self, *, compact: bool = True, offset: int = 0, limit: int = 6000,
             known_hash: str | None = None) -> str:
        if type(offset) is not int or offset < 0 or type(limit) is not int or not 256 <= limit <= 32768:
            raise ValueError("offset must be nonnegative; limit must be 256..32768 characters")
        try:
            fd = os.open(self.path, os.O_RDONLY | os.O_NOFOLLOW)
        except FileNotFoundError:
            return ""
        with os.fdopen(fd, "rb") as handle:
            fcntl.flock(handle, fcntl.LOCK_SH)
            raw = handle.read()
        digest = hashlib.sha256(raw).hexdigest()
        if known_hash == digest:
            return f"mailboxSha256: {digest}\nunchanged: true"
        text = raw.decode("utf-8", errors="replace")
        metadata = f"mailboxSha256: {digest}\ntotalCharacters: {len(text)}\n"
        if not compact:
            page = text[offset:offset + limit]
            next_offset = offset + len(page)
            return metadata + f"offset: {offset}\nnextOffset: {next_offset if next_offset < len(text) else 'none'}\n\n" + page
        if len(text) <= limit:
            return metadata + text
        head_size = min(1500, limit // 3)
        return (metadata + "truncated: true; omitted text may contain unresolved issues. "
                "Use compact=False with offset/limit to read every page.\n\n" + text[:head_size]
                + "\n\n[... middle omitted; latest text follows ...]\n\n" + text[-(limit - head_size):])

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
