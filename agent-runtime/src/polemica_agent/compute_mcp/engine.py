"""Pure compute-v1 operations over normalized Polemica player-game rows.

This module intentionally has no filesystem, environment, clock, subprocess, or
network integration.  Its complete input is the supplied JSON-compatible value.
"""

from __future__ import annotations

import json
import math
import random
from collections.abc import Mapping, Sequence
from typing import Any


ENGINE_VERSION = "compute-v1"
MAX_ROWS = 20_000
MAX_PLAYERS = 100
MAX_QUANTILES = 5
MAX_FEATURES = 4
MAX_GAMES = 20
MAX_TRIALS = 10_000
MAX_OUTPUT_BYTES = 1024 * 1024

OPERATIONS = frozenset(
    {
        "describe_player_points",
        "correlate_player_metrics",
        "simulate_player_totals",
    }
)
FEATURES = frozenset({"points", "mmr", "win", "isBlack"})
ROW_FIELDS = frozenset({"playerId", "points", "mmr", "win", "roleCode"})


class ComputeError(ValueError):
    """A bounded, caller-safe contract or computation error."""

    def __init__(self, code: str) -> None:
        self.code = code
        super().__init__(code)


def execute(operation: str, payload: Mapping[str, Any]) -> dict[str, Any]:
    """Execute one fixed operation and return a canonical-JSON-safe result."""
    if not isinstance(operation, str) or operation not in OPERATIONS:
        raise ComputeError("UNKNOWN_OPERATION")
    if not isinstance(payload, Mapping):
        raise ComputeError("INVALID_PAYLOAD")

    if operation == "describe_player_points":
        result = _describe_player_points(payload)
    elif operation == "correlate_player_metrics":
        result = _correlate_player_metrics(payload)
    else:
        result = _simulate_player_totals(payload)

    encoded = _canonical_bytes(result)
    if len(encoded) > MAX_OUTPUT_BYTES:
        raise ComputeError("OUTPUT_TOO_LARGE")
    return result


def canonical_result_bytes(result: Mapping[str, Any]) -> bytes:
    """Encode an engine result deterministically for audit and worker transport."""
    encoded = _canonical_bytes(result)
    if len(encoded) > MAX_OUTPUT_BYTES:
        raise ComputeError("OUTPUT_TOO_LARGE")
    return encoded


def _describe_player_points(payload: Mapping[str, Any]) -> dict[str, Any]:
    _require_keys(payload, {"rows", "playerIds", "quantiles"})
    rows = _rows(payload["rows"])
    player_ids = _player_ids(payload["playerIds"])
    quantiles = _quantiles(payload["quantiles"])
    by_player = _selected_rows(rows, player_ids)

    players: list[dict[str, Any]] = []
    for player_id in player_ids:
        selected = by_player[player_id]
        values = sorted(row["points"] for row in selected if row["points"] is not None)
        players.append(
            {
                "playerId": player_id,
                "sampleSize": len(selected),
                "pointsSampleSize": len(values),
                "missingPoints": len(selected) - len(values),
                "mean": _mean(values),
                "minimum": values[0] if values else None,
                "maximum": values[-1] if values else None,
                "quantiles": [
                    {"probability": probability, "value": _quantile(values, probability)}
                    for probability in quantiles
                ],
            }
        )
    return {
        "engineVersion": ENGINE_VERSION,
        "operation": "describe_player_points",
        "quantileMethod": "linear-r7",
        "players": players,
    }


def _correlate_player_metrics(payload: Mapping[str, Any]) -> dict[str, Any]:
    _require_keys(payload, {"rows", "playerIds", "features"})
    rows = _rows(payload["rows"])
    player_ids = _player_ids(payload["playerIds"])
    features = _features(payload["features"])
    selected = [row for group in _selected_rows(rows, player_ids).values() for row in group]

    correlations: list[list[float | None]] = []
    sample_sizes: list[list[int]] = []
    for left in features:
        correlation_row: list[float | None] = []
        size_row: list[int] = []
        for right in features:
            pairs = [
                (left_value, right_value)
                for row in selected
                if (left_value := _feature(row, left)) is not None
                and (right_value := _feature(row, right)) is not None
            ]
            size_row.append(len(pairs))
            correlation_row.append(_pearson(pairs))
        correlations.append(correlation_row)
        sample_sizes.append(size_row)
    return {
        "engineVersion": ENGINE_VERSION,
        "operation": "correlate_player_metrics",
        "method": "pearson-pairwise-complete",
        "features": features,
        "correlations": correlations,
        "sampleSizes": sample_sizes,
    }


