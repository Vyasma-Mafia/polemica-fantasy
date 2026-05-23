#!/usr/bin/env python3

import argparse
import hashlib
import hmac
import json
import os
import pathlib
import shlex
import sys
import time
import urllib.parse


WEB_APP_DATA_KEY = b"WebAppData"


def repo_root() -> pathlib.Path:
    return pathlib.Path(__file__).resolve().parents[1]


def load_env_file(path: pathlib.Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export ") :].strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        values[key] = value
    return values


def resolve_bot_token(args: argparse.Namespace) -> str:
    if args.bot_token:
        return args.bot_token

    env_token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    if env_token:
        return env_token

    env_values = load_env_file(args.env_file)
    file_token = env_values.get("TELEGRAM_BOT_TOKEN", "").strip()
    if file_token:
        return file_token

    raise SystemExit(
        "TELEGRAM_BOT_TOKEN is required. Pass --bot-token, export TELEGRAM_BOT_TOKEN, "
        f"or add it to {args.env_file}."
    )


def build_init_data(bot_token: str, auth_date: int, user: dict[str, object]) -> str:
    user_json = json.dumps(user, ensure_ascii=False, separators=(",", ":"))
    pairs = {
        "auth_date": str(auth_date),
        "user": user_json,
    }
    data_check_string = "\n".join(f"{key}={pairs[key]}" for key in sorted(pairs))
    secret_key = hmac.new(WEB_APP_DATA_KEY, bot_token.encode("utf-8"), hashlib.sha256).digest()
    digest = hmac.new(secret_key, data_check_string.encode("utf-8"), hashlib.sha256).hexdigest()
    return urllib.parse.urlencode(
        {
            "auth_date": str(auth_date),
            "user": user_json,
            "hash": digest,
        }
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate signed Telegram Mini App initData for local Vite development."
    )
    parser.add_argument("--bot-token", help="Telegram bot token. Defaults to env TELEGRAM_BOT_TOKEN or .env.")
    parser.add_argument(
        "--env-file",
        type=pathlib.Path,
        default=repo_root() / ".env",
        help="Env file to read TELEGRAM_BOT_TOKEN from when the env var is not set.",
    )
    parser.add_argument("--telegram-id", type=int, default=888001, help="Telegram user id for the dev user.")
    parser.add_argument("--username", default="localdev", help="Telegram username for the dev user.")
    parser.add_argument("--first-name", default="LocalDev", help="Telegram first_name for the dev user.")
    parser.add_argument("--last-name", default="", help="Optional Telegram last_name for the dev user.")
    parser.add_argument("--language-code", default="", help="Optional Telegram language_code for the dev user.")
    parser.add_argument("--auth-date", type=int, default=int(time.time()), help="Unix auth_date. Defaults to now.")
    parser.add_argument(
        "--format",
        choices=("env", "raw", "json"),
        default="env",
        help="Output format. Use raw for command substitution.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    bot_token = resolve_bot_token(args)
    user: dict[str, object] = {
        "id": args.telegram_id,
        "first_name": args.first_name,
        "username": args.username,
    }
    if args.last_name:
        user["last_name"] = args.last_name
    if args.language_code:
        user["language_code"] = args.language_code

    init_data = build_init_data(bot_token, args.auth_date, user)
    if args.format == "raw":
        print(init_data)
    elif args.format == "json":
        print(json.dumps({"VITE_DEV_INIT_DATA": init_data}, ensure_ascii=False))
    else:
        print(f"VITE_DEV_INIT_DATA={shlex.quote(init_data)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
