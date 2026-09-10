"""Research use cases: collection, provenance, partial results, and aggregates."""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from typing import Any, Callable, Iterable, Mapping, Sequence

from . import analytics
from .cache import RawPayloadCache, RawPayloadRecord
from .client import PolemicaClient
from .errors import ContractError, ResearchError, UpstreamError
from .snapshots import SnapshotCoordinator
from .types import PartialError, Provenance, ResearchResult, isoformat, utc_now


def _record_manifest(record: RawPayloadRecord) -> dict[str, Any]:
    return {"source": record.source, "objectId": record.object_id, "sourceVersion": record.source_version,
            "payloadHash": record.payload_hash, "firstSeenAt": record.first_seen_at,
            "fetchedAt": record.fetched_at, "parserVersion": record.parser_version,
            "correctionIndex": record.correction_index, "isCorrection": record.correction_index > 1,
            "completeness": "COMPLETE"}


class ResearchService:
    def __init__(
        self,
        client: PolemicaClient,
        cache: RawPayloadCache,
        snapshots: SnapshotCoordinator,
        *,
        clock: Callable[[], datetime] = utc_now,
        max_parallel_reads: int = 4,
    ) -> None:
        if not 1 <= max_parallel_reads <= 8:
            raise ContractError("max_parallel_reads must be in 1..8")
        self.client = client
        self.cache = cache
        self.snapshots = snapshots
        self.clock = clock
        self.max_parallel_reads = max_parallel_reads

    def begin_snapshot(self, run_id: str, snapshot_id: str | None = None) -> dict[str, Any]:
        snapshot = self.snapshots.begin(run_id, snapshot_id=snapshot_id)
        return _snapshot_dict(snapshot)

    def seal_snapshot(self, snapshot_id: str) -> dict[str, Any]:
        return _snapshot_dict(self.snapshots.seal(snapshot_id))

    def get_player_games(
        self,
        snapshot_id: str,
        player_id: int,
        *,
        page_size: int = 100,
        max_pages: int = 10,
        minimum_rows: int | None = None,
    ) -> ResearchResult:
        _positive(player_id, "player_id")
        if not 1 <= page_size <= 200:
            raise ContractError("page_size must be in 1..200")
        if not 1 <= max_pages <= 50:
            raise ContractError("max_pages must be in 1..50")
        if minimum_rows is not None and (type(minimum_rows) is not int or not 1 <= minimum_rows <= 500):
            raise ContractError("minimum_rows must be in 1..500")
        self.snapshots.require_collecting(snapshot_id)
        rows_by_id: dict[tuple[Any, ...], Mapping[str, Any]] = {}
        raw_row_count = 0
        invalid_row_count = 0
        records: list[RawPayloadRecord] = []
        errors: list[PartialError] = []
        total_count: int | None = None
        reached_end = False
        for page in range(1, max_pages + 1):
            self.snapshots.require_collecting(snapshot_id)
            try:
                payload = self.client.get_player_games(player_id, page, page_size)
                record = self._record(
                    snapshot_id,
                    "profile-games-page",
                    f"{player_id}:{page}:{page_size}",
                    payload,
                )
                records.append(record)
                raw_rows = payload.get("rows")
                if not isinstance(raw_rows, list):
                    raise UpstreamError("get_player_games")
                if isinstance(payload.get("totalCount"), int):
                    total_count = payload["totalCount"]
                for row in raw_rows:
                    raw_row_count += 1
                    if isinstance(row, Mapping) and type(row.get("id")) is int:
                        rows_by_id.setdefault((str(row.get("type")), str(row.get("competition_id")), row["id"]), row)
                    else:
                        invalid_row_count += 1
                if minimum_rows is not None and len(rows_by_id) >= minimum_rows:
                    break
                if not raw_rows:
                    reached_end = True
                    break
                if total_count is not None and len(rows_by_id) >= total_count:
                    reached_end = True
                    break
                if total_count is None and len(raw_rows) < page_size:
                    reached_end = True
                    break
            except ResearchError as error:
                errors.append(_partial("get_player_games", error, f"player:{player_id}:page:{page}"))
                break
        complete = not errors and (
            reached_end
            or (total_count is not None and len(rows_by_id) >= total_count)
            or (minimum_rows is not None and len(rows_by_id) >= minimum_rows)
        )
        if not complete and not errors:
            errors.append(PartialError("get_player_games", "PAGE_BOUND", "pagination bound reached", f"player:{player_id}"))
        rows = list(rows_by_id.values())
        if minimum_rows is not None:
            rows = rows[:minimum_rows]
        return self._result(
            data={"playerId": player_id, "rows": rows, "reportedTotalCount": total_count,
                  "rawRowCount": raw_row_count, "invalidRowCount": invalid_row_count,
                  "duplicateRowCount": raw_row_count - invalid_row_count - len(rows_by_id),
                  "uniqueRowCount": len(rows_by_id),
                  "requestedLimit": minimum_rows, "coverage": "WINDOW" if minimum_rows else "FULL_HISTORY"},
            snapshot_id=snapshot_id,
            source="profile/default/get-games",
            records=records,
            sample_size=len(rows),
            complete=complete,
            errors=errors,
        )

    def get_game(
        self,
        snapshot_id: str,
        *,
        kind: str,
        game_id: int,
        competition_id: int | None = None,
        version: int | None = None,
    ) -> ResearchResult:
        _positive(game_id, "game_id")
        if not isinstance(kind, str) or kind not in {"match", "competition"}:
            raise ContractError("kind must be 'match' or 'competition'")
        if competition_id is not None or kind == "competition":
            _positive(competition_id, "competition_id")
        if version is not None:
            _positive(version, "version")
        self.snapshots.require_collecting(snapshot_id)
        try:
            if kind == "match":
                payload = self.client.get_match(game_id, version)
                source, object_id = "match", str(game_id)
            else:
                payload = self.client.get_competition_game(competition_id, game_id, version)  # type: ignore[arg-type]
                source, object_id = "competition-game", f"{competition_id}:{game_id}"
            record = self._record(snapshot_id, source, object_id, payload, source_version=version or payload.get("version"))
            return self._result(payload, snapshot_id, source, [record], 1, True, [])
        except ResearchError as error:
            return self._result(None, snapshot_id, kind, [], 0, False, [_partial("get_game", error, f"game:{game_id}")])

    def list_competitions(self, snapshot_id: str) -> ResearchResult:
        return self._single_list(snapshot_id, "competitions", "all", self.client.list_competitions)

    def get_competition(self, snapshot_id: str, competition_id: int) -> ResearchResult:
        _positive(competition_id, "competition_id")
        return self._single_object(
            snapshot_id, "competition", str(competition_id), lambda: self.client.get_competition(competition_id)
        )

    def get_competition_members(self, snapshot_id: str, competition_id: int) -> ResearchResult:
        _positive(competition_id, "competition_id")
        return self._single_list(
            snapshot_id,
            "competition-members",
            str(competition_id),
            lambda: self.client.get_competition_members(competition_id),
        )

    def get_competition_games(self, snapshot_id: str, competition_id: int) -> ResearchResult:
        _positive(competition_id, "competition_id")
        return self._single_list(
            snapshot_id,
            "competition-games",
            str(competition_id),
            lambda: self.client.get_competition_games(competition_id),
        )

    def get_competition_metrics(
        self, snapshot_id: str, competition_id: int, scoring_type: int | None = None
    ) -> ResearchResult:
        _positive(competition_id, "competition_id")
        return self._single_list(
            snapshot_id,
            "competition-metrics",
            f"{competition_id}:{scoring_type if scoring_type is not None else 'default'}",
            lambda: self.client.get_competition_metrics(competition_id, scoring_type),
        )

    def get_player_statistics(self, snapshot_id: str, player_id: int, *, max_pages: int = 10) -> ResearchResult:
        collected = self.get_player_games(snapshot_id, player_id, max_pages=max_pages)
        rows = collected.data["rows"]
        return self._derived(collected, analytics.player_statistics(rows, complete=collected.provenance.complete), "player-statistics")

    def get_player_recent_form(
        self, snapshot_id: str, player_id: int, *, window: int = 20, max_pages: int = 10
    ) -> ResearchResult:
        collected = self.get_player_games(
            snapshot_id, player_id, max_pages=max_pages, minimum_rows=window
        )
        try:
            data = analytics.recent_form(collected.data["rows"], window, complete=collected.provenance.complete)
        except ValueError as error:
            raise ContractError(str(error)) from None
        return self._derived(collected, data, "player-recent-form")

    def get_player_role_distribution(
        self, snapshot_id: str, player_id: int, *, max_pages: int = 10
    ) -> ResearchResult:
        collected = self.get_player_games(snapshot_id, player_id, max_pages=max_pages)
        data = analytics.role_distribution(collected.data["rows"], complete=collected.provenance.complete)
        return self._derived(collected, data, "player-role-distribution")

    def get_player_perk_rates(
        self,
        snapshot_id: str,
        player_id: int,
        games: Sequence[Mapping[str, Any]],
        perk_ids: Sequence[str] | None = None,
    ) -> ResearchResult:
        _positive(player_id, "player_id")
        if not 1 <= len(games) <= 100:
            raise ContractError("games must contain 1..100 typed game locators")
        # Validate the entire request before concurrent reads can attach evidence.
        # A typo is not a failed upstream observation and must not poison COLLECT.
        for locator in games:
            if not isinstance(locator, Mapping):
                raise ContractError("games must contain typed game locator objects")
            if not isinstance(locator.get("kind"), str) or locator["kind"] not in {"match", "competition"}:
                raise ContractError("kind must be 'match' or 'competition'")
            _required_int(locator, "game_id")
            if locator["kind"] == "competition":
                _required_int(locator, "competition_id")
            else:
                _optional_int(locator, "competition_id")
            _optional_int(locator, "version")
        if perk_ids is not None:
            if not perk_ids or any(not isinstance(item, str) for item in perk_ids):
                raise ContractError("perk_ids must be non-empty strings")
            if len(set(perk_ids)) != len(perk_ids):
                raise ContractError("perk_ids must be non-empty and unique")
            if set(perk_ids) - set(analytics.PERK_IDS):
                raise ContractError("unknown perk_ids; use supported perk identifiers")
        payloads: list[Mapping[str, Any]] = []
        hashes: set[str] = set()
        manifests: list[Mapping[str, Any]] = []
        errors: list[PartialError] = []
        if any("base_points" in locator for locator in games):
            raise ContractError("base_points is not accepted without trusted source provenance")
        unique: dict[tuple[Any, ...], Mapping[str, Any]] = {}
        for locator in games:
            key = (locator["kind"], locator.get("competition_id") if locator["kind"] == "competition" else None, locator["game_id"])
            if key in unique and unique[key].get("version") != locator.get("version"):
                raise ContractError("conflicting versions for the same game locator")
            unique[key] = locator
        requested_count = len(games)
        games = list(unique.values())
        payload_locators: list[Mapping[str, Any]] = []

        def fetch(locator: Mapping[str, Any]) -> ResearchResult:
            return self.get_game(
                snapshot_id,
                kind=str(locator.get("kind")),
                game_id=_required_int(locator, "game_id"),
                competition_id=_optional_int(locator, "competition_id"),
                version=_optional_int(locator, "version"),
            )

        # Bounded pool plus the client's request gate protects the upstream.
        with ThreadPoolExecutor(max_workers=self.max_parallel_reads) as executor:
            futures = {executor.submit(fetch, locator): locator for locator in games}
            for future in as_completed(futures):
                locator = futures[future]
                try:
                    result = future.result()
                    if isinstance(result.data, Mapping):
                        if type(result.data.get("id")) is not int or result.data["id"] != locator["game_id"]:
                            errors.append(PartialError("get_player_perk_rates", "GAME_IDENTITY_MISMATCH", "game payload does not match requested locator", f"{locator['kind']}:{locator['game_id']}"))
                            continue
                        payloads.append(result.data)
                        payload_locators.append(locator)
                        hashes.update(result.provenance.payload_hashes)
                        manifests.extend(result.provenance.evidence_manifest)
                    errors.extend(result.provenance.errors)
                except ResearchError as error:
                    errors.append(_partial("get_player_perk_rates", error, f"game:{locator.get('game_id')}"))
        points: list[float | None] = [None] * len(payloads)
        if perk_ids is None or "ninja" in perk_ids:
            points, points_records, points_errors = self._trusted_profile_points(snapshot_id, player_id, payloads, payload_locators)
            errors.extend(points_errors)
            for record in points_records:
                hashes.add(record.payload_hash)
                manifests.append(_record_manifest(record))
        try:
            data = analytics.perk_rates(
                payloads,
                player_id,
                perk_ids=perk_ids,
                base_points_by_index=points,
                complete=not errors and len(payloads) == len(games),
            )
        except ValueError as error:
            raise ContractError(str(error)) from None
        data["requestedLocatorCount"] = requested_count
        data["duplicateLocatorCount"] = requested_count - len(games)
        for skipped in data["skippedGames"]:
            errors.append(PartialError("get_player_perk_rates", "GAME_PARSE_ERROR", "game excluded from perk sample", f"game:{skipped['gameId']}"))
        # Records were attached by get_game; create provenance directly to avoid duplicate cache writes.
        complete = data["complete"] and not errors
        self.snapshots.observe_result(
            snapshot_id, complete=complete, sample_size=data["sampleSize"], errors=errors
        )
        return ResearchResult(
            data=data,
            provenance=Provenance(
                snapshot_id=snapshot_id,
                observed_at=isoformat(self.clock()),
                source="derived:perk-rates",
                source_object_ids=tuple(str(item.get("game_id")) for item in games),
                payload_hashes=tuple(sorted(hashes)),
                sample_size=data["sampleSize"],
                complete=complete,
                errors=tuple(errors),
                evidence_manifest=tuple(sorted(manifests, key=_manifest_key)),
            ),
        )

    def _trusted_profile_points(
        self, snapshot_id: str, player_id: int, payloads: Sequence[Mapping[str, Any]],
        locators: Sequence[Mapping[str, Any]],
    ) -> tuple[list[float | None], list[RawPayloadRecord], list[PartialError]]:
        # Only broker-owned records attached to this still-COLLECTING snapshot.
        records = [r for r in self.snapshots.require_collecting(snapshot_id).records
                   if r.source == "profile-games-page" and r.object_id.split(":")[0] == str(player_id)]
        rows: list[Mapping[str, Any]] = []
        errors: list[PartialError] = []
        for record in records:
            try:
                payload = self.cache.load(record.payload_hash)  # verifies bytes against SHA-256
                if not isinstance(payload, Mapping) or not isinstance(payload.get("rows"), list):
                    raise ContractError("invalid profile page")
                rows.extend(row for row in payload["rows"] if isinstance(row, Mapping))
            except ResearchError:
                errors.append(PartialError("get_player_perk_rates", "POINTS_SOURCE_INVALID", "profile points payload missing, corrupt or invalid", f"profile-games-page:{record.object_id}"))
        values: list[float | None] = []
        for game, locator in zip(payloads, locators):
            value = None
            subject = f"player:{player_id}:{locator['kind']}:{locator.get('competition_id', '-')}:game:{locator['game_id']}"
            try:
                eligible = game.get("result") is not None and analytics._find_player(game, player_id) is not None
            except (TypeError, AttributeError, ValueError):
                eligible = False  # analytics reports malformed games separately
            if eligible:
                candidates = [r for r in rows if type(r.get("id")) is int and r["id"] == locator["game_id"]
                              and r.get("type") == locator["kind"]
                              and (locator["kind"] == "match" or
                                   (type(r.get("competition_id")) is int and r["competition_id"] == locator["competition_id"]))]
                numeric = [analytics._number(row.get("points")) if row.get("result") is not None else None
                           for row in candidates]
                if numeric and all(v is not None for v in numeric) and len(set(numeric)) == 1:
                    value = numeric[0]
                else:
                    code = "POINTS_MISSING" if not candidates else "POINTS_INVALID_OR_CONFLICTING"
                    errors.append(PartialError("get_player_perk_rates", code, "finite unambiguous profile points required for exact player and game identity", subject))
            values.append(value)
        return values, records, errors

    def compare_players(self, snapshot_id: str, player_ids: Sequence[int], *, max_pages: int = 10) -> ResearchResult:
        ids = _player_ids(player_ids)
        results = self._parallel_statistics(snapshot_id, ids, max_pages)
        data = analytics.compare({player_id: result.data for player_id, result in results.items()})
        return _combine(results.values(), snapshot_id, "derived:compare-players", data, self.clock, self.snapshots)

    def build_series_projection(
        self,
        snapshot_id: str,
        player_ids: Sequence[int],
        *,
        max_pages: int = 10,
        minimum_sample: int = 5,
    ) -> ResearchResult:
        ids = _player_ids(player_ids)
        if not 1 <= minimum_sample <= 1000:
            raise ContractError("minimum_sample must be in 1..1000")
        results = self._parallel_statistics(snapshot_id, ids, max_pages)
        data = analytics.build_projection(
            {player_id: result.data for player_id, result in results.items()}, minimum_sample=minimum_sample
        )
        return _combine(results.values(), snapshot_id, "derived:series-projection", data, self.clock, self.snapshots)

    def _parallel_statistics(self, snapshot_id: str, ids: Sequence[int], max_pages: int) -> dict[int, ResearchResult]:
        results: dict[int, ResearchResult] = {}
        with ThreadPoolExecutor(max_workers=self.max_parallel_reads) as executor:
            futures = {
                executor.submit(self.get_player_statistics, snapshot_id, player_id, max_pages=max_pages): player_id
                for player_id in ids
            }
            for future in as_completed(futures):
                player_id = futures[future]
                try:
                    results[player_id] = future.result()
                except ResearchError as error:
                    results[player_id] = self._result(
                        analytics.player_statistics([], complete=False),
                        snapshot_id,
                        "player-statistics",
                        [],
                        0,
                        False,
                        [_partial("compare_players", error, f"player:{player_id}")],
                    )
        return results

    def _single_object(self, snapshot_id: str, source: str, object_id: str, fetch: Callable[[], Mapping[str, Any]]) -> ResearchResult:
        self.snapshots.require_collecting(snapshot_id)
        try:
            payload = fetch()
            record = self._record(snapshot_id, source, object_id, payload, source_version=payload.get("version"))
            return self._result(payload, snapshot_id, source, [record], 1, True, [])
        except ResearchError as error:
            return self._result(None, snapshot_id, source, [], 0, False, [_partial(source, error, object_id)])

    def _single_list(self, snapshot_id: str, source: str, object_id: str, fetch: Callable[[], list[Mapping[str, Any]]]) -> ResearchResult:
        self.snapshots.require_collecting(snapshot_id)
        try:
            payload = fetch()
            record = self._record(snapshot_id, source, object_id, payload)
            return self._result(payload, snapshot_id, source, [record], len(payload), True, [])
        except ResearchError as error:
            return self._result([], snapshot_id, source, [], 0, False, [_partial(source, error, object_id)])

    def _record(
        self,
        snapshot_id: str,
        source: str,
        object_id: str,
        payload: Any,
        *,
        source_version: str | int | None = None,
    ) -> RawPayloadRecord:
        self.snapshots.require_collecting(snapshot_id)
        record = self.cache.store(
            source=source, object_id=object_id, payload=payload, source_version=source_version
        )
        self.snapshots.attach(snapshot_id, record)
        return record

    def _derived(self, source: ResearchResult, data: Any, name: str) -> ResearchResult:
        complete = source.provenance.complete and bool(data.get("complete", True))
        self.snapshots.observe_result(
            str(source.provenance.snapshot_id), complete=complete,
            sample_size=data.get("sampleSize", source.provenance.sample_size),
            errors=source.provenance.errors,
        )
        return ResearchResult(
            data=data,
            provenance=Provenance(
                snapshot_id=source.provenance.snapshot_id,
                observed_at=isoformat(self.clock()),
                source=f"derived:{name}",
                source_object_ids=source.provenance.source_object_ids,
                payload_hashes=source.provenance.payload_hashes,
                sample_size=data.get("sampleSize", source.provenance.sample_size),
                complete=complete,
                errors=source.provenance.errors,
                evidence_manifest=source.provenance.evidence_manifest,
            ),
        )

    def _result(
        self,
        data: Any,
        snapshot_id: str,
        source: str,
        records: Sequence[RawPayloadRecord],
        sample_size: int,
        complete: bool,
        errors: Sequence[PartialError],
    ) -> ResearchResult:
        self.snapshots.observe_result(
            snapshot_id, complete=complete, sample_size=sample_size, errors=errors
        )
        return ResearchResult(
            data=data,
            provenance=Provenance(
                snapshot_id=snapshot_id,
                observed_at=isoformat(self.clock()),
                source=source,
                source_object_ids=tuple(record.object_id for record in records),
                payload_hashes=tuple(record.payload_hash for record in records),
                sample_size=sample_size,
                complete=complete,
                errors=tuple(errors),
                evidence_manifest=tuple({
                    "source": record.source,
                    "objectId": record.object_id,
                    "sourceVersion": record.source_version,
                    "payloadHash": record.payload_hash,
                    "firstSeenAt": record.first_seen_at,
                    "fetchedAt": record.fetched_at,
                    "parserVersion": record.parser_version,
                    "correctionIndex": record.correction_index,
                    "isCorrection": record.correction_index > 1,
                    "completeness": "COMPLETE",
                } for record in records),
            ),
        )