def _simulate_player_totals(payload: Mapping[str, Any]) -> dict[str, Any]:
    _require_keys(payload, {"rows", "playerIds", "gamesCount", "trials", "seed"})
    rows = _rows(payload["rows"])
    player_ids = _player_ids(payload["playerIds"])
    games_count = _bounded_int(payload["gamesCount"], 1, MAX_GAMES, "INVALID_GAMES_COUNT")
    trials = _bounded_int(payload["trials"], 1, MAX_TRIALS, "INVALID_TRIALS")
    seed = _bounded_int(payload["seed"], 0, (1 << 63) - 1, "INVALID_SEED")
    by_player = _selected_rows(rows, player_ids)
    samples: dict[int, list[float]] = {}
    for player_id in player_ids:
        values = sorted(row["points"] for row in by_player[player_id] if row["points"] is not None)
        if not values:
            raise ComputeError("PLAYER_HAS_NO_POINTS")
        samples[player_id] = values

    rng = random.Random(seed)
    totals = {player_id: [] for player_id in player_ids}
    top_credits = {player_id: 0.0 for player_id in player_ids}
    for _ in range(trials):
        trial_totals: dict[int, float] = {}
        for player_id in player_ids:
            history = samples[player_id]
            total = math.fsum(history[rng.randrange(len(history))] for _ in range(games_count))
            totals[player_id].append(total)
            trial_totals[player_id] = total
        top = max(trial_totals.values())
        leaders = [player_id for player_id in player_ids if trial_totals[player_id] == top]
        credit = 1.0 / len(leaders)
        for player_id in leaders:
            top_credits[player_id] += credit

    players = []
    for player_id in player_ids:
        simulated = sorted(totals[player_id])
        players.append(
            {
                "playerId": player_id,
                "historicalSampleSize": len(samples[player_id]),
                "expected": _mean(simulated),
                "p10": _quantile(simulated, 0.1),
                "p50": _quantile(simulated, 0.5),
                "p90": _quantile(simulated, 0.9),
                "topRate": top_credits[player_id] / trials,
            }
        )
    return {
        "engineVersion": ENGINE_VERSION,
        "operation": "simulate_player_totals",
        "method": "per-player empirical sampling with replacement; ties split equally",
        "quantileMethod": "linear-r7",
        "gamesCount": games_count,
        "trials": trials,
        "seed": seed,
        "players": players,
    }


def _rows(value: Any) -> list[dict[str, int | float | None]]:
    if not _is_sequence(value) or len(value) > MAX_ROWS:
        raise ComputeError("INVALID_ROWS")
    result: list[dict[str, int | float | None]] = []
    for item in value:
        if not isinstance(item, Mapping) or set(item) != ROW_FIELDS:
            raise ComputeError("INVALID_ROW")
        player_id = _bounded_int(item["playerId"], 1, (1 << 63) - 1, "INVALID_PLAYER_ID")
        points = _nullable_number(item["points"], "INVALID_POINTS")
        mmr = _nullable_number(item["mmr"], "INVALID_MMR")
        win = item["win"]
        if win is not None and (isinstance(win, bool) or not isinstance(win, int) or win not in (0, 1)):
            raise ComputeError("INVALID_WIN")
        role_code = item["roleCode"]
        if role_code is not None and (
            isinstance(role_code, bool) or not isinstance(role_code, int) or role_code not in (0, 1, 2, 3)
        ):
            raise ComputeError("INVALID_ROLE_CODE")
        result.append(
            {"playerId": player_id, "points": points, "mmr": mmr, "win": win, "roleCode": role_code}
        )
    return result


