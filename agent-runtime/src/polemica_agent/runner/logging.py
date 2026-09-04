from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

from polemica_agent.common.canonical import redact


class RedactedJsonlLog:
    def __init__(self, path: Path) -> None:
        if not path.is_absolute():
            raise ValueError("log path must be absolute")
        path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        self._handle = path.open("a", encoding="utf-8")
        os.chmod(path, 0o600)

    def write_line(self, raw: str) -> None:
        try:
            event: Any = json.loads(raw)
        except json.JSONDecodeError:
            event = {"type": "unparseable_output", "length": len(raw)}
        self._handle.write(json.dumps(redact(event), ensure_ascii=False, sort_keys=True) + "\n")
        self._handle.flush()
        os.fsync(self._handle.fileno())

    def write_event(self, event: Any) -> None:
        self._handle.write(json.dumps(redact(event), ensure_ascii=False, sort_keys=True) + "\n")
        self._handle.flush()
        os.fsync(self._handle.fileno())

    def close(self) -> None:
        self._handle.close()

    def __enter__(self) -> "RedactedJsonlLog":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()
