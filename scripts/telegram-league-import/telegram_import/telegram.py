from __future__ import annotations

import asyncio
import fcntl
import ipaddress
import json
import os
import signal
import stat
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Iterator

from telethon import TelegramClient, utils
from telethon.errors import AuthKeyError, FloodWaitError, ForbiddenError, RPCError, ServerError, UnauthorizedError
from telethon.tl.types import PeerChannel

from .classifier import CLASSIFIER_RULE_VERSION, classify, fingerprint
from .backend import BackendIngestClient, retry_timestamp as backend_retry_timestamp
from .config import DeliveryMode, OcrConfig, Paths, backend_delivery_config, delivery_mode, normalize_phone, ocr_config, parse_api_id, read_metadata, require_routed_source, required_env
from .notifier import BotNotifier, render, retry_timestamp
from .ocr import MediaMetadata, YandexVisionClient, inspect_media, terminal_ocr
from .storage import Inbox, utcnow


DEFAULT_DC_ID = 2
DEFAULT_DC_IPV4 = "149.154.167.51"
DEFAULT_DC_PORT = 443


@contextmanager
def exclusive_lock(path: Path) -> Iterator[None]:
    with path.open("a+", encoding="utf-8") as handle:
        os.chmod(path, 0o600)
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise SystemExit("Another auth/worker process is already using this Telethon session") from exc
        yield


def make_client(paths: Paths, app_version: str) -> TelegramClient:
    client = TelegramClient(str(paths.session_base), parse_api_id(), required_env("TELEGRAM_IMPORT_API_HASH"), use_ipv6=False, device_model="Polemica League Import Worker", app_version=app_version, timeout=10, request_retries=3, connection_retries=3, retry_delay=2, auto_reconnect=True, flood_sleep_threshold=0)
    if client.session.server_address is None:
        client.session.set_dc(DEFAULT_DC_ID, DEFAULT_DC_IPV4, DEFAULT_DC_PORT)
        client.session.save()
    address = ipaddress.ip_address(client.session.server_address)
    if not isinstance(address, ipaddress.IPv4Address):
        raise SystemExit("Refusing a non-IPv4 Telegram DC")
    return client


def secure_session_files(paths: Paths) -> None:
    for candidate in paths.session_dir.iterdir():
        if candidate.is_file():
            os.chmod(candidate, 0o600)


def write_metadata(path: Path, payload: dict[str, object]) -> None:
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.chmod(temp, 0o600)
    temp.replace(path)
    os.chmod(path, 0o600)


def validate_identity(me: object, entity: object, metadata: dict[str, object], phone: str | None = None, *, require_pinned: bool = True) -> int:
    expected_user = metadata.get("authorizedTelegramUserId")
    if expected_user is None and require_pinned:
        raise SystemExit("Authorization metadata does not pin a Telegram account ID; run auth again")
    actual_user = getattr(me, "id", None)
    if expected_user is not None and int(expected_user) != actual_user:
        raise SystemExit("Telethon session account identity changed")
    if phone and getattr(me, "phone", None) and normalize_phone(me.phone) != normalize_phone(phone):
        raise SystemExit("Telethon session phone identity changed")
    expected_peer = metadata.get("channelPeerId")
    if expected_peer is None and require_pinned:
        raise SystemExit("Authorization metadata does not pin a Telegram channel ID; run auth again")
    actual_peer = utils.get_peer_id(entity)
    if expected_peer is not None and int(expected_peer) != actual_peer:
        raise SystemExit("Telegram channel numeric identity changed")
    return actual_peer