def _snapshot_dict(snapshot: Any) -> dict[str, Any]:
    return {
        "snapshotId": snapshot.snapshot_id,
        "collectionId": snapshot.collection_id or (
            snapshot.snapshot_id if isinstance(snapshot.snapshot_id, str) else None
        ),
        "runId": snapshot.run_id,
        "state": snapshot.state,
        "createdAt": snapshot.created_at,
        "asOf": snapshot.as_of,
        "payloadHashes": [record.payload_hash for record in snapshot.records],
        "completeness": snapshot.completeness,
        "errorCount": snapshot.error_count,
        "errorDetailsComplete": snapshot.error_count == len(snapshot.errors),
        "errors": [dict(operation=e.operation, code=e.code, message=e.message, subject=e.subject)
                   for e in snapshot.errors],
        "evidenceManifest": [{
            "source": record.source,
            "objectId": record.object_id,
            "sourceVersion": record.source_version,
            "payloadHash": record.payload_hash,
            "firstSeenAt": record.first_seen_at,
            "fetchedAt": record.fetched_at,
            "parserVersion": record.parser_version,
            "correctionIndex": record.correction_index,
            "isCorrection": record.correction_index > 1,
            "completeness": "COMPLETE",
        } for record in snapshot.records],
    }


def _partial(operation: str, error: Exception, subject: str | None = None) -> PartialError:
    return PartialError(operation, type(error).__name__, str(error)[:256], subject)


