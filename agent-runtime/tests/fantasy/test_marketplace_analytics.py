from pathlib import Path
from typing import Any

import pytest

from polemica_agent.common.storage import AuditStore
from polemica_agent.fantasy_mcp.registry import build_tool_registry
from polemica_agent.fantasy_mcp.service import FantasyService
from polemica_agent.mcp_runtime.fantasy_adapter import FantasyRegistryAdapter
from mcp.server.mcpserver.exceptions import ToolError


class AnalyticsClient:
    def __init__(self) -> None:
        self.calls: list[tuple[str, Any]] = []

    def _get(self, path: str, query: Any) -> Any:
        self.calls.append((path, query))
        if path.endswith("/summary"):
            return {"items": [{"fantasyPlayerId": 123, "rarity": "EPIC", "activeCount": 2, "minActivePrice": 400}]}
        return {
            "fantasyPlayerId": 123, "rarity": "EPIC", "activeCount": 2,
            "activeMinPrice": 400, "activeMaxPrice": 500,
            "recentSales": [{"price": 350, "soldAt": "2026-09-05T00:00:00Z"}],
            "avgSalePrice": 350,
        }


@pytest.fixture
def analytics(tmp_path: Path):
    client = AnalyticsClient()
    store = AuditStore((tmp_path / "state.sqlite3").resolve())
    try:
        yield FantasyService(client, store), client
    finally:
        store.close()


def test_analytics_detail_adapter_preserves_completed_sales(analytics) -> None:
    service, client = analytics
    adapter = FantasyRegistryAdapter(build_tool_registry(service))
    result = adapter.fantasy_get_marketplace_analytics(
        fantasy_player_ids=None, fantasy_player_id=123, rarity="EPIC",
    )
    assert client.calls == [("/api/v1/marketplace/analytics/detail", {"fantasyPlayerId": 123, "rarity": "EPIC"})]
    assert result["data"]["recentSales"] == [{"price": 350, "soldAt": "2026-09-05T00:00:00Z"}]
    assert result["data"]["avgSalePrice"] == 350


def test_analytics_summary_adapter_keeps_active_summary_contract(analytics) -> None:
    service, client = analytics
    adapter = FantasyRegistryAdapter(build_tool_registry(service))
    result = adapter.fantasy_get_marketplace_analytics(
        fantasy_player_ids=[123], fantasy_player_id=None, rarity=None,
    )
    assert client.calls == [("/api/v1/marketplace/analytics/summary", {"fantasyPlayerIds": [123]})]
    assert result["data"] == {"items": [{"fantasyPlayerId": 123, "rarity": "EPIC", "activeCount": 2, "minActivePrice": 400}]}


def test_empty_analytics_adapter_gives_safe_actionable_error(analytics) -> None:
    service, client = analytics
    adapter = FantasyRegistryAdapter(build_tool_registry(service))
    with pytest.raises(ToolError, match="Use exactly one mode"):
        adapter.fantasy_get_marketplace_analytics()
    assert client.calls == []


@pytest.mark.parametrize("arguments", [
    {}, {"fantasy_player_id": 123}, {"rarity": "EPIC"},
    {"fantasy_player_ids": [123], "rarity": "EPIC"},
    {"fantasy_player_ids": [123], "fantasy_player_id": 123},
    {"fantasy_player_ids": [123], "fantasy_player_id": 123, "rarity": "EPIC"},
    {"fantasy_player_ids": []}, {"fantasy_player_ids": [123] * 101},
    {"fantasy_player_ids": [0]},
    {"fantasy_player_id": 0, "rarity": "EPIC"},
    {"fantasy_player_id": 123, "rarity": "invalid"},
])
def test_invalid_analytics_modes_make_no_upstream_request(analytics, arguments) -> None:
    service, client = analytics
    with pytest.raises(ValueError):
        service.get_marketplace_analytics(**arguments)
    assert client.calls == []