async def authorize(paths: Paths, hold_seconds: int) -> None:
    source = require_routed_source()
    phone = required_env("TELEGRAM_IMPORT_PHONE")
    channel = os.getenv("TELEGRAM_IMPORT_CHANNEL", "@polemica_closed_league").strip()
    expected_username = channel.strip().removeprefix("@").casefold()
    if expected_username != "polemica_closed_league":
        raise SystemExit("TELEGRAM_IMPORT_CHANNEL must be exactly @polemica_closed_league")
    metadata = read_metadata(paths.metadata) if paths.metadata.exists() else {}
    with exclusive_lock(paths.lock):
        client = make_client(paths, "auth-bootstrap-2")
        try:
            await client.start(phone=phone)
            me = await client.get_me()
            entity = await client.get_entity(channel)
            actual_username = (getattr(entity, "username", "") or "").casefold()
            if actual_username != expected_username:
                raise SystemExit("Resolved Telegram channel username does not match the allowlist")
            peer_id = validate_identity(me, entity, metadata, phone, require_pinned=bool(metadata))
            latest = (await client.get_messages(entity, limit=1))
            message = latest[0] if latest else None
            write_metadata(paths.metadata, {"channelPeerId": peer_id, "channelUsername": f"@{getattr(entity, 'username', '')}", "channelTitle": getattr(entity, "title", ""), "authorizedTelegramUserId": getattr(me, "id", None), "authorizedAt": utcnow(), "lastReadableMessageId": getattr(message, "id", None), "lastReadableMessageDate": getattr(message, "date", None).astimezone(timezone.utc).isoformat() if message and message.date else None})
            print("Telethon authorization and channel read check succeeded.")
            print(f"container_source_ip={source}\nauthorized_user_id={getattr(me, 'id', None)}\nchannel_peer_id={peer_id}\nlast_readable_message_id={getattr(message, 'id', None)}")
            if hold_seconds:
                await asyncio.sleep(hold_seconds)
        except RPCError as exc:
            raise SystemExit(f"Telegram API error: {type(exc).__name__}") from exc
        finally:
            await client.disconnect()
            secure_session_files(paths)


def message_payload(message: object) -> dict[str, object]:
    media = getattr(message, "media", None)
    media_object = getattr(media, "photo", None) or getattr(media, "document", None) or getattr(media, "webpage", None)
    media_id = getattr(media_object, "id", None)
    message_date = message.date.astimezone(timezone.utc).isoformat() if message.date else None
    edit_date = message.edit_date.astimezone(timezone.utc).isoformat() if message.edit_date else None
    return {"message_id": int(message.id), "source_version": edit_date or message_date or "", "text": getattr(message, "message", "") or "", "message_date": message_date, "edit_date": edit_date, "grouped_id": getattr(message, "grouped_id", None), "media_kind": type(media).__name__ if media else None, "media_id": str(media_id) if media_id is not None else None}


def persist_message(
    inbox: Inbox,
    peer_id: int,
    message: object,
    *,
    silent: bool,
    watermark: int | None = None,
    classifier_replay: bool = False,
    mode: DeliveryMode = DeliveryMode.DIRECT,
    ocr_enabled: bool = False,
) -> str:
    payload = message_payload(message)
    result = classify(str(payload["text"]))
    semantic = {key:payload[key] for key in ("text","grouped_id","media_kind","media_id")}
    if classifier_replay:
        semantic["classifier_rule_version"] = CLASSIFIER_RULE_VERSION
    reason = f"{result.reason}; classifier rule v{CLASSIFIER_RULE_VERSION}"
    return inbox.persist(
        peer_id=peer_id,
        fingerprint=fingerprint(semantic),
        classification=result.kind,
        league=result.league,
        reason=reason,
        silent=silent,
        watermark=watermark,
        backend_delivery=mode is DeliveryMode.BACKEND,
        force_backend_delivery=classifier_replay and mode is DeliveryMode.BACKEND,
        ocr_enabled=ocr_enabled,
        **payload,
    )


def prepare_ocr_spool(path: Path) -> None:
    if path.exists() and path.is_symlink():
        raise SystemExit("OCR spool directory must not be a symlink")
    path.mkdir(mode=0o700, exist_ok=True)
    os.chmod(path, 0o700)
    if stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise SystemExit("OCR spool directory must not be accessible by group/other")
    # The worker holds the session-wide lock, so no other process can own these
    # raw files. Every OCR attempt can safely redownload them after a crash.
    for candidate in path.iterdir():
        if candidate.is_file() and not candidate.is_symlink():
            candidate.unlink()