def _positive(value: int | None, name: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ContractError(f"{name} must be a positive integer")
    return value


def _required_int(value: Mapping[str, Any], key: str) -> int:
    return _positive(value.get(key), key)


def _optional_int(value: Mapping[str, Any], key: str) -> int | None:
    item = value.get(key)
    return None if item is None else _positive(item, key)


def _player_ids(values: Sequence[int]) -> list[int]:
    if not 1 <= len(values) <= 20:
        raise ContractError("player_ids must contain 1..20 players")
    result = []
    for value in values:
        item = _positive(value, "player_id")
        if item not in result:
            result.append(item)
    return result


def _combine(
    results: Iterable[ResearchResult],
    snapshot_id: str,
    source: str,
    data: Any,
    clock: Callable[[], datetime],
    snapshots: SnapshotCoordinator,
) -> ResearchResult:
    materialized = list(results)
    errors = tuple(sorted(
        (error for result in materialized for error in result.provenance.errors),
        key=lambda error: (error.operation, error.code, error.subject or "", error.message),
    ))
    hashes = tuple(sorted({digest for result in materialized for digest in result.provenance.payload_hashes}))
    ids = tuple(sorted(item for result in materialized for item in result.provenance.source_object_ids))
    complete = all(result.provenance.complete for result in materialized) and not errors and bool(
        data.get("complete", True) if isinstance(data, Mapping) else True
    )
    sample_size = sum(result.provenance.sample_size for result in materialized)
    snapshots.observe_result(
        snapshot_id, complete=complete, sample_size=sample_size, errors=errors
    )
    return ResearchResult(
        data=data,
        provenance=Provenance(
            snapshot_id=snapshot_id,
            observed_at=isoformat(clock()),
            source=source,
            source_object_ids=ids,
            payload_hashes=hashes,
            sample_size=sample_size,
            complete=complete,
            errors=errors,
            evidence_manifest=tuple(sorted(
                (item for result in materialized for item in result.provenance.evidence_manifest),
                key=_manifest_key,
            )),
        ),
    )


def _manifest_key(item: Mapping[str, Any]) -> tuple[str, str, str, str]:
    return (
        str(item.get("source", "")), str(item.get("objectId", "")),
        str(item.get("sourceVersion", "")), str(item.get("payloadHash", "")),
    )
