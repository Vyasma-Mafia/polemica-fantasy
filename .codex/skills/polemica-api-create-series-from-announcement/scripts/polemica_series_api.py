#!/usr/bin/env python3
"""Narrow Polemica Fantasy admin API client for announcement-created series."""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


DEFAULT_API_BASE = "https://admin.fantasy.maftourbot.ru/api"
USERNAME_ENV = "POLEMICA_ADMIN_USERNAME"
PASSWORD_ENV = "POLEMICA_ADMIN_PASSWORD"
BASE_ENV = "POLEMICA_ADMIN_API_BASE"


def positive_int(raw: str) -> int:
    try:
        value = int(raw)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"Expected an integer, got {raw!r}") from exc
    if value <= 0:
        raise argparse.ArgumentTypeError("ID must be positive")
    return value


def parse_ids(raw: str) -> list[int]:
    values = [positive_int(part.strip()) for part in raw.split(",") if part.strip()]
    if not values:
        raise argparse.ArgumentTypeError("At least one ID is required")
    if len(values) != len(set(values)):
        raise argparse.ArgumentTypeError("Tournament-player IDs must be unique")
    return values


def nonblank_nickname(raw: str) -> str:
    value = raw.strip()
    if not value:
        raise argparse.ArgumentTypeError("Nickname must not be blank")
    if len(value) > 512:
        raise argparse.ArgumentTypeError("Nickname must be at most 512 characters")
    return value


def load_json(path: str) -> Any:
    with Path(path).open("r", encoding="utf-8") as handle:
        return json.load(handle)


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{label} must be a JSON object")
    return value


def derive_public_number(name: str) -> int:
    matches = re.findall(r"\d+", name)
    return int(matches[-1]) if matches else 1


def validate_create_payload(value: Any, expected_public_number: int) -> dict[str, Any]:
    payload = require_object(value, "Create payload")
    required = ("name", "status", "startsAt", "teamDeadline")
    missing = [key for key in required if key not in payload]
    if missing:
        raise ValueError(f"Create payload is missing required fields: {', '.join(missing)}")
    name = payload["name"]
    if not isinstance(name, str) or not name.strip():
        raise ValueError("Create payload name must be a non-blank string")
    derived_public_number = derive_public_number(name)
    if derived_public_number != expected_public_number:
        raise ValueError(
            f"Backend would derive publicNumber={derived_public_number} from name, "
            f"but --expected-public-number is {expected_public_number}"
        )
    if payload["status"] != "UPCOMING":
        raise ValueError("This client only creates series with status UPCOMING")
    standalone = payload.get("gameStartedOn") is not None
    competition = payload.get("gameNumFrom") is not None or payload.get("gameNumTo") is not None
    if standalone and competition:
        raise ValueError("Create payload cannot mix gameStartedOn with a competition game range")
    if competition and (payload.get("gameNumFrom") is None or payload.get("gameNumTo") is None):
        raise ValueError("Competition create payload requires both gameNumFrom and gameNumTo")
    if competition:
        if "gamePhase" not in payload:
            raise ValueError(
                "Competition create payload must include gamePhase explicitly; "
                "omission would silently default to 0"
            )
        phase = payload["gamePhase"]
        if phase is not None and (
            not isinstance(phase, int) or isinstance(phase, bool) or phase not in (0, 1, 2)
        ):
            raise ValueError("gamePhase must be 0, 1, 2, or explicit null")
    elif standalone and "gamePhase" in payload:
        raise ValueError("Standalone create payload must omit gamePhase")
    return payload


def validate_assignment_payload(value: Any) -> dict[str, Any]:
    payload = require_object(value, "Assignment payload")
    ids = payload.get("tournamentPlayerIds")
    if not isinstance(ids, list) or not ids:
        raise ValueError("Assignment payload requires a non-empty tournamentPlayerIds array")
    if any(not isinstance(value, int) or isinstance(value, bool) or value <= 0 for value in ids):
        raise ValueError("Every tournamentPlayerId must be a positive integer")
    if len(ids) != len(set(ids)):
        raise ValueError("tournamentPlayerIds must be unique")
    replacements = payload.get("replacementPolemicaUserIds", {})
    if not isinstance(replacements, dict):
        raise ValueError("replacementPolemicaUserIds must be an object")
    selected = {str(value) for value in ids}
    if any(str(key) not in selected for key in replacements):
        raise ValueError("Every replacement key must refer to a selected tournamentPlayerId")
    if any(
        not isinstance(value, int) or isinstance(value, bool) or value <= 0
        for value in replacements.values()
    ):
        raise ValueError("Every replacement Polemica user ID must be a positive integer")
    payload.setdefault("replacementPolemicaUserIds", {})
    return payload


