#!/usr/bin/env python3
import argparse
import json
import os
import shlex
import subprocess
import sys


DEFAULT_SSH_TARGET = "mafia@51.250.18.236"
DEFAULT_SSH_KEY = "~/personal/mafia/id_rsa"


def load_json(path: str) -> object:
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def parse_ids(raw: str) -> list[int]:
    ids: list[int] = []
    for part in raw.split(","):
        value = part.strip()
        if not value:
            continue
        try:
            parsed = int(value)
        except ValueError as exc:
            raise argparse.ArgumentTypeError(f"Invalid id: {value}") from exc
        if parsed <= 0:
            raise argparse.ArgumentTypeError(f"Id must be positive: {value}")
        ids.append(parsed)
    if not ids:
        raise argparse.ArgumentTypeError("At least one id is required")
    return ids


def repo_cd_command(remote_repo: str) -> str:
    if remote_repo == "~/polemica-fantasy":
        return 'cd "$HOME/polemica-fantasy"'
    return f"cd {shlex.quote(remote_repo)}"


def remote_python(method: str, path: str, payload: object) -> str:
    payload_text = json.dumps(payload, ensure_ascii=False)
    return f"""
import base64
import os
import sys
import urllib.error
import urllib.request

payload = {payload_text!r}.encode("utf-8")
url = "http://127.0.0.1:18080{path}"
auth_raw = (os.environ["ADMIN_USERNAME"] + ":" + os.environ["ADMIN_PASSWORD"]).encode()
headers = {{
    "Authorization": "Basic " + base64.b64encode(auth_raw).decode(),
    "Content-Type": "application/json; charset=utf-8",
}}
request = urllib.request.Request(url, data=payload, headers=headers, method="{method}")
try:
    with urllib.request.urlopen(request, timeout=30) as response:
        print("HTTP", response.status)
        print(response.read().decode("utf-8"))
except urllib.error.HTTPError as exc:
    print("HTTP", exc.code)
    print(exc.read().decode("utf-8"))
    sys.exit(1)
"""


def run_remote(args: argparse.Namespace, method: str, path: str, payload: object) -> int:
    if not path.startswith("/api/v1/admin/"):
        print(f"Refusing non-admin API path: {path}", file=sys.stderr)
        return 2

    summary = {
        "method": method,
        "path": path,
        "payload": payload,
        "ssh_target": args.ssh_target,
        "remote_repo": args.remote_repo,
    }
    if not args.execute:
        print(json.dumps({"dryRun": True, **summary}, ensure_ascii=False, indent=2))
        return 0

    remote_cmd = (
        f"{repo_cd_command(args.remote_repo)} && "
        "set -a && . ./.env && set +a && python3 -"
    )
    ssh_cmd = [
        "ssh",
        "-i",
        os.path.expanduser(args.ssh_key),
        args.ssh_target,
        remote_cmd,
    ]
    completed = subprocess.run(
        ssh_cmd,
        input=remote_python(method, path, payload),
        text=True,
        check=False,
    )
    return completed.returncode


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Call Polemica Fantasy production admin series endpoints through the VPS. Dry-run by default.",
    )
    parser.add_argument("--ssh-target", default=DEFAULT_SSH_TARGET)
    parser.add_argument("--ssh-key", default=DEFAULT_SSH_KEY)
    parser.add_argument("--remote-repo", default="~/polemica-fantasy")
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Actually call the production admin API. Without this flag, only print the planned request.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    create = subparsers.add_parser("create-series", help="POST /api/v1/admin/tournaments/{id}/series")
    create.add_argument("--tournament-id", type=int, required=True)
    create.add_argument("--payload-file", required=True)

    assign = subparsers.add_parser("assign-players", help="POST /api/v1/admin/series/{id}/players")
    assign.add_argument("--series-id", type=int, required=True)
    assign.add_argument("--ids", type=parse_ids, required=True, help="Comma-separated tournament_player ids")

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    if args.command == "create-series":
        payload = load_json(args.payload_file)
        path = f"/api/v1/admin/tournaments/{args.tournament_id}/series"
        return run_remote(args, "POST", path, payload)

    if args.command == "assign-players":
        payload = {
            "tournamentPlayerIds": args.ids,
            "replacementPolemicaUserIds": {},
        }
        path = f"/api/v1/admin/series/{args.series_id}/players"
        return run_remote(args, "POST", path, payload)

    parser.error(f"Unknown command: {args.command}")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
