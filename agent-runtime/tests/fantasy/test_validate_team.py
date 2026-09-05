from pathlib import Path

import pytest

from polemica_agent.common.storage import AuditStore
from polemica_agent.fantasy_mcp.service import FantasyService


class TeamClient:
    def __init__(self):
        self.cards = [{"id": 1, "fantasyPlayerId": 10, "value": 100, "rarity": "EPIC",
                       "usesRemaining": 1, "canJoinMoreLeagues": False, "activeMarketplaceListing": None}]
        self.teams = []
        self.leagues = [{"code": "BUDGET", "minTeamSize": 1, "maxTeamSize": 3, "valueCap": 175, "maxLegendaryCount": 1}]
        self.series = {"players": [{"fantasyPlayerId": 10}], "teamDeadline": "2999-01-01T00:00:00Z"}
        self.calls = []

    def _get(self, path, query):
        self.calls.append((path, query))
        return {"/api/v1/me/cards": self.cards, "/api/v1/me/fantasy-teams": self.teams,
                "/api/v1/series/9/leagues": self.leagues, "/api/v1/series/9": self.series}[path]


@pytest.fixture
def validator(tmp_path: Path):
    client = TeamClient()
    store = AuditStore((tmp_path / "state.sqlite3").resolve())
    try:
        yield FantasyService(client, store), client
    finally:
        store.close()


def test_new_card_reserved_elsewhere_is_rejected(validator):
    service, client = validator
    result = service.validate_team(9, [1], "BUDGET").data
    assert result["issues"] == [{"code": "CARD_USES_ALREADY_RESERVED", "userCardId": 1}]
    assert result["advisory"] is True and result["atomic"] is False
    assert client.calls[0] == ("/api/v1/me/cards", {"tournamentId": None, "seriesId": 9, "rarity": None, "perkIds": None})


def test_retained_card_uses_existing_reservation(validator):
    service, client = validator
    client.teams = [{"seriesId": 9, "leagueCode": "BUDGET", "slots": [{"userCardId": 1}]}]
    result = service.validate_team(9, [1], "BUDGET").data
    assert result["passesObservedChecks"] is True
    assert result["unchecked"] == []


def test_other_league_card_is_not_retained(validator):
    service, client = validator
    client.teams = [{"seriesId": 9, "leagueCode": "MAIN", "slots": [{"userCardId": 1}]}]
    assert service.validate_team(9, [1], "BUDGET").data["passesObservedChecks"] is False


def test_constraints_are_reported_together(validator):
    service, client = validator
    client.cards[0].update(usesRemaining=0, activeMarketplaceListing={"listingId": 2}, rarity="LEGENDARY")
    client.cards.append({**client.cards[0], "id": 2})
    client.series["players"] = []
    client.series["teamDeadline"] = "2000-01-01T00:00:00Z"
    result = service.validate_team(9, [1, 2], "BUDGET").data
    codes = {issue["code"] for issue in result["issues"]}
    assert {"NO_REMAINING_USES", "CARD_LISTED_FOR_SALE", "DUPLICATE_PLAYER", "PLAYER_NOT_IN_SERIES",
            "VALUE_CAP_EXCEEDED", "LEGENDARY_LIMIT_EXCEEDED", "DEADLINE_PASSED"} <= codes


def test_missing_fields_are_explicitly_unchecked(validator):
    service, client = validator
    del client.cards[0]["canJoinMoreLeagues"]
    del client.series["teamDeadline"]
    result = service.validate_team(9, [1], "BUDGET").data
    assert result["unchecked"] == ["globalReservations:1", "teamDeadline"]


def test_duplicate_unknown_and_empty_cards(validator):
    service, _ = validator
    result = service.validate_team(9, [99, 99], "BUDGET").data
    assert {issue["code"] for issue in result["issues"]} == {"DUPLICATE_CARD", "CARD_NOT_OWNED_OR_NOT_IN_SERIES"}
    assert service.validate_team(9, [], "BUDGET").data["issues"][0]["code"] == "TEAM_SIZE"
