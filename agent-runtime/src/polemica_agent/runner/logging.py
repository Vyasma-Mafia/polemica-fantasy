from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

from polemica_agent.common.canonical import redact

USAGE_FIELDS = frozenset({
    "input_tokens", "cached_input_tokens", "cache_write_input_tokens",
    "output_tokens", "reasoning_output_tokens",
})


def safe_event(event: Any) -> Any:
    clean = redact(event)
    # Only CLI's top-level completion counters, never arbitrary MCP payload keys.
    if isinstance(event, dict) and event.get("type") == "turn.completed":
        usage = event.get("usage")
        if isinstance(usage, dict):
            for key in USAGE_FIELDS:
                value = usage.get(key)
                if type(value) is int and value >= 0:
                    clean["usage"][key] = value
    return clean


class RedactedJsonlLog:
    def __init__(self, path: Path) -> None:
        if not path.is_absolute():
            raise ValueError("log path must be absolute")
        path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        self._handle = path.open("a", encoding="utf-8")
        os.chmod(path, 0o600)
        self.usage: dict[str, int] = {}
        self.idle_safe = False

    def write_line(self, raw: str) -> None:
        try:
            event: Any = json.loads(raw)
        except json.JSONDecodeError:
            event = {"type": "unparseable_output", "length": len(raw)}
        self.write_event(event)

    def write_event(self, event: Any) -> None:
        clean = safe_event(event)
        if (isinstance(clean, dict) and clean.get("type") == "turn.completed"
                and isinstance(clean.get("usage"), dict)):
            for key, value in clean.get("usage", {}).items():
                if key in USAGE_FIELDS and type(value) is int:
                    self.usage[key] = self.usage.get(key, 0) + value
        if isinstance(event, dict) and event.get("type") == "item.completed":
            item = event.get("item", {})
            if isinstance(item, dict) and item.get("type") == "agent_message":
                self.idle_safe = False
                try:
                    report = json.loads(item.get("text", ""))
                    self.idle_safe = isinstance(report, dict) and report.get("idleSafe") is True
                except (ValueError, TypeError):
                    pass
        self._handle.write(json.dumps(clean, ensure_ascii=False, sort_keys=True) + "\n")
        self._handle.flush()
        os.fsync(self._handle.fileno())

    def close(self) -> None:
        self._handle.close()

    def __enter__(self) -> "RedactedJsonlLog":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()
