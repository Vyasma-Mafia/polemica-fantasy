from __future__ import annotations

import asyncio
import hashlib
import hmac
import json
import random
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from .config import BackendDeliveryConfig


@dataclass(frozen=True)
class BackendDeliveryResult:
    delivered: bool
    terminal: bool = False
    retry_after: float = 0
    error: str | None = None


class BackendIngestClient:
    def __init__(self, config: BackendDeliveryConfig):
        self.config = config

    async def send(self, delivery_id: str, payload: str) -> BackendDeliveryResult:
        return await asyncio.to_thread(self._send_sync, delivery_id, payload)

    def _send_sync(self, delivery_id: str, payload: str) -> BackendDeliveryResult:
        timestamp = str(int(datetime.now(timezone.utc).timestamp()))
        body = payload.encode("utf-8")
        signed = f"{self.config.key_id}\n{delivery_id}\n{timestamp}\n".encode("ascii") + body
        signature = hmac.new(self.config.secret.encode("utf-8"), signed, hashlib.sha256).hexdigest()
        request = urllib.request.Request(
            self.config.url,
            data=body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "X-League-Import-Key-Id": self.config.key_id,
                "X-League-Import-Delivery-Id": delivery_id,
                "X-League-Import-Timestamp": timestamp,
                "X-League-Import-Signature": signature,
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                if 200 <= response.status < 300:
                    return BackendDeliveryResult(True)
                return BackendDeliveryResult(False, retry_after=5 + random.random(), error=f"HTTP {response.status}")
        except urllib.error.HTTPError as exc:
            if exc.code == 429:
                retry = int(exc.headers.get("Retry-After", "5"))
                return BackendDeliveryResult(False, retry_after=retry + random.random(), error="HTTP 429")
            if 500 <= exc.code < 600:
                return BackendDeliveryResult(False, retry_after=5 + random.random() * 5, error=f"HTTP {exc.code}")
            return BackendDeliveryResult(False, terminal=True, error=f"HTTP {exc.code}")
        except (urllib.error.URLError, TimeoutError):
            return BackendDeliveryResult(False, retry_after=5 + random.random() * 5, error="network error")


def retry_timestamp(seconds: float) -> str:
    return (datetime.now(timezone.utc) + timedelta(seconds=seconds)).isoformat()
