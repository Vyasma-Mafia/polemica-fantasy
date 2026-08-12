from __future__ import annotations

import asyncio
import json
import random
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from .config import NotifierConfig


@dataclass(frozen=True)
class DeliveryResult:
    delivered: bool
    terminal: bool = False
    retry_after: float = 0
    error: str | None = None


def render(payload: str) -> str:
    data = json.loads(payload)
    event = data["eventType"]
    labels = {"CANDIDATE": "Найден кандидат", "REVISED": "Кандидат изменён", "RETRACTED": "Кандидат отозван"}
    return (
        f"[SHADOW][NO PRODUCTION WRITE]\n"
        f"{labels[event]}: {data['classification']}, лига {data.get('league') or '-'}, "
        f"сообщение #{data['messageId']}, ревизия {data['revision']}.\n"
        f"Ключ: {data['eventKey']}\nИсточник: {data['sourceUrl']}\n"
        "Серия не создана и не финализирована."
    )


class BotNotifier:
    def __init__(self, config: NotifierConfig):
        self.config = config

    async def send(self, text: str) -> DeliveryResult:
        if not self.config.enabled:
            return DeliveryResult(False, terminal=True, error="notifier disabled")
        return await asyncio.to_thread(self._send_sync, text[:4096])

    def _send_sync(self, text: str) -> DeliveryResult:
        assert self.config.token and self.config.chat_id is not None
        data: dict[str, object] = {"chat_id": self.config.chat_id, "text": text}
        if self.config.thread_id is not None:
            data["message_thread_id"] = self.config.thread_id
        request = urllib.request.Request(
            f"https://api.telegram.org/bot{self.config.token}/sendMessage",
            data=urllib.parse.urlencode(data).encode(), method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                if 200 <= response.status < 300:
                    return DeliveryResult(True)
                return DeliveryResult(False, retry_after=5 + random.random(), error=f"HTTP {response.status}")
        except urllib.error.HTTPError as exc:
            if exc.code == 403:
                return DeliveryResult(False, terminal=True, error="HTTP 403")
            if exc.code == 429:
                retry = 1
                try:
                    body = json.loads(exc.read().decode("utf-8"))
                    retry = int(body.get("parameters", {}).get("retry_after", 1))
                except (ValueError, json.JSONDecodeError, UnicodeDecodeError):
                    pass
                return DeliveryResult(False, retry_after=retry + random.random(), error="HTTP 429")
            if 500 <= exc.code < 600:
                return DeliveryResult(False, retry_after=5 + random.random() * 5, error=f"HTTP {exc.code}")
            return DeliveryResult(False, terminal=True, error=f"HTTP {exc.code}")
        except (urllib.error.URLError, TimeoutError):
            return DeliveryResult(False, retry_after=5 + random.random() * 5, error="network error")


def retry_timestamp(seconds: float) -> str:
    return (datetime.now(timezone.utc) + timedelta(seconds=seconds)).isoformat()
