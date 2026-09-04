"""Research component exceptions."""


class ResearchError(RuntimeError):
    """Base error safe to convert to an MCP tool failure."""


class ContractError(ResearchError):
    """The caller supplied an invalid identifier, bound, or state transition."""


class UpstreamError(ResearchError):
    """A Polemica read failed without exposing credentials or response bodies."""

    def __init__(self, operation: str, status: int | None = None) -> None:
        detail = f" (HTTP {status})" if status is not None else ""
        super().__init__(f"Polemica read '{operation}' failed{detail}")
        self.operation = operation
        self.status = status


class SnapshotSealedError(ResearchError):
    """An attempted mutation targeted an already sealed snapshot."""
