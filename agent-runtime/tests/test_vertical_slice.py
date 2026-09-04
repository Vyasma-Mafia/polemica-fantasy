from __future__ import annotations

from pathlib import Path

from polemica_agent.common.operations import IntentState
from polemica_agent.common.storage import AuditStore
from polemica_agent.runner.vertical_slice import run_team_vertical_slice


class FakeTeamGateway:
    def __init__(self) -> None:
        self.team = None
        self.write_count = 0

    def get_team(self, series_id: int, league_code: str):
        return self.team

    def write_team(self, series_id: int, league_code: str, user_card_ids: list[int]):
        self.write_count += 1
        self.team = {
            "seriesId": series_id,
            "leagueCode": league_code,
            "userCardIds": list(user_card_ids),
        }
        return {"accepted": True}


def test_mock_team_vertical_slice_writes_reads_back_and_audits(tmp_path: Path) -> None:
    with AuditStore((tmp_path / "agent.sqlite3").resolve()) as store:
        run_id = store.start_run(
            run_id="run",
            model="mock",
            prompt_hash="p",
            tools_hash="t",
            config_hash="c",
        )
        gateway = FakeTeamGateway()
        result = run_team_vertical_slice(
            store=store,
            gateway=gateway,
            run_id=run_id,
            operation_id="team-1",
            series_id=42,
            league_code="MAIN",
            user_card_ids=[3, 5, 8],
        )
        assert result.state is IntentState.SUCCEEDED
        assert result.verification == {"matchesExpectedState": True, "readBackCompleted": True}
        assert gateway.write_count == 1
        assert store.connection.execute("SELECT COUNT(*) FROM snapshots").fetchone()[0] == 1
        assert store.connection.execute("SELECT COUNT(*) FROM decisions").fetchone()[0] == 1
        assert store.get_intent("team-1")["state"] == "SUCCEEDED"
