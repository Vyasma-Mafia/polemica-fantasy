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
        if minimum_rows is not None and not 1 <= minimum_rows <= 500:
            raise ContractError("minimum_rows must be in 1..500")
        self.snapshots.require_collecting(snapshot_id)
        rows_by_id: dict[int, Mapping[str, Any]] = {}
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
                    if isinstance(row, Mapping) and isinstance(row.get("id"), int):
                        rows_by_id.setdefault(row["id"], row)
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
        return self._result(
            data={"playerId": player_id, "rows": rows, "reportedTotalCount": total_count},
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
        if kind not in {"match", "competition"}:
            raise ContractError("kind must be 'match' or 'competition'")
        self.snapshots.require_collecting(snapshot_id)
        try:
            if kind == "match":
                payload = self.client.get_match(game_id, version)
                source, object_id = "match", str(game_id)
            else:
                _positive(competition_id, "competition_id")
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
    ) -> ResearchResult:
        _positive(player_id, "player_id")
        if not 1 <= len(games) <= 100:
            raise ContractError("games must contain 1..100 typed game locators")
        payloads: list[Mapping[str, Any]] = []
        hashes: set[str] = set()
        manifests: list[Mapping[str, Any]] = []
        errors: list[PartialError] = []
        if any("base_points" in locator for locator in games):
            raise ContractError("base_points is not accepted without trusted source provenance")

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
                        payloads.append(result.data)
                        hashes.update(result.provenance.payload_hashes)
                        manifests.extend(result.provenance.evidence_manifest)
                    errors.extend(result.provenance.errors)
                except ResearchError as error:
                    errors.append(_partial("get_player_perk_rates", error, f"game:{locator.get('game_id')}"))
        data = analytics.perk_rates(
            payloads,
            player_id,
            base_points_by_game_id=None,
            complete=not errors and len(payloads) == len(games),
        )
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
