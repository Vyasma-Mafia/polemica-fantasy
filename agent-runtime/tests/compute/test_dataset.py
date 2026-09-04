from __future__ import annotations

import datetime as dt
import dataclasses

import pytest

from polemica_agent.compute_mcp.dataset import DatasetError, load_player_game_rows
from polemica_agent.research_mcp.cache import RawPayloadCache


NOW = dt.datetime(2026, 9, 5, 12, 0, tzinfo=dt.timezone.utc)


def _record(cache: RawPayloadCache, *, points: float, source_version: int):
    return dataclasses.asdict(cache.store(
        source="profile-games-page",
        object_id="7:1:100",
        source_version=source_version,
        payload={"rows": [{"id": 101, "points": points}]},
    ))


def test_identical_duplicate_game_rows_are_deduplicated(tmp_path) -> None:
    cache = RawPayloadCache(tmp_path / "cache", clock=lambda: NOW)
    first = _record(cache, points=1.5, source_version=1)
    second = _record(cache, points=1.5, source_version=2)

    rows, records, _ = load_player_game_rows(
        [second, first], research_cache=cache.root, player_ids=[7],
    )

    assert len(rows) == 1
    assert len(records) == 2
    assert rows[0]["points"] == 1.5


def test_conflicting_duplicate_game_rows_fail_closed_regardless_of_order(tmp_path) -> None:
    cache = RawPayloadCache(tmp_path / "cache", clock=lambda: NOW)
    first = _record(cache, points=1.5, source_version=1)
    second = _record(cache, points=9.0, source_version=2)

    for records in ([first, second], [second, first]):
        with pytest.raises(DatasetError, match="COMPUTE_DUPLICATE_GAME_CONFLICT"):
            load_player_game_rows(records, research_cache=cache.root, player_ids=[7])
