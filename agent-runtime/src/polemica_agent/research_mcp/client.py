"""Typed, read-only Polemica client with bounded request concurrency and rate."""

from __future__ import annotations

import json
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from contextlib import contextmanager
from dataclasses import dataclass
from typing import Any, Iterator, Mapping, Protocol

from .errors import ContractError, UpstreamError


Json = Any


class PolemicaClient(Protocol):
    """Only allowlisted reads needed by Research tools."""

    def get_player_games(self, player_id: int, page: int, limit: int) -> Mapping[str, Any]: ...
    def get_match(self, match_id: int, version: int | None = None) -> Mapping[str, Any]: ...
    def get_competition_game(
        self, competition_id: int, game_id: int, version: int | None = None
    ) -> Mapping[str, Any]: ...
    def list_competitions(self) -> list[Mapping[str, Any]]: ...
    def get_competition(self, competition_id: int) -> Mapping[str, Any]: ...
    def get_competition_members(self, competition_id: int) -> list[Mapping[str, Any]]: ...
    def get_competition_games(self, competition_id: int) -> list[Mapping[str, Any]]: ...
    def get_competition_metrics(
        self, competition_id: int, scoring_type: int | None = None
    ) -> list[Mapping[str, Any]]: ...


class RequestGate:
    def __init__(self, *, max_concurrency: int = 4, min_interval_seconds: float = 0.05) -> None:
        if not 1 <= max_concurrency <= 32:
            raise ContractError("max_concurrency must be in 1..32")
        if not 0 <= min_interval_seconds <= 60:
            raise ContractError("min_interval_seconds must be in 0..60")
        self._semaphore = threading.BoundedSemaphore(max_concurrency)
        self._spacing_lock = threading.Lock()
        self._last_started = 0.0
        self._interval = min_interval_seconds

    @contextmanager
    def request(self) -> Iterator[None]:
        with self._semaphore:
            with self._spacing_lock:
                remaining = self._interval - (time.monotonic() - self._last_started)
                if remaining > 0:
                    time.sleep(remaining)
                self._last_started = time.monotonic()
            yield


@dataclass(frozen=True)
class HttpClientConfig:
    api_base_url: str
    profile_base_url: str = "https://polemicagame.com"
    username: str | None = None
    password: str | None = None
    timeout_seconds: float = 20.0
    max_response_bytes: int = 8 * 1024 * 1024


