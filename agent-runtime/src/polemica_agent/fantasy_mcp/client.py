from __future__ import annotations

import dataclasses
import json
import socket
import urllib.error
import urllib.parse
import urllib.request
from collections.abc import Callable, Mapping
from typing import Any, Protocol

from polemica_agent.common.canonical import redact
from polemica_agent.common.operations import DeterministicUpstreamError


MAX_ERROR_LENGTH = 500
MAX_RESPONSE_BYTES = 8 * 1024 * 1024
AMBIGUOUS_HTTP_STATUSES = frozenset({408, 425, 499})
ALLOWED_PREFIXES = (
    "/api/v1/me",
    "/api/v1/tournaments",
    "/api/v1/series",
    "/api/v1/store",
    "/api/v1/marketplace",
    "/api/v1/card-value",
    "/api/v1/cards/merge",
    "/api/v1/legendary-upgrade",
    "/api/v1/achievements",
    "/api/v1/periodic-ratings",
)
FORBIDDEN_FRAGMENTS = ("/admin/", "/telegram/", "/internal/", "/users/")


@dataclasses.dataclass(frozen=True)
class HttpResponse:
    status: int
    body: Any = None
    headers: Mapping[str, str] = dataclasses.field(default_factory=dict)


class HttpTransport(Protocol):
    def request(
        self,
        *,
        method: str,
        url: str,
        headers: Mapping[str, str],
        json_body: Any | None,
        timeout_seconds: float,
    ) -> HttpResponse: ...


class FantasyApiError(RuntimeError):
    def __init__(self, status: int | None, code: str, message: str, *, uncertain: bool) -> None:
        super().__init__(message[:MAX_ERROR_LENGTH])
        self.status = status
        self.code = code
        self.uncertain = uncertain


class UrlLibTransport:
    """Small stdlib transport; callers cannot choose arbitrary Fantasy paths."""

    def request(
        self,
        *,
        method: str,
        url: str,
        headers: Mapping[str, str],
        json_body: Any | None,
        timeout_seconds: float,
    ) -> HttpResponse:
        data = None if json_body is None else json.dumps(json_body, separators=(",", ":")).encode("utf-8")
        request = urllib.request.Request(url, data=data, headers=dict(headers), method=method)
        try:
            with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
                raw = response.read(MAX_RESPONSE_BYTES + 1)
                if len(raw) > MAX_RESPONSE_BYTES:
                    raise FantasyApiError(None, "RESPONSE_TOO_LARGE", "Fantasy response exceeded limit", uncertain=True)
                body = _decode_body(raw, response.headers.get("Content-Type"))
                return HttpResponse(response.status, body, dict(response.headers.items()))
        except urllib.error.HTTPError as exc:
            raw = exc.read(MAX_ERROR_LENGTH)
            return HttpResponse(exc.code, _decode_body(raw, exc.headers.get("Content-Type")), dict(exc.headers.items()))
        except (urllib.error.URLError, socket.timeout, TimeoutError) as exc:
            raise FantasyApiError(None, "TRANSPORT_ERROR", str(exc), uncertain=True) from exc


