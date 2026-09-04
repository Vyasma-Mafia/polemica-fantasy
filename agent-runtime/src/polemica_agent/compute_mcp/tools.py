from __future__ import annotations

from collections.abc import Callable
from typing import Any, Sequence

from mcp.server.mcpserver.exceptions import ToolError

from polemica_agent.common.storage import FailClosedError

from .audit import ComputationConflictError
from .dataset import DatasetError
from .service import ComputeService, ComputeServiceError


class ComputeTools:
    """Fixed numerical tools; none accepts code, paths, URLs, SQL, or inline datasets."""

    def __init__(self, service: ComputeService) -> None:
        self.service = service

    def compute_list_operations(self) -> dict[str, Any]:
        return self.service.list_operations()

    def compute_get_result(self, run_id: str, computation_id: str) -> dict[str, Any]:
        return _safe_call(lambda: self.service.get_result(run_id, computation_id))

    def compute_describe_player_points(
        self,
        run_id: str,
        snapshot_id: int,
        computation_id: str,
        player_ids: Sequence[int],
        quantiles: Sequence[float] = (0.25, 0.5, 0.75),
    ) -> dict[str, Any]:
        return _safe_call(lambda: self.service.run(
            run_id=run_id,
            snapshot_id=snapshot_id,
            computation_id=computation_id,
            operation="describe_player_points",
            player_ids=player_ids,
            parameters={"quantiles": list(quantiles)},
        ))

    def compute_correlate_player_metrics(
        self,
        run_id: str,
        snapshot_id: int,
        computation_id: str,
        player_ids: Sequence[int],
        features: Sequence[str],
    ) -> dict[str, Any]:
        return _safe_call(lambda: self.service.run(
            run_id=run_id,
            snapshot_id=snapshot_id,
            computation_id=computation_id,
            operation="correlate_player_metrics",
            player_ids=player_ids,
            parameters={"features": list(features)},
        ))

    def compute_simulate_player_totals(
        self,
        run_id: str,
        snapshot_id: int,
        computation_id: str,
        player_ids: Sequence[int],
        games_count: int,
        trials: int,
        seed: int,
    ) -> dict[str, Any]:
        return _safe_call(lambda: self.service.run(
            run_id=run_id,
            snapshot_id=snapshot_id,
            computation_id=computation_id,
            operation="simulate_player_totals",
            player_ids=player_ids,
            parameters={"gamesCount": games_count, "trials": trials, "seed": seed},
        ))


def _safe_call(call: Callable[[], dict[str, Any]]) -> dict[str, Any]:
    try:
        return call()
    except ComputationConflictError as exc:
        raise ToolError("Compute rejected request (COMPUTE_ID_CONFLICT)") from exc
    except (ComputeServiceError, DatasetError) as exc:
        raise ToolError(f"Compute rejected request ({exc.code})") from exc
    except KeyError as exc:
        raise ToolError("Compute rejected request (COMPUTE_NOT_FOUND)") from exc
    except (FailClosedError, ValueError) as exc:
        raise ToolError("Compute rejected request (COMPUTE_EVIDENCE_OR_ARGUMENT_INVALID)") from exc
    except Exception as exc:
        raise ToolError("Compute failed (COMPUTE_INTERNAL_ERROR)") from exc
