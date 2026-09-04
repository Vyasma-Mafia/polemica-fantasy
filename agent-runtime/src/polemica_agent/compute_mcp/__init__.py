"""Fixed, deterministic numerical engine for the Compute MCP."""

from .engine import ENGINE_VERSION, ComputeError, execute

__all__ = ["ENGINE_VERSION", "ComputeError", "execute"]
