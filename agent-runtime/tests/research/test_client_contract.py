from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[2] / "src"))

from polemica_agent.research_mcp.client import HttpClientConfig, HttpPolemicaClient, RequestGate
from polemica_agent.research_mcp.errors import ContractError


class RecordingClient(HttpPolemicaClient):
    def __init__(self) -> None:
        super().__init__(HttpClientConfig("https://api.example", username="u", password="p"), gate=RequestGate(min_interval_seconds=0))
        self.requests = []

    def _auth_token(self) -> str:
        return "secret"

    def _request(self, method, base, path, query, *, operation, token, body=None):
        self.requests.append((method, base, path, query, operation, token is not None))
        if path.endswith("/metrics"):
            return []
        return {"id": 7, "version": 2}


class ClientContractTest(unittest.TestCase):
    def test_typed_methods_construct_only_known_get_paths(self) -> None:
        client = RecordingClient()
        client.get_match(7, 2)
        client.get_competition(9)
        client.get_competition_metrics(9, 1)
        self.assertEqual(
            [
                ("GET", "https://api.example", "/v1/matches/7", {"version": 2}, "get_match", True),
                ("GET", "https://api.example", "/v1/competitions/9", {}, "get_competition", True),
                ("GET", "https://api.example", "/v1/competitions/9/metrics", {"scoringType": 1}, "get_competition_metrics", True),
            ],
            client.requests,
        )

    def test_rejects_non_https_origin_and_unbounded_inputs(self) -> None:
        with self.assertRaises(ContractError):
            HttpPolemicaClient(HttpClientConfig("http://api.example"))
        client = RecordingClient()
        with self.assertRaises(ContractError):
            client.get_player_games(1, 1, 201)
        with self.assertRaises(ContractError):
            RequestGate(max_concurrency=100)


if __name__ == "__main__":
    unittest.main()