def media_evidence(task: object, metadata: MediaMetadata | None, ocr: object) -> dict[str, object]:
    return {
        "telegramMediaId": task["telegram_media_id"],
        "groupedId": task["grouped_id"],
        "mimeType": metadata.mime_type if metadata else None,
        "byteSize": metadata.byte_size if metadata else None,
        "width": metadata.width if metadata else None,
        "height": metadata.height if metadata else None,
        "sha256": metadata.sha256 if metadata else None,
        "ocr": ocr.evidence(),
    }


async def drain_ocr_tasks(
    client: TelegramClient,
    entity: object,
    inbox: Inbox,
    vision: YandexVisionClient,
    config: OcrConfig,
    spool: Path,
) -> None:
    repeat_limit = 10
    for _ in range(repeat_limit):
        task = inbox.lease_ocr_task()
        if task is None:
            return
        token = task["lease_token"]
        raw_path = spool / f"ocr-{task['id']}-{token}.raw"
        metadata = None
        raw_is_durable = False
        try:
            fetched = await client.get_messages(entity, ids=int(task["message_id"]))
            message = fetched[0] if isinstance(fetched, list) and fetched else fetched
            if message is None:
                inbox.supersede_ocr_task(task["id"], token, "source message is no longer readable")
                continue
            current = message_payload(message)
            if (
                current["source_version"] != task["source_version"]
                or current["media_id"] != task["telegram_media_id"]
                or current["grouped_id"] != task["grouped_id"]
                or current["media_kind"] != task["media_kind"]
            ):
                inbox.supersede_ocr_task(task["id"], token, "source media changed")
                continue
            if task["grouped_id"] is not None:
                outcome = terminal_ocr("UNSUPPORTED", config, "ALBUM_UNSUPPORTED")
            elif task["media_kind"] != "MessageMediaPhoto":
                outcome = terminal_ocr("UNSUPPORTED", config, "MEDIA_KIND_UNSUPPORTED")
            else:
                descriptor = os.open(raw_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
                os.close(descriptor)
                downloaded = await client.download_media(message, file=str(raw_path))
                if not downloaded or not raw_path.exists():
                    raise OSError("Telegram media download returned no file")
                os.chmod(raw_path, 0o600)
                try:
                    metadata = inspect_media(raw_path, config)
                except ValueError as exc:
                    outcome = terminal_ocr("UNSUPPORTED", config, str(exc))
                else:
                    outcome = await vision.recognize(raw_path, metadata)
            if outcome.retryable:
                if int(task["attempts"]) < config.max_attempts:
                    retry_at = backend_retry_timestamp(outcome.retry_after)
                    raw_is_durable = inbox.retry_ocr_task(
                        task["id"], token, retry_at, outcome.error_code or "PROVIDER_UNAVAILABLE"
                    )
                    continue
                outcome = terminal_ocr("FAILED", config, "PROVIDER_RETRIES_EXHAUSTED")
            latest_fetched = await client.get_messages(entity, ids=int(task["message_id"]))
            latest_message = latest_fetched[0] if isinstance(latest_fetched, list) and latest_fetched else latest_fetched
            if latest_message is None:
                inbox.supersede_ocr_task(task["id"], token, "source message is no longer readable after OCR")
                continue
            latest = message_payload(latest_message)
            if (
                latest["source_version"] != task["source_version"]
                or latest["media_id"] != task["telegram_media_id"]
                or latest["grouped_id"] != task["grouped_id"]
                or latest["media_kind"] != task["media_kind"]
            ):
                inbox.supersede_ocr_task(task["id"], token, "source media changed during OCR")
                continue
            evidence = media_evidence(task, metadata, outcome)
            try:
                committed = inbox.complete_ocr_task(task["id"], token, evidence)
            except ValueError:
                compact = terminal_ocr("FAILED", config, "EVIDENCE_TOO_LARGE")
                committed = inbox.complete_ocr_task(task["id"], token, media_evidence(task, metadata, compact))
            raw_is_durable = committed
            if not committed:
                continue
        except (OSError, asyncio.TimeoutError) as exc:
            if int(task["attempts"]) < config.max_attempts:
                raw_is_durable = inbox.retry_ocr_task(
                    task["id"], token, backend_retry_timestamp(min(300, 2 ** int(task["attempts"]))),
                    type(exc).__name__,
                )
            else:
                failed = terminal_ocr("FAILED", config, "MEDIA_DOWNLOAD_FAILED")
                raw_is_durable = inbox.complete_ocr_task(
                    task["id"], token, media_evidence(task, metadata, failed)
                )
        finally:
            if raw_is_durable and raw_path.exists():
                raw_path.unlink()


async def drain_outbox(inbox: Inbox, notifier: BotNotifier) -> None:
    if not notifier.config.enabled:
        return
    while row := inbox.lease_outbox():
        result = await notifier.send(render(row["payload"]))
        inbox.finish_outbox(row["id"], delivered=result.delivered, terminal=result.terminal, error=result.error, retry_at=retry_timestamp(result.retry_after) if result.retry_after else None)
        if not result.delivered:
            break


async def drain_backend_outbox(inbox: Inbox, client: BackendIngestClient) -> None:
    while row := inbox.lease_backend_outbox():
        result = await client.send(row["delivery_id"], row["payload"])
        inbox.finish_backend_outbox(
            row["id"], delivered=result.delivered, terminal=result.terminal, error=result.error,
            retry_at=backend_retry_timestamp(result.retry_after) if result.retry_after else None,
        )
        if not result.delivered:
            break


async def poll_once(client: TelegramClient, entity: object, peer_id: int, inbox: Inbox, notifier: BotNotifier, mode: DeliveryMode = DeliveryMode.DIRECT, backend_client: BackendIngestClient | None = None, vision: YandexVisionClient | None = None, ocr: OcrConfig | None = None, ocr_spool: Path | None = None) -> None:
    ocr_enabled = bool(ocr and ocr.enabled)
    watermark_raw = inbox.get_state("watermark")
    if watermark_raw is None:
        initial_high_raw = inbox.get_state("initial_high_watermark")
        if initial_high_raw is None:
            latest = await client.get_messages(entity, limit=1)
            high_watermark = int(latest[0].id) if latest else 0
            # Persist H before any backfill I/O. A crash then resumes against
            # the same historical boundary instead of silently widening it.
            with inbox.transaction() as db:
                inbox.set_state(db, "initial_high_watermark", high_watermark)
                inbox.set_state(db, "bootstrap_status", "INCOMPLETE")
        else:
            high_watermark = int(initial_high_raw)
        initial = max(1, int(os.getenv("TELEGRAM_IMPORT_INITIAL_BACKFILL", "100")))
        messages = [message async for message in client.iter_messages(entity, limit=initial, max_id=high_watermark+1)]
        for message in reversed(messages):
            persist_message(inbox, peer_id, message, silent=True, mode=mode, ocr_enabled=ocr_enabled)
        with inbox.transaction() as db:
            inbox.set_state(db, "watermark", high_watermark)
            inbox.set_state(db, "bootstrap_status", "COMPLETE")

    watermark = int(inbox.get_state("watermark") or 0)
    upper_messages = await client.get_messages(entity, limit=1)
    upper_message = upper_messages[0] if upper_messages else None
    upper = int(upper_message.id) if upper_message else watermark
    messages = [message async for message in client.iter_messages(entity, min_id=watermark, max_id=upper+1, reverse=True)]
    if upper_message is not None and upper > watermark and all(int(message.id) != upper for message in messages):
        messages.append(upper_message)
    messages.sort(key=lambda message: int(message.id))
    for message in messages:
        persist_message(inbox, peer_id, message, silent=False, watermark=int(message.id), mode=mode, ocr_enabled=ocr_enabled)

    rolling = max(1, int(os.getenv("TELEGRAM_IMPORT_EDIT_SCAN", "50")))
    async for message in client.iter_messages(entity, limit=rolling, max_id=upper+1):
        persist_message(inbox, peer_id, message, silent=False, mode=mode, ocr_enabled=ocr_enabled)

    deep_page = max(1, int(os.getenv("TELEGRAM_IMPORT_DEEP_SCAN", "100")))
    cursor_raw = inbox.get_state("deep_scan_before_id")
    saved_ids = inbox.saved_message_ids(peer_id, int(cursor_raw) if cursor_raw else None, deep_page)
    if not saved_ids and cursor_raw:
        saved_ids = inbox.saved_message_ids(peer_id, None, deep_page)
    deep_messages = await client.get_messages(entity, ids=saved_ids) if saved_ids else []
    for saved_id, message in sorted(zip(saved_ids, deep_messages), key=lambda item: item[0]):
        if message is not None:
            persist_message(inbox, peer_id, message, silent=False, mode=mode, ocr_enabled=ocr_enabled)
        elif inbox.latest_classification(peer_id, saved_id) not in (None, "IGNORE"):
            observed = utcnow()
            inbox.persist(peer_id=peer_id, message_id=saved_id, source_version=observed, message_date=None, edit_date=observed, grouped_id=None, media_kind=None, media_id=None, text="", fingerprint=f"deleted:{peer_id}:{saved_id}", classification="IGNORE", league=None, reason="message no longer readable by numeric ID", silent=False, backend_delivery=mode is DeliveryMode.BACKEND)
    with inbox.transaction() as db:
        inbox.set_state(db, "deep_scan_before_id", min(saved_ids) if saved_ids else "")
        inbox.set_state(db, "last_poll_at", utcnow())
        inbox.set_state(db, "heartbeat_at", utcnow())
        inbox.set_state(db, "flood_wait_until", "")
    if mode is DeliveryMode.DIRECT:
        await drain_outbox(inbox, notifier)
    elif backend_client is not None:
        if vision is not None and ocr is not None and ocr_spool is not None:
            await drain_ocr_tasks(client, entity, inbox, vision, ocr, ocr_spool)
        await drain_backend_outbox(inbox, backend_client)


async def resolve_pinned_channel(client: TelegramClient, metadata: dict[str, object]) -> tuple[object, object, int]:
    if not await client.is_user_authorized():
        raise SystemExit("Telethon session is not authorized; run auth first")
    me = await client.get_me()
    channel_id = int(metadata["channelPeerId"])
    raw_channel_id, peer_type = utils.resolve_id(channel_id)
    if peer_type is not PeerChannel:
        raise SystemExit("Pinned Telegram peer is not a channel")
    entity = await client.get_entity(PeerChannel(raw_channel_id))
    peer_id = validate_identity(me, entity, metadata)
    return me, entity, peer_id


async def reclassify_message(paths: Paths, notifier: BotNotifier, message_id: int) -> None:
    if message_id <= 0:
        raise SystemExit("Message ID must be positive")
    require_routed_source()
    metadata = read_metadata(paths.metadata)
    mode = delivery_mode()
    ocr = ocr_config(mode)
    vision = YandexVisionClient(ocr) if ocr.enabled else None
    backend_client = BackendIngestClient(backend_delivery_config()) if mode is DeliveryMode.BACKEND else None
    with exclusive_lock(paths.lock):
        if ocr.enabled:
            prepare_ocr_spool(paths.ocr_spool)
        inbox = Inbox(paths.inbox)
        client = make_client(paths, "shadow-reclassify-1")
        try:
            await client.connect()
            _, entity, peer_id = await resolve_pinned_channel(client, metadata)
            message = await client.get_messages(entity, ids=message_id)
            if message is None or not getattr(message, "id", None):
                raise SystemExit(f"Telegram message {message_id} is not readable")
            outcome = persist_message(
                inbox,
                peer_id,
                message,
                silent=False,
                classifier_replay=True,
                mode=mode,
                ocr_enabled=ocr.enabled,
            )
            if mode is DeliveryMode.DIRECT:
                await drain_outbox(inbox, notifier)
            elif backend_client is not None:
                if vision is not None:
                    await drain_ocr_tasks(client, entity, inbox, vision, ocr, paths.ocr_spool)
                await drain_backend_outbox(inbox, backend_client)
            latest = inbox.connection.execute(
                "SELECT classification FROM messages WHERE channel_peer_id=? AND message_id=?",
                (peer_id, message_id),
            ).fetchone()
            outbox = inbox.connection.execute(
                "SELECT status FROM outbox WHERE channel_peer_id=? AND message_id=? ORDER BY id DESC LIMIT 1",
                (peer_id, message_id),
            ).fetchone()
            print(json.dumps({
                "messageId": message_id,
                "classification": latest[0] if latest else None,
                "outcome": outcome,
                "notificationStatus": outbox[0] if outbox else None,
                "classifierRuleVersion": CLASSIFIER_RULE_VERSION,
            }, sort_keys=True))
        finally:
            await client.disconnect()
            inbox.close()
            secure_session_files(paths)


async def run_worker(paths: Paths, notifier: BotNotifier, *, once: bool) -> None:
    require_routed_source()
    mode = delivery_mode()
    ocr = ocr_config(mode)
    vision = YandexVisionClient(ocr) if ocr.enabled else None
    backend_client = BackendIngestClient(backend_delivery_config()) if mode is DeliveryMode.BACKEND else None
    metadata = read_metadata(paths.metadata)
    stop = asyncio.Event()
    loop = asyncio.get_running_loop()
    for signum in (signal.SIGTERM, signal.SIGINT):
        try:
            loop.add_signal_handler(signum, stop.set)
        except NotImplementedError:
            pass
    with exclusive_lock(paths.lock):
        if ocr.enabled:
            prepare_ocr_spool(paths.ocr_spool)
        inbox = Inbox(paths.inbox)
        client = make_client(paths, "shadow-worker-1")
        backoff = 2
        try:
            await client.connect()
            me, entity, peer_id = await resolve_pinned_channel(client, metadata)
            with inbox.transaction() as db:
                inbox.set_state(db, "mode", "SHADOW")
                inbox.set_state(db, "delivery_mode", mode.value)
                inbox.set_state(db, "pinned_channel_peer_id", peer_id)
                inbox.set_state(db, "pinned_account_user_id", getattr(me, "id", ""))
            while not stop.is_set():
                try:
                    await poll_once(client, entity, peer_id, inbox, notifier, mode, backend_client, vision, ocr, paths.ocr_spool)
                    backoff = 2
                    if once:
                        break
                    try:
                        await asyncio.wait_for(stop.wait(), timeout=max(10, int(os.getenv("TELEGRAM_IMPORT_POLL_SECONDS", "60"))))
                    except asyncio.TimeoutError:
                        pass
                except FloodWaitError as exc:
                    wait = max(1, int(exc.seconds))
                    with inbox.transaction() as db:
                        inbox.set_state(db, "flood_wait_until", (datetime.now(timezone.utc) + timedelta(seconds=wait)).isoformat())
                        inbox.set_state(db, "heartbeat_at", utcnow())
                    try:
                        await asyncio.wait_for(stop.wait(), timeout=wait)
                    except asyncio.TimeoutError:
                        pass
                except (UnauthorizedError, ForbiddenError, AuthKeyError) as exc:
                    raise SystemExit(f"Permanent Telegram authorization/channel error: {type(exc).__name__}") from exc
                except (OSError, asyncio.TimeoutError, ServerError) as exc:
                    print(f"Telegram poll failed: {type(exc).__name__}; retrying", flush=True)
                    with inbox.transaction() as db:
                        inbox.set_state(db, "heartbeat_at", utcnow())
                    try:
                        await asyncio.wait_for(stop.wait(), timeout=backoff)
                    except asyncio.TimeoutError:
                        pass
                    backoff = min(backoff * 2, 300)
        finally:
            await client.disconnect()
            inbox.close()
            secure_session_files(paths)
