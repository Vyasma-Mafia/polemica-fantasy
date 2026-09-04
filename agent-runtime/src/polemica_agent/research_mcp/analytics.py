"""Deterministic aggregates over saved Polemica JSON payloads."""

from __future__ import annotations

import math
import statistics
from collections import Counter
from datetime import datetime
from typing import Any, Iterable, Mapping, Sequence


ROLE_NAMES = {0: "DON", 1: "MAFIA", 2: "PEACE", 3: "SHERIFF"}
RED = {2, 3}
BLACK = {0, 1}
PERK_IDS = (
    "sniper",
    "winThreeToThree",
    "findSheriff",
    "sheriffCheckBlack",
    "voteOutSheriffDay1Or2",
    "voteForBlack",
    "strongCity",
    "firstKickedFullGuess",
    "votingOnlyForBlack",
    "winWithoutCritic",
    "ninja",
    "crowned",
    "lastHeroGuess",
)


def player_statistics(rows: Sequence[Mapping[str, Any]], *, complete: bool = True) -> dict[str, Any]:
    valid = [row for row in rows if isinstance(row, Mapping)]
    points = [_number(row.get("points")) for row in valid]
    point_values = [value for value in points if value is not None]
    results = [_result_code(row.get("result")) for row in valid]
    known_results = [value for value in results if value is not None]
    wins = sum(1 for value in known_results if value in {"win", "success", "victory"})
    mmr = [_mmr(row.get("mmr")) for row in valid]
    known_mmr = [value for value in mmr if value is not None]
    return {
        "sampleSize": len(valid),
        "pointsSampleSize": len(point_values),
        "resultSampleSize": len(known_results),
        "averagePoints": _mean(point_values),
        "medianPoints": _median(point_values),
        "pointsStdDev": _pstdev(point_values),
        "winRate": wins / len(known_results) if known_results else None,
        "averageMmr": _mean(known_mmr),
        "complete": complete,
    }


def recent_form(rows: Sequence[Mapping[str, Any]], window: int, *, complete: bool = True) -> dict[str, Any]:
    if not 1 <= window <= 500:
        raise ValueError("window must be in 1..500")
    ordered = sorted(
        (row for row in rows if isinstance(row, Mapping)),
        key=lambda row: _date_key(row.get("date_start")),
        reverse=True,
    )[:window]
    result = player_statistics(ordered, complete=complete)
    result["window"] = window
    result["gameIds"] = [row.get("id") for row in ordered]
    return result


def role_distribution(rows: Sequence[Mapping[str, Any]], *, complete: bool = True) -> dict[str, Any]:
    roles = [_profile_role(row.get("role")) for row in rows if isinstance(row, Mapping)]
    known = [role for role in roles if role is not None]
    counts = Counter(known)
    total = len(known)
    return {
        "sampleSize": total,
        "roles": {
            role: {"count": counts[role], "rate": counts[role] / total if total else None}
            for role in sorted(counts)
        },
        "unknownRoles": len(roles) - total,
        "complete": complete,
    }


def perk_rates(
    games: Sequence[Mapping[str, Any]],
    player_id: int,
    *,
    base_points_by_game_id: Mapping[int, float] | None = None,
    complete: bool = True,
) -> dict[str, Any]:
    totals = Counter({perk_id: 0 for perk_id in PERK_IDS})
    matched_games = Counter({perk_id: 0 for perk_id in PERK_IDS})
    eligible = 0
    skipped: list[dict[str, Any]] = []
    for game in games:
        try:
            player = _find_player(game, player_id)
            if player is None or game.get("result") is None:
                continue
            eligible += 1
            base_points = None
            game_id = _int(game.get("id"))
            if base_points_by_game_id is not None and game_id is not None:
                base_points = base_points_by_game_id.get(game_id)
            matches = _perk_matches(game, player, base_points)
            for perk_id, count in matches.items():
                totals[perk_id] += count
                if count > 0:
                    matched_games[perk_id] += 1
        except (KeyError, TypeError, ValueError, ArithmeticError) as error:
            skipped.append({"gameId": game.get("id"), "reason": type(error).__name__})
    rates = {
        perk_id: {
            "matchCount": totals[perk_id],
            "matchedGames": matched_games[perk_id],
            "ratePerGame": totals[perk_id] / eligible if eligible else None,
            "gameHitRate": matched_games[perk_id] / eligible if eligible else None,
        }
        for perk_id in PERK_IDS
    }
    # Ninja requires the external base-points calculation, which full game JSON does not carry.
    ninja_sample = 0 if base_points_by_game_id is None else sum(
        1 for game in games if _int(game.get("id")) in base_points_by_game_id
    )
    rates["ninja"]["sampleSize"] = ninja_sample
    return {
        "playerId": player_id,
        "sampleSize": eligible,
        "perks": rates,
        "skippedGames": skipped,
        "complete": complete and not skipped and (base_points_by_game_id is not None),
        "limitations": [] if base_points_by_game_id is not None else ["ninja requires base points by game id"],
    }


