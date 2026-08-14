from __future__ import annotations

import argparse
import asyncio
import json
import os
from datetime import datetime, timedelta, timezone

from .config import DeliveryMode, delivery_mode, notifier_config, ocr_config, paths, require_shadow_mode
from .notifier import BotNotifier
from .storage import Inbox
from .telegram import authorize, reclassify_message, run_worker


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description="Polemica Telegram league-import shadow worker")
    commands = root.add_subparsers(dest="command", required=True)
    auth = commands.add_parser("auth")
    auth.add_argument("--hold-seconds", type=int, default=int(os.getenv("TELEGRAM_IMPORT_AUTH_HOLD_SECONDS", "0")))
    commands.add_parser("worker")
    commands.add_parser("poll-once")
    commands.add_parser("health")
    commands.add_parser("inspect")
    commands.add_parser("notify-test")
    replay = commands.add_parser("reclassify-message")
    replay.add_argument("--message-id", type=int, required=True)
    return root


def health() -> None:
    mode = delivery_mode()
    ocr = ocr_config(mode)
    notifications_enabled = mode is DeliveryMode.DIRECT and notifier_config().enabled
    info = Inbox(paths().inbox)
    try:
        data = info.quick_health()
    finally:
        info.close()
    now = datetime.now(timezone.utc)
    last_poll = datetime.fromisoformat(data["lastPollAt"]) if data["lastPollAt"] else None
    flood = datetime.fromisoformat(data["floodWaitUntil"]) if data["floodWaitUntil"] else None
    allowance = flood is not None and flood > now
    stale = last_poll is None or last_poll < now - timedelta(seconds=max(120, int(os.getenv("TELEGRAM_IMPORT_HEALTH_MAX_AGE", "300"))))
    max_pending = int(os.getenv("TELEGRAM_IMPORT_HEALTH_MAX_PENDING_SECONDS", "900"))
    oldest = datetime.fromisoformat(data["oldestPendingAt"]) if data["oldestPendingAt"] else None
    pending_stale = oldest is not None and oldest < now - timedelta(seconds=max_pending)
    oldest_backend = datetime.fromisoformat(data["oldestBackendPendingAt"]) if data["oldestBackendPendingAt"] else None
    backend_pending_stale = oldest_backend is not None and oldest_backend < now - timedelta(seconds=max_pending)
    oldest_ocr = datetime.fromisoformat(data["oldestPendingOcrAt"]) if data["oldestPendingOcrAt"] else None
    ocr_pending_stale = oldest_ocr is not None and oldest_ocr < now - timedelta(seconds=max_pending)
    backend_mode = mode is DeliveryMode.BACKEND
    healthy = data["quickCheck"] == "ok" and data["schemaVersion"] == 3 and (not stale or allowance) and (not pending_stale or not notifications_enabled) and (data["failedOutbox"] == 0 or not notifications_enabled) and (not backend_pending_stale or not backend_mode) and (data["failedBackendOutbox"] == 0 or not backend_mode) and (not ocr_pending_stale or not ocr.enabled)
    print(json.dumps({**data, "status": "ok" if healthy else "unhealthy", "floodWaitDegraded": allowance, "deliveryMode": mode.value, "notificationsEnabled": notifications_enabled, "ocrEnabled": ocr.enabled}, sort_keys=True))
    if not healthy:
        raise SystemExit(1)


async def notify_test() -> None:
    if delivery_mode() is not DeliveryMode.DIRECT:
        raise SystemExit("notify-test is only available in DIRECT delivery mode")
    notifier = BotNotifier(notifier_config())
    result = await notifier.send("[SHADOW][NO PRODUCTION WRITE]\nТест уведомлений Telegram league import. Серия не создана и не финализирована.")
    if not result.delivered:
        raise SystemExit(f"Operator notification failed: {result.error}")
    print("Operator notification delivered.")


def main() -> None:
    os.umask(0o077)
    args = parser().parse_args()
    if args.command == "auth":
        asyncio.run(authorize(paths(), args.hold_seconds))
        return
    require_shadow_mode()
    if args.command == "health":
        health()
    elif args.command == "inspect":
        inbox = Inbox(paths().inbox)
        try:
            print(json.dumps(inbox.inspect_summary(), ensure_ascii=False, sort_keys=True, indent=2))
        finally:
            inbox.close()
    elif args.command == "notify-test":
        asyncio.run(notify_test())
    elif args.command == "reclassify-message":
        asyncio.run(reclassify_message(paths(), BotNotifier(notifier_config()), args.message_id))
    else:
        asyncio.run(run_worker(paths(), BotNotifier(notifier_config()), once=args.command == "poll-once"))