class FantasyHttpClient:
    """Hard-coded user API client. It intentionally has no public generic request method."""

    def __init__(
        self,
        base_url: str,
        bearer_token: str | Callable[[], str],
        *,
        transport: HttpTransport | None = None,
        timeout_seconds: float = 20.0,
        allow_http_localhost: bool = False,
    ) -> None:
        parsed = urllib.parse.urlsplit(base_url.rstrip("/"))
        allowed_http = allow_http_localhost and parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost"}
        if parsed.scheme != "https" and not allowed_http:
            raise ValueError("Fantasy base_url must use HTTPS")
        if not parsed.netloc or parsed.path not in {"", "/"} or parsed.query or parsed.fragment:
            raise ValueError("Fantasy base_url must contain only scheme and authority")
        if timeout_seconds <= 0 or timeout_seconds >= 60:
            raise ValueError("Fantasy timeout must be in (0, 60) seconds")
        self._base_url = base_url.rstrip("/")
        self._token = bearer_token
        self._transport = transport or UrlLibTransport()
        self._timeout_seconds = timeout_seconds

    def _get(self, path: str, query: Mapping[str, Any] | None = None) -> Any:
        return self._request("GET", path, query=query)

    def _post(self, path: str, body: Any | None = None, *, idempotency_key: str | None = None) -> Any:
        return self._request("POST", path, body=body, idempotency_key=idempotency_key)

    def _put(self, path: str, body: Any | None = None) -> Any:
        return self._request("PUT", path, body=body)

    def _patch(self, path: str, body: Any | None = None) -> Any:
        return self._request("PATCH", path, body=body)

    def _delete(self, path: str) -> Any:
        return self._request("DELETE", path)

    def _request(
        self,
        method: str,
        path: str,
        *,
        query: Mapping[str, Any] | None = None,
        body: Any | None = None,
        idempotency_key: str | None = None,
    ) -> Any:
        _validate_path(path)
        encoded_query = urllib.parse.urlencode(_query_items(query or {}), doseq=True)
        url = f"{self._base_url}{path}" + (f"?{encoded_query}" if encoded_query else "")
        token = self._token() if callable(self._token) else self._token
        if not token or "\n" in token or "\r" in token:
            raise FantasyApiError(None, "CREDENTIAL_UNAVAILABLE", "Fantasy credential unavailable", uncertain=False)
        headers = {"Accept": "application/json", "Authorization": f"Bearer {token}"}
        if body is not None:
            headers["Content-Type"] = "application/json"
        if idempotency_key is not None:
            if not idempotency_key or len(idempotency_key) > 128 or any(c.isspace() for c in idempotency_key):
                raise ValueError("operation_id is not a valid Idempotency-Key")
            headers["Idempotency-Key"] = idempotency_key
        response = self._transport.request(
            method=method,
            url=url,
            headers=headers,
            json_body=body,
            timeout_seconds=self._timeout_seconds,
        )
        if 200 <= response.status < 300:
            return redact(response.body)
        message = _error_message(response.body, response.status)
        code = f"HTTP_{response.status}"
        if response.status in AMBIGUOUS_HTTP_STATUSES:
            raise FantasyApiError(response.status, code, message, uncertain=True)
        if 400 <= response.status < 500:
            raise DeterministicUpstreamError(code, message)
        raise FantasyApiError(response.status, code, message, uncertain=True)


def _validate_path(path: str) -> None:
    if not path.startswith("/api/v1/") or "?" in path or "#" in path or ".." in path or "//" in path:
        raise ValueError("Invalid Fantasy API path")
    if not any(path == prefix or path.startswith(prefix + "/") for prefix in ALLOWED_PREFIXES):
        raise ValueError("Fantasy API path is not allowlisted")
    if any(fragment in path for fragment in FORBIDDEN_FRAGMENTS):
        raise ValueError("Fantasy API path is forbidden")


def _query_items(query: Mapping[str, Any]) -> list[tuple[str, Any]]:
    items: list[tuple[str, Any]] = []
    for key, value in query.items():
        if value is None:
            continue
        if not key.replace("_", "").isalnum():
            raise ValueError("Invalid query parameter")
        if isinstance(value, (list, tuple)):
            items.extend((key, item) for item in value)
        else:
            items.append((key, value))
    return items


def _decode_body(raw: bytes, content_type: str | None) -> Any:
    if not raw:
        return None
    text = raw.decode("utf-8", errors="replace")
    if content_type and "json" in content_type.lower():
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return {"message": text[:MAX_ERROR_LENGTH]}
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return text[:MAX_ERROR_LENGTH]


def _error_message(body: Any, status: int) -> str:
    if isinstance(body, Mapping):
        candidate = body.get("message") or body.get("error")
        if candidate is not None:
            return str(candidate)[:MAX_ERROR_LENGTH]
    if isinstance(body, str):
        return body[:MAX_ERROR_LENGTH]
    return f"Fantasy API returned HTTP {status}"