def compare(statistics_by_player: Mapping[int, Mapping[str, Any]]) -> dict[str, Any]:
    rows = []
    for player_id, stats in statistics_by_player.items():
        rows.append(
            {
                "playerId": player_id,
                "sampleSize": stats.get("sampleSize", 0),
                "averagePoints": stats.get("averagePoints"),
                "winRate": stats.get("winRate"),
                "complete": bool(stats.get("complete", False)),
            }
        )
    rows.sort(
        key=lambda row: (
            row["averagePoints"] is not None,
            row["averagePoints"] or float("-inf"),
            row["sampleSize"],
            -row["playerId"],
        ),
        reverse=True,
    )
    return {"players": rows, "complete": all(row["complete"] for row in rows)}


def build_projection(
    statistics_by_player: Mapping[int, Mapping[str, Any]],
    *,
    minimum_sample: int = 5,
) -> dict[str, Any]:
    projections = []
    for player_id, stats in statistics_by_player.items():
        sample = int(stats.get("sampleSize", 0))
        average = _number(stats.get("averagePoints"))
        stddev = _number(stats.get("pointsStdDev"))
        projections.append(
            {
                "playerId": player_id,
                "expectedPoints": average,
                "uncertainty": stddev,
                "sampleSize": sample,
                "eligible": sample >= minimum_sample and average is not None,
                "complete": bool(stats.get("complete", False)),
            }
        )
    projections.sort(
        key=lambda row: (row["eligible"], row["expectedPoints"] or float("-inf"), -row["playerId"]),
        reverse=True,
    )
    return {
        "minimumSample": minimum_sample,
        "players": projections,
        "complete": all(row["complete"] for row in projections),
        "method": "historical mean with population standard deviation; no causal adjustment",
    }


def _perk_matches(game: Mapping[str, Any], player: Mapping[str, Any], base_points: float | None) -> dict[str, int]:
    position = int(player["position"])
    role = _role(player.get("role"))
    roles = {int(item["position"]): _role(item.get("role")) for item in game.get("players", [])}
    result = _int(game.get("result"))
    final_votes = _final_votes(game)
    kicked = _kicked(game, final_votes)
    sheriff = next((pos for pos, item_role in roles.items() if item_role == 3), None)
    black_on_table = {pos for pos in _players_on_table(game, kicked) if roles.get(pos) in BLACK}
    real_killer = _real_com_killer(game, roles, kicked)
    guess = player.get("guess") if isinstance(player.get("guess"), Mapping) else {}

    find_sheriff = role == 0 and any(
        _role(check.get("role")) == 0
        and _int(check.get("night")) == 1
        and roles.get(_int(check.get("player"))) == 3
        for check in game.get("checks") or []
    )
    sheriff_checks = len(
        {
            _int(check.get("player"))
            for check in game.get("checks") or []
            if _role(check.get("role")) == 3 and roles.get(_int(check.get("player"))) in BLACK
        }
    ) if role == 3 else 0
    vote_out_sheriff = role in BLACK and any(
        item["position"] == sheriff and item["reason"] == "VOTING" and item["day"] in (1, 2)
        for item in kicked
    )
    mine = [vote for vote in final_votes if vote["voter"] == position]
    vote_for_black = sum(
        1 for vote in mine for convicted in vote["convicted"] if roles.get(convicted) in BLACK
    ) if role in RED else 0
    first_guess = False
    if role in RED and kicked:
        first = kicked[0]
        simultaneous = [item for item in kicked if (item["day"], item["phase"]) == (first["day"], first["phase"])]
        mafs = guess.get("mafs") or []
        civs = guess.get("civs") or []
        first_guess = (
            len(simultaneous) == 1
            and first["position"] == position
            and len(mafs) + len(civs) == 3
            and all(roles.get(_int(pos)) in BLACK for pos in mafs)
            and all(roles.get(_int(pos)) in RED for pos in civs)
        )
    only_black = role == 2 and bool(mine) and all(
        any(roles.get(pos) in BLACK for pos in vote["convicted"]) for vote in mine
    )
    red_win = result == 0
    black_win = result == 1
    on_table = _players_on_table(game, kicked)
    return {
        "sniper": int(real_killer == position and any(item["position"] == sheriff and item["night"] == 1 for item in _killed(game, final_votes))),
        "winThreeToThree": int(role in BLACK and black_win and len(black_on_table) == 3),
        "findSheriff": int(find_sheriff),
        "sheriffCheckBlack": sheriff_checks,
        "voteOutSheriffDay1Or2": int(vote_out_sheriff),
        "voteForBlack": vote_for_black,
        "strongCity": int(real_killer is not None and role == 2 and red_win),
        "firstKickedFullGuess": int(first_guess),
        "votingOnlyForBlack": int(only_black),
        "winWithoutCritic": int(role in RED and red_win and _critic_day(game, kicked, roles) is None),
        "ninja": int(base_points is not None and abs(base_points) < 1e-5),
        "crowned": _red_vice_chain_count(game, position),
        "lastHeroGuess": int((role in RED) == red_win and len(on_table) == 2 and position in on_table),
    }


