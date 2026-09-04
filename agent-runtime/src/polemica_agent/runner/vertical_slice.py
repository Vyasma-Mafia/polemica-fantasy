from __future__ import annotations

import datetime as dt
from typing import Any, Protocol

from polemica_agent.common.operations import (
    IntentState,
    OperationCoordinator,
    OperationResult,
    ReadBackResolution,
)
from polemica_agent.common.storage import AuditStore


class TeamGateway(Protocol):
    """Minimal mockable contract; a real Fantasy MCP adapter implements it later."""

    def get_team(self, series_id: int, league_code: str) -> dict[str, Any] | None: ...

    def write_team(self, series_id: int, league_code: str, user_card_ids: list[int]) -> dict[str, Any]: ...


def run_team_vertical_slice(
    *,
    store: AuditStore,
    gateway: TeamGateway,
    run_id: str,
    operation_id: str,
    series_id: int,
    league_code: str,
    user_card_ids: list[int],
) -> OperationResult:
    """Exercise one team write and mandatory exact read-back without real network."""
    now = dt.datetime.now(dt.timezone.utc)
    before = gateway.get_team(series_id, league_code)
    snapshot_id = store.create_snapshot(
        run_id=run_id,
        kind="FANTASY_TEAM_PRE_WRITE",
        as_of=now,
        generated_at=now,
        source="mock-fantasy-gateway",
        payload={"team": before},
        sample_size=0 if before is None else 1,
    )
    request = {
        "seriesId": series_id,
        "leagueCode": league_code,
        "userCardIds": user_card_ids,
    }
    decision_id = store.record_decision(
        run_id=run_id,
        decision_type="TEAM_SELECTION",
        subject_type="SERIES_LEAGUE",
        subject_id=f"{series_id}:{league_code}",
        snapshot_ids=[snapshot_id],
        alternatives=[{"userCardIds": user_card_ids}],
        choice=request,
        rationale="Mock vertical slice decision",
    )

    def read_back() -> ReadBackResolution:
        team = gateway.get_team(series_id, league_code)
        observed = [] if team is None else team.get("userCardIds", [])
        exact = observed == user_card_ids
        return ReadBackResolution(
            IntentState.SUCCEEDED if exact else IntentState.UNKNOWN,
            {"team": team},
            {"readBackCompleted": True, "matchesExpectedState": exact},
        )

    return OperationCoordinator(store).execute(
        operation_id=operation_id,
        run_id=run_id,
        decision_id=decision_id,
        kind="TEAM_WRITE",
        target_id=f"{series_id}:{league_code}",
        request=request,
        is_economic=False,
        send=lambda: gateway.write_team(series_id, league_code, user_card_ids),
        read_back=read_back,
    )
