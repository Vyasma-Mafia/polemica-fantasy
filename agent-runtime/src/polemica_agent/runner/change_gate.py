"""Read-only wake-up filter. Never authorizes an action or supplies ACT evidence."""
from __future__ import annotations

import datetime as dt
import json
import math
import os
from pathlib import Path
from typing import Any

import anyio
from mcp import Client

from polemica_agent.common.canonical import payload_hash


READS = {
    "fantasy_get_my_profile": dict,
    "fantasy_get_my_cards": list,
    "fantasy_get_my_teams": list,
    "fantasy_list_store_packs": list,
    "fantasy_get_achievement_catalog": dict,
    "fantasy_get_economy_info": dict,
    "fantasy_list_open_series": list,
    "fantasy_get_my_listings": list,
    "fantasy_get_periodic_rating_current": (dict, type(None)),
    "fantasy_get_periodic_rating_rewards": list,
}


def _semantic(value: Any) -> Any:
    # Strip observation clocks, NOT business timestamps (deadlines, soldAt, etc.).
    if isinstance(value, dict):
        return {k: _semantic(v) for k, v in value.items()
                if k not in {"observedAt", "asOf", "generatedAt", "fetchedAt"}}
    if isinstance(value, list):
        return [_semantic(v) for v in value]
    return value


def read_state(urls: dict[str, str]) -> dict[str, Any]:
    async def read() -> dict[str, Any]:
        async def call(client: Client, tool: str, args: dict) -> Any:
            result = await client.call_tool(tool, args)
            if result.is_error:
                raise ValueError("wake-up read failed")
            value = result.structured_content
            if isinstance(value, dict) and set(value) == {"result"}:
                value = value["result"]
            if not isinstance(value, dict) or "data" not in value:
                raise ValueError("invalid wake-up envelope")
            return value["data"]

        with anyio.fail_after(50):
            async with Client(urls["fantasy"], read_timeout_seconds=12) as client:
                state = {}
                for tool, shape in READS.items():
                    value = await call(client, tool, {})
                    if not isinstance(value, shape):
                        raise ValueError("invalid wake-up state")
                    if isinstance(value, list) and any(not isinstance(x, dict) for x in value):
                        raise ValueError("invalid wake-up list")
                    state[tool] = value
                series = state["fantasy_list_open_series"]
                if len(series) > 20:
                    raise ValueError("wake-up series bound exceeded")
                state["seriesDetails"] = []
                for row in series:
                    if type(row.get("seriesId")) is not int:
                        raise ValueError("invalid series identity")
                    detail = await call(client, "fantasy_get_series", {"series_id": row["seriesId"]})
                    if not isinstance(detail, dict) or not isinstance(detail.get("players"), list):
                        raise ValueError("invalid series roster")
                    leagues = await call(client, "fantasy_list_series_leagues", {"series_id": row["seriesId"]})
                    if not isinstance(leagues, list):
                        raise ValueError("invalid leagues")
                    state["seriesDetails"].append({"series": detail, "leagues": leagues})
                # A bounded market watch, not a claim of exhaustive market coverage.
                market = await call(client, "fantasy_list_marketplace", {"page": 0, "size": 100})
                if not isinstance(market, dict) or not isinstance(market.get("content"), list):
                    raise ValueError("invalid market page")
                state["marketWatch"] = market
            async with Client(urls["memory"], read_timeout_seconds=12) as client:
                result = await client.call_tool("read_developer_notes", {"compact": False})
                if result.is_error:
                    raise ValueError("mailbox read failed")
                state["developerNotes"] = result.structured_content or [
                    getattr(item, "text", "") for item in result.content]
        return state
    return anyio.run(read)


def evaluate(state: dict, baseline: dict | None, config_hash: str, now: dt.datetime,
             max_interval: int) -> tuple[bool, str, str]:
    signature = payload_hash(_semantic(state))
    for series in state["fantasy_list_open_series"]:
        try:
            deadline = dt.datetime.fromisoformat(series["teamDeadline"].replace("Z", "+00:00"))
            if deadline.tzinfo is None:
                raise ValueError("naive deadline")
            if (deadline - now).total_seconds() <= 7200:
                return True, "NEAR_DEADLINE", signature
        except (KeyError, ValueError, TypeError, AttributeError):
            return True, "UNKNOWN_DEADLINE", signature
    if baseline is None:
        return True, "NO_BASELINE", signature
    try:
        stamp = baseline["completedAt"]
        if type(stamp) not in (int, float) or not math.isfinite(stamp):
            return True, "INVALID_BASELINE", signature
        age = now.timestamp() - stamp
        if age < 0 or age >= max_interval:
            return True, "EXPLORATION_DUE", signature
        if baseline.get("idleSafe") is not True:
            return True, "WORK_REMAINS", signature
        if baseline["configHash"] != config_hash:
            return True, "CONFIG_CHANGED", signature
        if baseline["signature"] != signature:
            return True, "STATE_CHANGED", signature
    except (KeyError, TypeError):
        return True, "INVALID_BASELINE", signature
    return False, "UNCHANGED", signature


class ChangeGate:
    def __init__(self, path: Path):
        self.path = path

    def load(self) -> dict | None:
        try:
            fd = os.open(self.path, os.O_RDONLY | os.O_NOFOLLOW)
            with os.fdopen(fd) as handle:
                value = json.load(handle)
            return value if isinstance(value, dict) else None
        except (OSError, ValueError):
            return None

    def invalidate(self) -> None:
        # A failed/interrupted model run cannot leave a previously idle baseline usable.
        self.save({"idleSafe": False})

    def save(self, value: dict) -> None:
        import tempfile
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        fd, temporary = tempfile.mkstemp(prefix=".gate-", dir=self.path.parent)
        try:
            with os.fdopen(fd, "w") as handle:
                json.dump(value, handle)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, self.path)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)
