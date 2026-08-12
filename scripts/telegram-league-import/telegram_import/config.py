from __future__ import annotations

import ipaddress
import json
import os
import re
import socket
import stat
from enum import Enum
from dataclasses import dataclass
from pathlib import Path


DEFAULT_CHANNEL = "@polemica_closed_league"
DEFAULT_SESSION_NAME = "polemica_closed_league"
DEFAULT_REQUIRED_SOURCE_CIDR = "172.24.0.0/28"


class DeliveryMode(str, Enum):
    DIRECT = "DIRECT"
    BACKEND = "BACKEND"


def required_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise SystemExit(f"Missing required environment variable: {name}")
    return value


def require_shadow_mode() -> None:
    if os.getenv("TELEGRAM_IMPORT_MODE", "").strip() != "SHADOW":
        raise SystemExit("Refusing to run: TELEGRAM_IMPORT_MODE must be exactly SHADOW")


def parse_api_id() -> int:
    try:
        return int(required_env("TELEGRAM_IMPORT_API_ID"))
    except ValueError as exc:
        raise SystemExit("TELEGRAM_IMPORT_API_ID must be an integer") from exc


def normalize_username(value: str) -> str:
    return value.strip().removeprefix("@").lower()


def normalize_phone(value: str) -> str:
    return "".join(character for character in value if character.isdigit())


@dataclass(frozen=True)
class Paths:
    session_dir: Path
    session_base: Path
    metadata: Path
    lock: Path
    inbox: Path


def paths() -> Paths:
    configured = Path(required_env("TELEGRAM_IMPORT_SESSION_DIR"))
    if configured.is_symlink():
        raise SystemExit("Session directory must not be a symlink")
    session_dir = configured.resolve()
    if not session_dir.is_dir():
        raise SystemExit(f"Session directory does not exist: {session_dir}")
    if stat.S_IMODE(session_dir.stat().st_mode) & 0o077:
        raise SystemExit(f"Session directory must not be accessible by group/other: {session_dir}")
    name = os.getenv("TELEGRAM_IMPORT_SESSION_NAME", DEFAULT_SESSION_NAME).strip()
    if not re.fullmatch(r"[A-Za-z0-9_-]+", name):
        raise SystemExit("TELEGRAM_IMPORT_SESSION_NAME may contain only letters, digits, _ and -")
    return Paths(session_dir, session_dir / name, session_dir / "authorized-channel.json", session_dir / ".session.lock", session_dir / "shadow-inbox.sqlite")


def read_metadata(path: Path) -> dict[str, object]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"Cannot read authorization metadata: {path}") from exc
    if not isinstance(payload, dict):
        raise SystemExit(f"Invalid authorization metadata: {path}")
    return payload


def detect_source_ip() -> ipaddress.IPv4Address:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("1.1.1.1", 80))
        address = ipaddress.ip_address(sock.getsockname()[0])
    finally:
        sock.close()
    if not isinstance(address, ipaddress.IPv4Address):
        raise SystemExit(f"Expected an IPv4 container source address, got {address}")
    return address


def require_routed_source() -> ipaddress.IPv4Address:
    network = ipaddress.ip_network(os.getenv("TELEGRAM_IMPORT_REQUIRED_SOURCE_CIDR", DEFAULT_REQUIRED_SOURCE_CIDR))
    address = detect_source_ip()
    if address not in network:
        raise SystemExit(f"Refusing Telegram access: container source {address} is outside routed VPN subnet {network}")
    return address


@dataclass(frozen=True)
class NotifierConfig:
    explicitly_enabled: bool
    token: str | None
    chat_id: int | None
    thread_id: int | None

    @property
    def enabled(self) -> bool:
        return self.explicitly_enabled and self.token is not None


def notifier_config() -> NotifierConfig:
    enabled_raw = os.getenv("TELEGRAM_IMPORT_OPERATOR_NOTIFICATIONS_ENABLED", "false").strip().casefold()
    if enabled_raw not in {"true", "false"}:
        raise SystemExit("TELEGRAM_IMPORT_OPERATOR_NOTIFICATIONS_ENABLED must be true or false")
    token = os.getenv("TELEGRAM_IMPORT_OPERATOR_BOT_TOKEN", "").strip() or None
    chat = os.getenv("TELEGRAM_IMPORT_OPERATOR_CHAT_ID", "").strip() or None
    thread = os.getenv("TELEGRAM_IMPORT_OPERATOR_THREAD_ID", "").strip() or None
    if bool(token) != bool(chat):
        raise SystemExit("TELEGRAM_IMPORT_OPERATOR_BOT_TOKEN and TELEGRAM_IMPORT_OPERATOR_CHAT_ID must be set together")
    if thread and not chat:
        raise SystemExit("TELEGRAM_IMPORT_OPERATOR_THREAD_ID requires TELEGRAM_IMPORT_OPERATOR_CHAT_ID")
    try:
        chat_id = int(chat) if chat else None
        thread_id = int(thread) if thread else None
    except ValueError as exc:
        raise SystemExit("Operator chat/thread IDs must be integers") from exc
    if enabled_raw == "true" and not token:
        raise SystemExit("Operator notifications are enabled but bot token/chat ID are missing")
    return NotifierConfig(enabled_raw == "true", token, chat_id, thread_id)


@dataclass(frozen=True)
class BackendDeliveryConfig:
    url: str
    key_id: str
    secret: str


def delivery_mode() -> DeliveryMode:
    raw = required_env("TELEGRAM_IMPORT_DELIVERY_MODE")
    try:
        mode = DeliveryMode(raw)
    except ValueError as exc:
        raise SystemExit("TELEGRAM_IMPORT_DELIVERY_MODE must be exactly DIRECT or BACKEND") from exc
    if mode is DeliveryMode.DIRECT and not notifier_config().enabled:
        raise SystemExit("DIRECT delivery mode requires enabled operator notifications")
    if mode is DeliveryMode.BACKEND:
        legacy = notifier_config()
        if legacy.explicitly_enabled or legacy.token is not None or legacy.chat_id is not None or legacy.thread_id is not None:
            raise SystemExit("BACKEND delivery mode must not retain legacy operator Bot API credentials")
        backend_delivery_config()
    return mode


def backend_delivery_config() -> BackendDeliveryConfig:
    url = required_env("TELEGRAM_IMPORT_BACKEND_INGEST_URL")
    if not url.startswith("https://"):
        raise SystemExit("TELEGRAM_IMPORT_BACKEND_INGEST_URL must use HTTPS")
    return BackendDeliveryConfig(
        url=url,
        key_id=required_env("TELEGRAM_IMPORT_BACKEND_INGEST_KEY_ID"),
        secret=required_env("TELEGRAM_IMPORT_BACKEND_INGEST_SECRET"),
    )
