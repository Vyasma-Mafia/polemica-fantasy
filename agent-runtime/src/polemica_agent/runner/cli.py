from __future__ import annotations

import argparse

from .orchestrator import run_once
from .settings import RuntimeSettings


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run one bounded Polemica AI agent turn")
    parser.add_argument("--force", action="store_true", help="Bypass unchanged-state gate, never safety checks")
    args = parser.parse_args(argv)
    run_once(RuntimeSettings.from_env(), force=args.force)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