def _final_votes(game: Mapping[str, Any]) -> list[dict[str, Any]]:
    grouped: dict[int, list[Mapping[str, Any]]] = {}
    for vote in game.get("votes") or []:
        day = _int(vote.get("day"))
        if day is not None:
            grouped.setdefault(day, []).append(vote)
    result = []
    for day, votes in grouped.items():
        stage = game.get("stage") or {}
        if _int(stage.get("day")) == day and stage.get("type") == "voting":
            continue
        rounds = [_int(vote.get("num")) for vote in votes if vote.get("num") is not None]
        if not rounds or max(rounds) == 0:
            continue
        last = max(rounds)
        real = [vote for vote in votes if _int(vote.get("num")) == last]
        tallies = Counter(_int(vote.get("candidate")) for vote in real if vote.get("candidate") is not None)
        if not tallies:
            continue
        top = max(tallies.values())
        tied = len([count for count in tallies.values() if count == top]) > 1 and len(tallies) > 1
        if tied:
            convicted = list(tallies)
            expel_votes = [vote for vote in votes if vote.get("num") is None and vote.get("candidate") is not None]
            expelled = len(expel_votes) > len(real) - len(expel_votes)
            for vote in expel_votes:
                result.append({"day": day, "voter": _int(vote.get("voter")), "convicted": convicted, "expelled": expelled})
        else:
            convicted = max(tallies, key=tallies.get)  # type: ignore[arg-type]
            if day == 1 and game.get("zeroVoting") in {"reSpeech", "liftOnly", "none"}:
                continue
            for vote in real:
                candidate = _int(vote.get("candidate"))
                if candidate is not None:
                    result.append({"day": day, "voter": _int(vote.get("voter")), "convicted": [candidate], "expelled": candidate == convicted})
    return result


def _killed(
    game: Mapping[str, Any], final_votes: Sequence[Mapping[str, Any]] | None = None
) -> list[dict[str, int | None]]:
    grouped: dict[int, list[Mapping[str, Any]]] = {}
    for shot in game.get("shots") or []:
        night = _int(shot.get("night"))
        if night is not None:
            grouped.setdefault(night, []).append(shot)
    roles = {
        _int(player.get("position")): _role(player.get("role"))
        for player in game.get("players") or []
    }
    disqualified = [
        (_int(player.get("position")), _int(player.get("disqual", {}).get("day")))
        for player in game.get("players") or []
        if isinstance(player.get("disqual"), Mapping)
    ]
    result: list[dict[str, int | None]] = []
    for night, shots in sorted(grouped.items()):
        victims = {_int(shot.get("victim")) for shot in shots}
        removed = {item["position"] for item in result if item["position"] is not None and item["night"] < night}
        removed.update(pos for pos, day in disqualified if pos is not None and day is not None and day <= night)
        if final_votes is not None:
            removed.update(
                pos
                for vote in final_votes
                if vote["expelled"] and vote["day"] <= night
                for pos in vote["convicted"]
            )
        black_on_table = sum(1 for pos, role in roles.items() if pos not in removed and role in BLACK)
        killed = next(iter(victims)) if len(victims) == 1 and black_on_table <= len(shots) else None
        result.append({"night": night, "position": killed})
    return result