def _player_ids(value: Any) -> list[int]:
    if not _is_sequence(value) or not 1 <= len(value) <= MAX_PLAYERS:
        raise ComputeError("INVALID_PLAYER_IDS")
    result = [_bounded_int(item, 1, (1 << 63) - 1, "INVALID_PLAYER_ID") for item in value]
    if len(result) != len(set(result)):
        raise ComputeError("DUPLICATE_PLAYER_ID")
    return sorted(result)


def _quantiles(value: Any) -> list[float]:
    if not _is_sequence(value) or len(value) > MAX_QUANTILES:
        raise ComputeError("INVALID_QUANTILES")
    result = [_number(item, "INVALID_QUANTILE") for item in value]
    if any(item < 0.0 or item > 1.0 for item in result) or len(result) != len(set(result)):
        raise ComputeError("INVALID_QUANTILES")
    return sorted(result)


def _features(value: Any) -> list[str]:
    if not _is_sequence(value) or not 2 <= len(value) <= MAX_FEATURES:
        raise ComputeError("INVALID_FEATURES")
    result = list(value)
    if any(not isinstance(item, str) or item not in FEATURES for item in result):
        raise ComputeError("INVALID_FEATURES")
    if len(result) != len(set(result)):
        raise ComputeError("INVALID_FEATURES")
    return result


def _selected_rows(rows: Sequence[dict[str, Any]], player_ids: Sequence[int]) -> dict[int, list[dict[str, Any]]]:
    selected = {player_id: [] for player_id in player_ids}
    for row in rows:
        if row["playerId"] in selected:
            selected[row["playerId"]].append(row)
    return selected


def _feature(row: Mapping[str, Any], feature: str) -> float | None:
    if feature == "isBlack":
        role = row["roleCode"]
        return None if role is None else float(role in (0, 1))
    value = row[feature]
    return None if value is None else float(value)


def _pearson(pairs: Sequence[tuple[float, float]]) -> float | None:
    if len(pairs) < 2:
        return None
    left_mean = math.fsum(left for left, _ in pairs) / len(pairs)
    right_mean = math.fsum(right for _, right in pairs) / len(pairs)
    covariance = math.fsum((left - left_mean) * (right - right_mean) for left, right in pairs)
    left_sum = math.fsum((left - left_mean) ** 2 for left, _ in pairs)
    right_sum = math.fsum((right - right_mean) ** 2 for _, right in pairs)
    denominator = math.sqrt(left_sum * right_sum)
    if denominator == 0.0:
        return None
    correlation = covariance / denominator
    return max(-1.0, min(1.0, correlation))


def _mean(values: Sequence[float]) -> float | None:
    return math.fsum(values) / len(values) if values else None


def _quantile(values: Sequence[float], probability: float) -> float | None:
    if not values:
        return None
    position = (len(values) - 1) * probability
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return values[lower]
    weight = position - lower
    return values[lower] * (1.0 - weight) + values[upper] * weight


def _nullable_number(value: Any, code: str) -> float | None:
    return None if value is None else _number(value, code)


def _number(value: Any, code: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ComputeError(code)
    converted = float(value)
    if not math.isfinite(converted):
        raise ComputeError(code)
    return converted


def _bounded_int(value: Any, minimum: int, maximum: int, code: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= maximum:
        raise ComputeError(code)
    return value


def _require_keys(value: Mapping[str, Any], expected: set[str]) -> None:
    if set(value) != expected:
        raise ComputeError("INVALID_PAYLOAD")


def _is_sequence(value: Any) -> bool:
    return isinstance(value, Sequence) and not isinstance(value, (str, bytes, bytearray))


def _canonical_bytes(value: Mapping[str, Any]) -> bytes:
    try:
        return json.dumps(
            value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
        ).encode("utf-8")
    except (TypeError, ValueError) as error:
        raise ComputeError("INVALID_RESULT") from error
