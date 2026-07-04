#!/usr/bin/env python3
"""Export Telegram support-forum messages with a user account.

This uses Telegram MTProto via Telethon, not the Bot API. It can read the same
chat history that the logged-in Telegram account can see. For Polemica support
the relevant user messages are forwarded by the bot into the support forum, so
the default export keeps only forwarded messages.
"""

from __future__ import annotations

import argparse
import asyncio
import csv
import json
import os
import re
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

try:
    from telethon import TelegramClient, utils
    from telethon.errors import RPCError
    from telethon.tl.types import PeerChannel, PeerChat, PeerUser
except ImportError as exc:  # pragma: no cover - useful CLI failure mode
    raise SystemExit(
        "Missing dependency: telethon. Install it with:\n"
        "  python3 -m pip install -r scripts/telegram-support-export/requirements.txt"
    ) from exc


ROOT = Path(__file__).resolve().parent
DEFAULT_SESSION = ROOT / "polemica_support_export"
DEFAULT_JSONL = ROOT / "out" / "support-messages.jsonl"
DEFAULT_CSV = ROOT / "out" / "support-messages.csv"
DEFAULT_REPORT = ROOT / "out" / "support-requests.md"

BUG_KEYWORDS = (
    "баг",
    "ошиб",
    "error",
    "exception",
    "не работает",
    "не сработ",
    "не могу",
    "не получается",
    "не дает",
    "не даёт",
    "не откры",
    "не отображ",
    "не видно",
    "не снимается",
    "завис",
    "слом",
    "почин",
    "пофикс",
)

FEATURE_KEYWORDS = (
    "можно сделать",
    "а можно",
    "добав",
    "сделать чтобы",
    "сделать, чтобы",
    "хочу",
    "хотел",
    "удобно",
    "нужно",
    "нужен",
    "нужна",
    "планируется",
    "фича",
    "фич",
    "улучш",
)

NOISE_PREFIXES = ("/start", "@")


@dataclass
class ExportedMessage:
    message_id: int
    date: str
    chat_id: int | None
    topic_message_id: int | None
    sender_user_id: int | None
    forwarded_from_user_id: int | None
    forwarded_from_name: str | None
    text: str
    media_type: str | None
    raw_category: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export user messages forwarded into a Telegram support forum.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--api-id", type=int, default=env_int("TELEGRAM_API_ID"))
    parser.add_argument("--api-hash", default=os.getenv("TELEGRAM_API_HASH"))
    parser.add_argument("--phone", default=os.getenv("TELEGRAM_PHONE"))
    parser.add_argument("--session", default=str(DEFAULT_SESSION), help="Telethon session path/name.")
    parser.add_argument(
        "--chat",
        help=(
            "Support chat id/username/link. For numeric supergroup ids use -100..., "
            "for usernames use @name. Not needed with --list-dialogs."
        ),
    )
    parser.add_argument("--list-dialogs", action="store_true", help="List visible chats and exit.")
    parser.add_argument("--limit", type=int, default=0, help="Max messages to scan. 0 means all available.")
    parser.add_argument("--from-date", help="Inclusive UTC date, e.g. 2026-04-01 or 2026-04-01T12:00:00.")
    parser.add_argument("--to-date", help="Exclusive UTC date, e.g. 2026-07-01 or 2026-07-01T00:00:00.")
    parser.add_argument("--all-messages", action="store_true", help="Export admin replies too, not only forwarded user messages.")
    parser.add_argument("--include-empty", action="store_true", help="Keep media/service messages without text.")
    parser.add_argument("--jsonl-out", default=str(DEFAULT_JSONL), help="Output JSONL path.")
    parser.add_argument("--csv-out", default=str(DEFAULT_CSV), help="Output CSV path. Empty value disables CSV.")
    parser.add_argument("--report-out", default=str(DEFAULT_REPORT), help="Output Markdown report path. Empty value disables report.")
    args = parser.parse_args()

    if not args.api_id or not args.api_hash:
        parser.error("Provide --api-id/--api-hash or TELEGRAM_API_ID/TELEGRAM_API_HASH.")
    if not args.list_dialogs and not args.chat:
        parser.error("Provide --chat or use --list-dialogs.")
    return args


def env_int(name: str) -> int | None:
    value = os.getenv(name)
    if not value:
        return None
    try:
        return int(value)
    except ValueError as exc:
        raise SystemExit(f"{name} must be an integer") from exc


def parse_utc_datetime(value: str | None) -> datetime | None:
    if not value:
        return None
    normalized = value.strip()
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", normalized):
        normalized += "T00:00:00"
    normalized = normalized.replace("Z", "+00:00")
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


async def main() -> None:
    args = parse_args()
    since = parse_utc_datetime(args.from_date)
    until = parse_utc_datetime(args.to_date)

    client = TelegramClient(args.session, args.api_id, args.api_hash)
    await client.start(phone=args.phone)

    if args.list_dialogs:
        await list_dialogs(client)
        await client.disconnect()
        return

    try:
        entity = await resolve_chat(client, args.chat)
        messages = await export_messages(client, entity, args, since=since, until=until)
    except RPCError as exc:
        raise SystemExit(f"Telegram API error: {exc}") from exc
    finally:
        await client.disconnect()

    write_jsonl(Path(args.jsonl_out), messages)
    if args.csv_out:
        write_csv(Path(args.csv_out), messages)
    if args.report_out:
        write_report(Path(args.report_out), messages)

    print(f"Exported {len(messages)} messages")
    print(f"JSONL: {args.jsonl_out}")
    if args.csv_out:
        print(f"CSV: {args.csv_out}")
    if args.report_out:
        print(f"Report: {args.report_out}")


