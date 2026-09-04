"""Memory service foundation.

The transport adapter is intentionally deferred until the VM preflight confirms the
Codex MCP transport. `MemoryService` is transport-neutral and safe to expose through
the official MCP SDK later.
"""

from .service import MemoryService

__all__ = ["MemoryService"]
