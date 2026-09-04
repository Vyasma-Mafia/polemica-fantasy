from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Any, Sequence


def fetchone(database_path: Path, sql: str, args: Sequence[Any] = ()) -> sqlite3.Row | None:
    rows = fetchall(database_path, sql, args)
    return rows[0] if rows else None


def fetchall(database_path: Path, sql: str, args: Sequence[Any] = ()) -> list[sqlite3.Row]:
    uri = f"file:{database_path}?mode=ro"
    connection = sqlite3.connect(uri, uri=True, timeout=5.0)
    connection.row_factory = sqlite3.Row
    try:
        connection.execute("PRAGMA query_only=ON")
        return list(connection.execute(sql, tuple(args)).fetchall())
    finally:
        connection.close()