def find_player_matches(players: Any, polemica_user_id: int) -> list[dict[str, Any]]:
    if not isinstance(players, list):
        raise RuntimeError("Global fantasy-player response was not an array")
    matches: list[dict[str, Any]] = []
    for player in players:
        if not isinstance(player, dict):
            continue
        ids = {player.get("polemicaUserId")}
        aliases = player.get("aliases", [])
        if isinstance(aliases, list):
            ids.update(
                alias.get("polemicaUserId") for alias in aliases if isinstance(alias, dict)
            )
        if polemica_user_id in ids:
            matches.append(player)
    return matches


def api_base(args: argparse.Namespace) -> str:
    raw = args.base_url or os.environ.get(BASE_ENV) or DEFAULT_API_BASE
    parsed = urllib.parse.urlparse(raw)
    if parsed.scheme != "https" or not parsed.netloc:
        raise ValueError("Admin API base URL must be an absolute https:// URL")
    return raw.rstrip("/")


def auth_header() -> str:
    username = os.environ.get(USERNAME_ENV)
    password = os.environ.get(PASSWORD_ENV)
    if not username or not password:
        raise ValueError(
            f"Set {USERNAME_ENV} and {PASSWORD_ENV} in the local environment; "
            "never pass credentials as command arguments"
        )
    token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


def request_json(
    args: argparse.Namespace,
    method: str,
    path: str,
    payload: Any | None = None,
) -> Any:
    if not path.startswith("/v1/admin/") and path != "/v1/admin/tournaments":
        raise ValueError(f"Refusing non-admin API path: {path}")
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Accept": "application/json", "Authorization": auth_header()}
    if body is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
    request = urllib.request.Request(
        f"{api_base(args)}{path}", data=body, headers=headers, method=method
    )
    try:
        with urllib.request.urlopen(request, timeout=args.timeout) as response:
            raw = response.read().decode("utf-8")
            return None if not raw else json.loads(raw)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code}: {detail or exc.reason}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"API request failed with unknown outcome: {exc.reason}") from exc


def print_json(value: Any) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True))


