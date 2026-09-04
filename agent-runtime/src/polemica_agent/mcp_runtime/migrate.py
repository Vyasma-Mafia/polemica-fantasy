from __future__ import annotations

import os
from pathlib import Path

from polemica_agent.common.storage import AuditStore


def main() -> int:
    value = os.environ.get("POLEMICA_AGENT_DATABASE")
    if not value:
        raise RuntimeError("POLEMICA_AGENT_DATABASE is required")
    path = Path(value)
    if not path.is_absolute():
        raise RuntimeError("POLEMICA_AGENT_DATABASE must be absolute")
    with AuditStore(path):
        pass
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