def _kicked(game: Mapping[str, Any], final_votes: Sequence[Mapping[str, Any]]) -> list[dict[str, Any]]:
    result = [
        {"position": item["position"], "day": item["night"], "phase": 1, "reason": "KILL"}
        for item in _killed(game, final_votes) if item["position"] is not None
    ]
    for vote in final_votes:
        if vote["expelled"]:
            result.extend({"position": pos, "day": vote["day"], "phase": 0, "reason": "VOTING"} for pos in vote["convicted"])
    for player in game.get("players") or []:
        disqual = player.get("disqual")
        if isinstance(disqual, Mapping) and _int(disqual.get("day")) is not None:
            result.append({"position": _int(player.get("position")), "day": _int(disqual.get("day")), "phase": 1, "reason": "DISQUAL"})
    unique = {(item["position"], item["day"], item["phase"], item["reason"]): item for item in result}
    return sorted(unique.values(), key=lambda item: (item["day"], item["phase"]))


def _players_on_table(game: Mapping[str, Any], kicked: Sequence[Mapping[str, Any]]) -> list[int]:
    removed = {item["position"] for item in kicked}
    return [int(player["position"]) for player in game.get("players") or [] if int(player["position"]) not in removed]


def _real_com_killer(game: Mapping[str, Any], roles: Mapping[int, int | None], kicked: Sequence[Mapping[str, Any]]) -> int | None:
    kills = [item for item in kicked if item["reason"] == "KILL"]
    if not kills or roles.get(kills[0]["position"]) != 3:
        return None
    value = _int(game.get("comKiller"))
    if value is not None:
        return value
    return next((pos for pos, role in roles.items() if role == 0), None)


def _critic_day(game: Mapping[str, Any], kicked: Sequence[Mapping[str, Any]], roles: Mapping[int, int | None]) -> int | None:
    stage = game.get("stage") or {}
    last_day = (_int(stage.get("day")) or 0) + 1
    red, black = 7, 3
    for day in range(2, last_day + 1):
        for item in kicked:
            if item["day"] == day - 1:
                if roles.get(item["position"]) in RED:
                    red -= 1
                else:
                    black -= 1
        if red <= black + 2:
            return day
    return None


def _red_vice_chain_count(game: Mapping[str, Any], target: int) -> int:
    players = {int(player["position"]): player for player in game.get("players") or []}
    count = 0
    for player in players.values():
        if _role(player.get("role")) not in RED:
            continue
        guess = player.get("guess") or {}
        current = _int(guess.get("vice")) if isinstance(guess, Mapping) else None
        visited = set()
        while current is not None and current not in visited:
            if current == target:
                count += 1
                break
            visited.add(current)
            holder = players.get(current) or {}
            holder_guess = holder.get("guess") or {}
            current = _int(holder_guess.get("vice")) if isinstance(holder_guess, Mapping) else None
    return count


def _find_player(game: Mapping[str, Any], player_id: int) -> Mapping[str, Any] | None:
    for player in game.get("players") or []:
        identity = player.get("player")
        if isinstance(identity, Mapping) and _int(identity.get("id")) == player_id:
            return player
        if _int(identity) == player_id:
            return player
    return None


def _profile_role(value: Any) -> str | None:
    if isinstance(value, Mapping):
        raw = value.get("type") or value.get("title")
    else:
        raw = value
    return str(raw)[:128] if raw is not None else None


def _role(value: Any) -> int | None:
    if isinstance(value, int) and not isinstance(value, bool):
        return value
    if isinstance(value, str):
        upper = value.upper()
        return {"DON": 0, "MAFIA": 1, "PEACE": 2, "CIVILIAN": 2, "SHERIFF": 3}.get(upper)
    return None


def _result_code(value: Any) -> str | None:
    if isinstance(value, Mapping) and value.get("code") is not None:
        return str(value["code"]).lower()
    return None


def _mmr(value: Any) -> float | None:
    if isinstance(value, Mapping):
        value = value.get("value")
    return _number(value)


def _number(value: Any) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    result = float(value)
    return result if math.isfinite(result) else None


def _int(value: Any) -> int | None:
    return value if isinstance(value, int) and not isinstance(value, bool) else None


def _date_key(value: Any) -> datetime:
    if not isinstance(value, str):
        return datetime.min
    normalized = value.replace("Z", "+00:00").replace(" ", "T")
    try:
        return datetime.fromisoformat(normalized).replace(tzinfo=None)
    except ValueError:
        return datetime.min


def _mean(values: Sequence[float]) -> float | None:
    return statistics.fmean(values) if values else None


def _median(values: Sequence[float]) -> float | None:
    return statistics.median(values) if values else None


def _pstdev(values: Sequence[float]) -> float | None:
    return statistics.pstdev(values) if values else None