def write_or_dry_run(
    args: argparse.Namespace,
    method: str,
    path: str,
    payload: Any,
    expected_response_fields: dict[str, Any] | None = None,
) -> int:
    plan = {
        "dryRun": not args.execute,
        "method": method,
        "url": f"{api_base(args)}{path}",
        "payload": payload,
    }
    if expected_response_fields:
        plan["expectedResponseFields"] = expected_response_fields
    if not args.execute:
        print_json(plan)
        return 0
    response = request_json(args, method, path, payload)
    print_json(response)
    if expected_response_fields:
        if not isinstance(response, dict):
            raise RuntimeError("Write may have committed, but the API response was not an object; do not retry")
        mismatches = {
            key: {"expected": expected, "actual": response.get(key)}
            for key, expected in expected_response_fields.items()
            if response.get(key) != expected
        }
        if mismatches:
            raise RuntimeError(
                "Write committed with unexpected response fields; do not retry: "
                + json.dumps(mismatches, ensure_ascii=False, sort_keys=True)
            )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Read and safely write Polemica Fantasy series and required roster additions "
            "through the HTTPS admin API. "
            "Writes are dry-run unless global --execute is supplied."
        )
    )
    parser.add_argument(
        "--base-url",
        help=f"Admin API base ending in /api (default: ${BASE_ENV} or production)",
    )
    parser.add_argument("--timeout", type=positive_int, default=30, help="HTTP timeout seconds")
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Execute player-addition/create/assignment writes; otherwise only print the request",
    )
    commands = parser.add_subparsers(dest="command", required=True)

    commands.add_parser("list-tournaments", help="GET /v1/admin/tournaments")
    commands.add_parser("list-fantasy-players", help="GET global players and Polemica aliases")

    find_player = commands.add_parser("find-player", help="Find a global player by exact Polemica ID")
    find_player.add_argument("--polemica-user-id", type=positive_int, required=True)

    get_tournament = commands.add_parser("get-tournament", help="GET tournament with roster")
    get_tournament.add_argument("--tournament-id", type=positive_int, required=True)

    list_series = commands.add_parser("list-series", help="GET all series for a tournament")
    list_series.add_argument("--tournament-id", type=positive_int, required=True)

    get_series = commands.add_parser("get-series", help="GET one series")
    get_series.add_argument("--series-id", type=positive_int, required=True)

    list_games = commands.add_parser("list-games", help="GET synced games for one series")
    list_games.add_argument("--series-id", type=positive_int, required=True)

    add_player = commands.add_parser("add-player", help="POST a reviewed tournament-player addition")
    add_player.add_argument("--tournament-id", type=positive_int, required=True)
    player_source = add_player.add_mutually_exclusive_group(required=True)
    player_source.add_argument("--fantasy-player-id", type=positive_int)
    player_source.add_argument("--polemica-user-id", type=positive_int)
    add_player.add_argument(
        "--nickname",
        type=nonblank_nickname,
        help="Required with --polemica-user-id; forbidden with --fantasy-player-id",
    )

    create = commands.add_parser("create-series", help="POST a reviewed create payload")
    create.add_argument("--tournament-id", type=positive_int, required=True)
    create.add_argument("--expected-public-number", type=positive_int, required=True)
    create.add_argument("--payload-file", required=True)

    assign = commands.add_parser("assign-players", help="POST a complete replacement roster")
    assign.add_argument("--series-id", type=positive_int, required=True)
    assignment_source = assign.add_mutually_exclusive_group(required=True)
    assignment_source.add_argument("--ids", type=parse_ids, help="Comma-separated tournament-player IDs")
    assignment_source.add_argument(
        "--payload-file", help="Complete assignment JSON, including any account replacements"
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        if args.command == "list-tournaments":
            print_json(request_json(args, "GET", "/v1/admin/tournaments"))
        elif args.command == "list-fantasy-players":
            print_json(request_json(args, "GET", "/v1/admin/fantasy-players"))
        elif args.command == "find-player":
            players = request_json(args, "GET", "/v1/admin/fantasy-players")
            matches = find_player_matches(players, args.polemica_user_id)
            print_json(
                {
                    "queryPolemicaUserId": args.polemica_user_id,
                    "matchCount": len(matches),
                    "matches": matches,
                }
            )
        elif args.command == "get-tournament":
            print_json(request_json(args, "GET", f"/v1/admin/tournaments/{args.tournament_id}"))
        elif args.command == "list-series":
            print_json(
                request_json(args, "GET", f"/v1/admin/tournaments/{args.tournament_id}/series")
            )
        elif args.command == "get-series":
            print_json(request_json(args, "GET", f"/v1/admin/series/{args.series_id}"))
        elif args.command == "list-games":
            print_json(request_json(args, "GET", f"/v1/admin/series/{args.series_id}/games"))
        elif args.command == "add-player":
            if args.fantasy_player_id is not None:
                if args.nickname is not None:
                    raise ValueError("--nickname is forbidden with --fantasy-player-id")
                payload = {"fantasyPlayerId": args.fantasy_player_id}
            else:
                if args.nickname is None:
                    raise ValueError("--nickname is required with --polemica-user-id")
                payload = {
                    "polemicaUserId": args.polemica_user_id,
                    "nickname": args.nickname,
                }
            return write_or_dry_run(
                args,
                "POST",
                f"/v1/admin/tournaments/{args.tournament_id}/players",
                payload,
            )
        elif args.command == "create-series":
            payload = validate_create_payload(
                load_json(args.payload_file), args.expected_public_number
            )
            return write_or_dry_run(
                args,
                "POST",
                f"/v1/admin/tournaments/{args.tournament_id}/series",
                payload,
                expected_response_fields={"publicNumber": args.expected_public_number},
            )
        elif args.command == "assign-players":
            payload = validate_assignment_payload(
                load_json(args.payload_file)
                if args.payload_file
                else {"tournamentPlayerIds": args.ids, "replacementPolemicaUserIds": {}}
            )
            return write_or_dry_run(
                args, "POST", f"/v1/admin/series/{args.series_id}/players", payload
            )
        else:
            parser.error(f"Unknown command: {args.command}")
        return 0
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
