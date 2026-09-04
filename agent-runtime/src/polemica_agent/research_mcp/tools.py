"""Framework-neutral typed handlers for the Research MCP server."""

from __future__ import annotations

from typing import Any, Mapping, Sequence

from .service import ResearchService


tool_names = (
    "begin_research_snapshot",
    "seal_research_snapshot",
    "get_player_games",
    "get_game",
    "list_competitions",
    "get_competition",
    "get_competition_members",
    "get_competition_games",
    "get_competition_metrics",
    "get_player_statistics",
    "get_player_recent_form",
    "get_player_role_distribution",
    "get_player_perk_rates",
    "compare_players",
    "build_series_projection",
)


class ResearchTools:
    """Methods are suitable for direct registration with an MCP SDK.

    There is intentionally no arbitrary URL, path, HTTP method, SQL, filesystem,
    or Polemica write handler.
    """

    def __init__(self, service: ResearchService) -> None:
        self.service = service

    def begin_research_snapshot(self, run_id: str, snapshot_id: str | None = None) -> dict[str, Any]:
        """Start a bounded collection snapshot for one run."""
        return self.service.begin_snapshot(run_id, snapshot_id)

    def seal_research_snapshot(self, snapshot_id: str) -> dict[str, Any]:
        """Seal a snapshot; later fetches against it fail closed."""
        return self.service.seal_snapshot(snapshot_id)

    def get_player_games(
        self, snapshot_id: str, player_id: int, page_size: int = 100, max_pages: int = 10
    ) -> dict[str, Any]:
        """Collect a player's deduplicated profile game rows."""
        return self.service.get_player_games(
            snapshot_id, player_id, page_size=page_size, max_pages=max_pages
        ).to_dict()

    def get_game(
        self,
        snapshot_id: str,
        kind: str,
        game_id: int,
        competition_id: int | None = None,
        version: int | None = None,
    ) -> dict[str, Any]:
        """Collect one match or competition-game payload by typed locator."""
        return self.service.get_game(
            snapshot_id,
            kind=kind,
            game_id=game_id,
            competition_id=competition_id,
            version=version,
        ).to_dict()

    def list_competitions(self, snapshot_id: str) -> dict[str, Any]:
        """Collect the competition list."""
        return self.service.list_competitions(snapshot_id).to_dict()

    def get_competition(self, snapshot_id: str, competition_id: int) -> dict[str, Any]:
        """Collect one competition."""
        return self.service.get_competition(snapshot_id, competition_id).to_dict()

    def get_competition_members(self, snapshot_id: str, competition_id: int) -> dict[str, Any]:
        """Collect competition members."""
        return self.service.get_competition_members(snapshot_id, competition_id).to_dict()

    def get_competition_games(self, snapshot_id: str, competition_id: int) -> dict[str, Any]:
        """Collect competition game references."""
        return self.service.get_competition_games(snapshot_id, competition_id).to_dict()

    def get_competition_metrics(
        self, snapshot_id: str, competition_id: int, scoring_type: int | None = None
    ) -> dict[str, Any]:
        """Collect competition metrics for an optional scoring type."""
        return self.service.get_competition_metrics(snapshot_id, competition_id, scoring_type).to_dict()

    def get_player_statistics(
        self, snapshot_id: str, player_id: int, max_pages: int = 10
    ) -> dict[str, Any]:
        """Calculate deterministic point/result statistics."""
        return self.service.get_player_statistics(snapshot_id, player_id, max_pages=max_pages).to_dict()

    def get_player_recent_form(
        self, snapshot_id: str, player_id: int, window: int = 20, max_pages: int = 10
    ) -> dict[str, Any]:
        """Calculate complete bounded recent form without requiring full career history."""
        return self.service.get_player_recent_form(
            snapshot_id, player_id, window=window, max_pages=max_pages
        ).to_dict()

    def get_player_role_distribution(
        self, snapshot_id: str, player_id: int, max_pages: int = 10
    ) -> dict[str, Any]:
        """Calculate role counts and rates."""
        return self.service.get_player_role_distribution(snapshot_id, player_id, max_pages=max_pages).to_dict()

    def get_player_perk_rates(
        self,
        snapshot_id: str,
        player_id: int,
        games: Sequence[Mapping[str, Any]],
    ) -> dict[str, Any]:
        """Calculate perk rates from explicit typed game locators."""
        return self.service.get_player_perk_rates(snapshot_id, player_id, games).to_dict()

    def compare_players(
        self, snapshot_id: str, player_ids: Sequence[int], max_pages: int = 10
    ) -> dict[str, Any]:
        """Compare bounded player histories without hiding partial inputs."""
        return self.service.compare_players(snapshot_id, player_ids, max_pages=max_pages).to_dict()

    def build_series_projection(
        self,
        snapshot_id: str,
        player_ids: Sequence[int],
        max_pages: int = 10,
        minimum_sample: int = 5,
    ) -> dict[str, Any]:
        """Build a transparent baseline projection from historical means."""
        return self.service.build_series_projection(
            snapshot_id,
            player_ids,
            max_pages=max_pages,
            minimum_sample=minimum_sample,
        ).to_dict()


def register_with_mcp(server: Any, tools: ResearchTools) -> None:
    """Register the fixed method set on an MCP server supporting `tool()(fn)`.

    The MCP SDK import and server lifecycle belong to the shared runtime layer;
    this adapter keeps the research package independently testable.
    """
    for name in tool_names:
        server.tool(name=name)(getattr(tools, name))
