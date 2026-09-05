from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[2] / "src"))

from polemica_agent.research_mcp.client import HttpClientConfig, HttpPolemicaClient, RequestGate
from polemica_agent.research_mcp.errors import ContractError, UpstreamError


class RecordingClient(HttpPolemicaClient):
    def __init__(self) -> None:
        super().__init__(HttpClientConfig("https://api.example", username="u", password="p"), gate=RequestGate(min_interval_seconds=0))
        self.requests = []
        self.competition_games = [{"id": 7, "version": 2}]

    def _auth_token(self) -> str:
        return "secret"

    def _request(self, method, base, path, query, *, operation, token, body=None):
        self.requests.append((method, base, path, query, operation, token is not None))
        if path.endswith("/metrics"):
            return []
        if path.endswith("/games"):
            return self.competition_games
        return {"id": 7, "version": 2}


class ClientContractTest(unittest.TestCase):
    def test_login_uses_access_token_contract(self) -> None:
        class LoginClient(HttpPolemicaClient):
            def _request(self, method, base, path, query, *, operation, token, body=None):
                self.assertion = (method, path, body, token)
                return {"access_token": "real-contract-token"}

        client = LoginClient(
            HttpClientConfig("https://api.example", username="reader", password="secret"),
            gate=RequestGate(min_interval_seconds=0),
        )
        self.assertEqual("real-contract-token", client._auth_token())
        self.assertEqual(
            ("POST", "/v1/auth/login", {"username": "reader", "password": "secret"}, None),
            client.assertion,
        )

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

    def test_competition_game_resolves_advertised_version_when_omitted(self) -> None:
        client = RecordingClient()
        client.competition_games = [{"id": 8, "version": 99}, {"id": 7, "version": 4}]
        client.get_competition_game(9, 7)
        self.assertEqual(
            [
                ("GET", "https://api.example", "/v1/competitions/9/games", {}, "get_competition_games", True),
                ("GET", "https://api.example", "/v1/competitions/9/games/7", {"version": 4}, "get_competition_game", True),
            ], client.requests,
        )
        # Do not cache or reuse another request's discovered version.
        client.competition_games = [{"id": 7, "version": 5}]
        client.get_competition_game(9, 7)
        self.assertEqual({"version": 5}, client.requests[-1][3])

    def test_explicit_competition_version_does_not_lookup_or_override(self) -> None:
        client = RecordingClient()
        client.get_competition_game(9, 7, 3)
        self.assertEqual(1, len(client.requests))
        self.assertEqual("/v1/competitions/9/games/7", client.requests[0][2])
        self.assertEqual({"version": 3}, client.requests[0][3])

    def test_missing_ambiguous_or_invalid_version_never_fetches_game(self) -> None:
        for metadata in [[], [{"id": 8, "version": 4}], [{"id": 7}],
                         [{"id": 7, "version": None}], [{"id": 7, "version": True}],
                         [{"id": 7, "version": 0}], [{"id": 7, "version": "4"}],
                         [{"id": 7, "version": 4}, {"id": 7, "version": 5}]]:
            with self.subTest(metadata=metadata):
                client = RecordingClient()
                client.competition_games = metadata
                with self.assertRaises(UpstreamError):
                    client.get_competition_game(9, 7)
                self.assertEqual(1, len(client.requests))
                self.assertEqual("/v1/competitions/9/games", client.requests[0][2])

    def test_match_without_version_keeps_existing_semantics(self) -> None:
        client = RecordingClient()
        client.get_match(7)
        self.assertEqual(1, len(client.requests))
        self.assertEqual({}, client.requests[0][3])


if __name__ == "__main__":
    unittest.main()