async def list_dialogs(client: TelegramClient) -> None:
    async for dialog in client.iter_dialogs():
        entity = dialog.entity
        entity_id = utils.get_peer_id(entity)
        print(f"{dialog.name}\t{entity_id}\t{type(entity).__name__}")


async def resolve_chat(client: TelegramClient, chat: str) -> Any:
    stripped = chat.strip()
    if re.fullmatch(r"-?\d+", stripped):
        return await client.get_entity(int(stripped))
    return await client.get_entity(stripped)


async def export_messages(
    client: TelegramClient,
    entity: Any,
    args: argparse.Namespace,
    *,
    since: datetime | None,
    until: datetime | None,
) -> list[ExportedMessage]:
    result: list[ExportedMessage] = []
    scanned = 0
    limit = args.limit if args.limit and args.limit > 0 else None

    async for message in client.iter_messages(entity, limit=limit):
        scanned += 1
        message_date = message.date.astimezone(timezone.utc)
        if until and message_date >= until:
            continue
        if since and message_date < since:
            break

        exported = to_exported_message(message, all_messages=args.all_messages)
        if exported is None:
            continue
        if not args.include_empty and not exported.text:
            continue
        result.append(exported)

        if scanned % 1000 == 0:
            print(f"Scanned {scanned}, kept {len(result)}...")

    result.sort(key=lambda item: (item.date, item.message_id))
    return result


def to_exported_message(message: Any, *, all_messages: bool) -> ExportedMessage | None:
    forwarded_from_user_id = peer_id(getattr(getattr(message, "fwd_from", None), "from_id", None))
    if not all_messages and forwarded_from_user_id is None:
        return None

    text = normalize_text(getattr(message, "message", None) or "")
    raw_category = classify_text(text)
    return ExportedMessage(
        message_id=message.id,
        date=message.date.astimezone(timezone.utc).isoformat(),
        chat_id=peer_id(getattr(message, "peer_id", None)),
        topic_message_id=topic_message_id(message),
        sender_user_id=peer_id(getattr(message, "from_id", None)),
        forwarded_from_user_id=forwarded_from_user_id,
        forwarded_from_name=getattr(getattr(message, "fwd_from", None), "from_name", None),
        text=text,
        media_type=type(message.media).__name__ if getattr(message, "media", None) is not None else None,
        raw_category=raw_category,
    )


def peer_id(peer: Any) -> int | None:
    if peer is None:
        return None
    try:
        return int(utils.get_peer_id(peer))
    except Exception:
        pass
    if isinstance(peer, PeerUser):
        return int(peer.user_id)
    if isinstance(peer, PeerChat):
        return int(peer.chat_id)
    if isinstance(peer, PeerChannel):
        return int(peer.channel_id)
    value = getattr(peer, "user_id", None) or getattr(peer, "chat_id", None) or getattr(peer, "channel_id", None)
    return int(value) if value is not None else None


def topic_message_id(message: Any) -> int | None:
    reply_to = getattr(message, "reply_to", None)
    if reply_to is None:
        return None
    return getattr(reply_to, "reply_to_top_id", None) or getattr(reply_to, "reply_to_msg_id", None)


def normalize_text(text: str) -> str:
    return re.sub(r"\n{3,}", "\n\n", text.replace("\r\n", "\n").strip())


def classify_text(text: str) -> str:
    lowered = text.lower().strip()
    if not lowered or lowered.startswith(NOISE_PREFIXES):
        return "noise"
    if any(keyword in lowered for keyword in BUG_KEYWORDS):
        return "bug"
    if any(keyword in lowered for keyword in FEATURE_KEYWORDS):
        return "feature"
    if "?" in lowered:
        return "question"
    return "uncategorized"


def write_jsonl(path: Path, messages: Iterable[ExportedMessage]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for message in messages:
            handle.write(json.dumps(asdict(message), ensure_ascii=False, sort_keys=True) + "\n")


def write_csv(path: Path, messages: Iterable[ExportedMessage]) -> None:
    rows = [asdict(message) for message in messages]
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(ExportedMessage.__dataclass_fields__.keys()))
        writer.writeheader()
        writer.writerows(rows)


def write_report(path: Path, messages: list[ExportedMessage]) -> None:
    grouped = {
        "bug": [],
        "feature": [],
        "question": [],
        "uncategorized": [],
        "noise": [],
    }
    for message in messages:
        grouped.setdefault(message.raw_category, []).append(message)

    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        handle.write("# Telegram Support Requests\n\n")
        handle.write(f"Total exported messages: {len(messages)}\n\n")
        for category, title in (
            ("bug", "Bug Candidates"),
            ("feature", "Feature Request Candidates"),
            ("question", "Questions"),
            ("uncategorized", "Uncategorized"),
            ("noise", "Noise"),
        ):
            items = grouped.get(category, [])
            handle.write(f"## {title} ({len(items)})\n\n")
            for message in items:
                handle.write(f"- `{message.date}` msg `{message.message_id}`")
                if message.topic_message_id is not None:
                    handle.write(f" topic `{message.topic_message_id}`")
                if message.forwarded_from_user_id is not None:
                    handle.write(f" user `{message.forwarded_from_user_id}`")
                handle.write(f": {one_line(message.text)}\n")
            handle.write("\n")


def one_line(text: str, *, max_length: int = 180) -> str:
    collapsed = re.sub(r"\s+", " ", text).strip()
    if len(collapsed) <= max_length:
        return collapsed
    return collapsed[: max_length - 1].rstrip() + "…"


if __name__ == "__main__":
    asyncio.run(main())