class HttpPolemicaClient:
    """HTTP implementation with typed public methods and a private transport.

    The only POST compiled into this client is `/v1/auth/login`. No caller can
    provide an arbitrary method, URL, or path.
    """

    def __init__(self, config: HttpClientConfig, *, gate: RequestGate | None = None) -> None:
        self.config = config
        self._api_base = _validated_base(config.api_base_url)
        self._profile_base = _validated_base(config.profile_base_url)
        self._gate = gate or RequestGate()
        self._token: str | None = None
        self._token_lock = threading.Lock()

    def get_player_games(self, player_id: int, page: int, limit: int) -> Mapping[str, Any]:
        _positive(player_id, "player_id")
        if not 1 <= page <= 10_000:
            raise ContractError("page must be in 1..10000")
        if not 1 <= limit <= 200:
            raise ContractError("limit must be in 1..200")
        value = self._get_profile(
            "/profile/default/get-games",
            {"userId": player_id, "page": page, "limit": limit},
            operation="get_player_games",
        )
        if not isinstance(value, dict):
            raise UpstreamError("get_player_games")
        return value

    def get_match(self, match_id: int, version: int | None = None) -> Mapping[str, Any]:
        _positive(match_id, "match_id")
        value = self._get_api(
            f"/v1/matches/{match_id}", _version_query(version), operation="get_match"
        )
        return _object(value, "get_match")

    def get_competition_game(
        self, competition_id: int, game_id: int, version: int | None = None
    ) -> Mapping[str, Any]:
        _positive(competition_id, "competition_id")
        _positive(game_id, "game_id")
        value = self._get_api(
            f"/v1/competitions/{competition_id}/games/{game_id}",
            _version_query(version),
            operation="get_competition_game",
        )
        return _object(value, "get_competition_game")

    def list_competitions(self) -> list[Mapping[str, Any]]:
        return _object_list(self._get_api("/v1/competitions", {}, operation="list_competitions"), "list_competitions")

    def get_competition(self, competition_id: int) -> Mapping[str, Any]:
        _positive(competition_id, "competition_id")
        return _object(
            self._get_api(f"/v1/competitions/{competition_id}", {}, operation="get_competition"),
            "get_competition",
        )

    def get_competition_members(self, competition_id: int) -> list[Mapping[str, Any]]:
        _positive(competition_id, "competition_id")
        return _object_list(
            self._get_api(f"/v1/competitions/{competition_id}/members", {}, operation="get_competition_members"),
            "get_competition_members",
        )

    def get_competition_games(self, competition_id: int) -> list[Mapping[str, Any]]:
        _positive(competition_id, "competition_id")
        return _object_list(
            self._get_api(f"/v1/competitions/{competition_id}/games", {}, operation="get_competition_games"),
            "get_competition_games",
        )

    def get_competition_metrics(
        self, competition_id: int, scoring_type: int | None = None
    ) -> list[Mapping[str, Any]]:
        _positive(competition_id, "competition_id")
        query: dict[str, int] = {}
        if scoring_type is not None:
            if not 0 <= scoring_type <= 100:
                raise ContractError("scoring_type must be in 0..100")
            query["scoringType"] = scoring_type
        return _object_list(
            self._get_api(f"/v1/competitions/{competition_id}/metrics", query, operation="get_competition_metrics"),
            "get_competition_metrics",
        )

    def _get_api(self, path: str, query: Mapping[str, int], *, operation: str) -> Json:
        token = self._auth_token()
        try:
            return self._request("GET", self._api_base, path, query, operation=operation, token=token)
        except UpstreamError as error:
            if error.status != 401:
                raise
        with self._token_lock:
            self._token = None
        return self._request("GET", self._api_base, path, query, operation=operation, token=self._auth_token())

    def _get_profile(self, path: str, query: Mapping[str, int], *, operation: str) -> Json:
        return self._request("GET", self._profile_base, path, query, operation=operation, token=None)

    def _auth_token(self) -> str:
        if self._token:
            return self._token
        with self._token_lock:
            if self._token:
                return self._token
            if not self.config.username or not self.config.password:
                raise UpstreamError("login")
            value = self._request(
                "POST",
                self._api_base,
                "/v1/auth/login",
                {},
                operation="login",
                body={"username": self.config.username, "password": self.config.password},
                token=None,
            )
            if not isinstance(value, dict) or not isinstance(value.get("access_token"), str):
                raise UpstreamError("login")
            self._token = value["access_token"]
            return self._token

    def _request(
        self,
        method: str,
        base: str,
        path: str,
        query: Mapping[str, int],
        *,
        operation: str,
        token: str | None,
        body: Mapping[str, str] | None = None,
    ) -> Json:
        # Private and only called above with compile-time allowlisted paths/methods.
        url = f"{base}{path}"
        if query:
            url += "?" + urllib.parse.urlencode(query)
        data = None if body is None else json.dumps(body).encode("utf-8")
        headers = {"Accept": "application/json"}
        if body is not None:
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with self._gate.request(), urllib.request.urlopen(request, timeout=self.config.timeout_seconds) as response:
                length = response.headers.get("Content-Length")
                if length and int(length) > self.config.max_response_bytes:
                    raise UpstreamError(operation)
                raw = response.read(self.config.max_response_bytes + 1)
                if len(raw) > self.config.max_response_bytes:
                    raise UpstreamError(operation)
                return json.loads(raw)
        except urllib.error.HTTPError as error:
            raise UpstreamError(operation, error.code) from None
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, ValueError):
            raise UpstreamError(operation) from None


def _validated_base(value: str) -> str:
    parsed = urllib.parse.urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc or parsed.query or parsed.fragment:
        raise ContractError("Polemica base URL must be an HTTPS origin")
    return value.rstrip("/")


def _positive(value: int, name: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ContractError(f"{name} must be a positive integer")
    return value


def _version_query(version: int | None) -> dict[str, int]:
    if version is None:
        return {}
    return {"version": _positive(version, "version")}


def _object(value: Json, operation: str) -> Mapping[str, Any]:
    if not isinstance(value, dict):
        raise UpstreamError(operation)
    return value


def _object_list(value: Json, operation: str) -> list[Mapping[str, Any]]:
    if not isinstance(value, list) or any(not isinstance(item, dict) for item in value):
        raise UpstreamError(operation)
    return value
