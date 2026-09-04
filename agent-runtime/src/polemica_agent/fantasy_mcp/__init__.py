"""Strict Fantasy user-API adapter and transport-neutral tool registry."""

from .client import FantasyApiError, FantasyHttpClient, HttpResponse, HttpTransport, UrlLibTransport
from .registry import FantasyToolRegistry, ToolSpec, build_tool_registry
from .service import FantasyService, OperationEnvelope, ReadEnvelope

__all__ = [
    "FantasyApiError",
    "FantasyHttpClient",
    "FantasyService",
    "FantasyToolRegistry",
    "HttpResponse",
    "HttpTransport",
    "OperationEnvelope",
    "ReadEnvelope",
    "ToolSpec",
    "UrlLibTransport",
    "build_tool_registry",
]
