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
        from mcp.server.mcpserver.exceptions import ToolError
        from polemica_agent.memory_mcp.evidence import EmptyEvidenceError
        try:
            return self.service.seal_snapshot(snapshot_id)
        except EmptyEvidenceError:
            raise ToolError(
                "EMPTY_EVIDENCE: no broker observations were collected. Ordinary Fantasy reads "
                "do not populate this collection. Call fantasy_collect_evidence with this run_id "
                "and collection_id, or collect relevant Polemica research, then SEAL. "
                "Never fabricate a payload or treat an empty collection as trusted evidence."
            ) from None

    def get_player_games(
        self, snapshot_id: str, player_id: int, page_size: int = 100, max_pages: int = 10,
        limit: int | None = None,
    ) -> dict[str, Any]:
        """Collect deduplicated profile games in upstream order. Set limit=1..500 for
        a bounded window (e.g. limit=20, page_size=40, max_pages=2). COMPLETE then
        describes that window, not the entire career. Without limit, all history
        is requested and hitting max_pages before exhaustion yields PAGE_BOUND.
        Allow spare rows/pages for duplicates; diagnostics explain raw, invalid,
        duplicate and unique row counts without treating 19 unique rows as 20.
        """
        return self.service.get_player_games(
            snapshot_id, player_id, page_size=page_size, max_pages=max_pages, minimum_rows=limit
        ).to_dict()

    def get_game(
        self,
        snapshot_id: str,
        kind: str,
        game_id: int,
        competition_id: int | None = None,
        version: int | None = None,
    ) -> dict[str, Any]:
        """Collect a game. kind is lowercase 'match' or 'competition'; competition requires competition_id.

        Use the exact version from game metadata. Omitted competition versions are
        resolved from the current competition game list before reading detail.
        """
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
        perk_ids: Sequence[str] | None = None,
    ) -> dict[str, Any]:
        """Calculate selected perk rates. games example: [{"kind":"match","game_id":501}].

        kind is lowercase 'match' or 'competition'; competition locators also require
        competition_id. game_id, competition_id and optional version are positive
        integers. Invalid inputs are rejected before collecting evidence; correct
        the arguments and retry the same collecting snapshot. Genuine partial
        upstream data stays PARTIAL and cannot be promoted by retrying.
        Select historical completed games (result is not null, including result=0),
        not future series games. Pass versions from their listing when available.
        For ninja (included by default), first collect this player's profile games
        in the SAME collecting snapshot. Broker-verified finite profile points are
        required for every eligible game, matched by kind and game ID and, for
        competition games, explicit competition_id. Missing/conflicting points
        remain PARTIAL; caller-provided base_points are never trusted. Duplicate
        locators are counted once; exclusions and ninja sample size are reported.
        """
        return self.service.get_player_perk_rates(
            snapshot_id, player_id, games, perk_ids=perk_ids
        ).to_dict()

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
