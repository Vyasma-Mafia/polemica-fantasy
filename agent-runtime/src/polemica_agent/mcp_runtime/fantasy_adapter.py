from __future__ import annotations

import inspect
from typing import Any, Mapping

from mcp.server.mcpserver.exceptions import ToolError

from polemica_agent.common.operations import DeterministicUpstreamError
from polemica_agent.fantasy_mcp.client import FantasyApiError


def _annotation(schema: Mapping[str, Any], *, nullable: bool = False) -> Any:
    kind = schema.get("type")
    if kind == "integer":
        result = int
    elif kind == "string":
        result = str
    elif kind == "boolean":
        result = bool
    elif kind == "array":
        result = list[_annotation(schema.get("items", {}))]
    else:
        result = Any
    return result | None if nullable else result


class FantasyRegistryAdapter:
    """Turn the domain registry into fixed, inspectable SDK callables."""

    def __init__(self, registry: Any) -> None:
        descriptions = {item["name"]: item for item in registry.list_tools()}
        if set(descriptions) != set(registry.names()):
            raise ValueError("Fantasy registry descriptions do not match names")
        for name, description in descriptions.items():
            schema = description["inputSchema"]
            required_order = list(schema.get("required", ()))
            required = set(required_order)
            properties = schema.get("properties", {})

            def call(_name=name, **kwargs: Any) -> dict[str, Any]:
                try:
                    # The SDK materializes optional signature defaults as None. The domain JSON
                    # schema models omission, not nullable values, so preserve that distinction.
                    arguments = {key: value for key, value in kwargs.items() if value is not None}
                    return registry.call(_name, arguments)
                except DeterministicUpstreamError as exc:
                    raise ToolError(f"Fantasy API rejected request ({exc.code})") from exc
                except FantasyApiError as exc:
                    uncertain = str(exc.uncertain).lower()
                    raise ToolError(
                        f"Fantasy API unavailable ({exc.code}; uncertain={uncertain})"
                    ) from exc
                except ValueError as exc:
                    if _name == "fantasy_get_marketplace_analytics":
                        # Constant guidance only: never surface arbitrary upstream exception text.
                        raise ToolError(
                            'Invalid analytics arguments. Use exactly one mode: '
                            '{"fantasy_player_ids":[123]} for active asks, or '
                            '{"fantasy_player_id":123,"rarity":"EPIC"} for recent completed sales. '
                            'IDs must be positive; rarity is COMMON, RARE, EPIC, or LEGENDARY. '
                            'Do not mix modes or omit both.'
                        ) from exc
                    raise

            call.__name__ = name
            call.__doc__ = description["description"]
            ordered = required_order + [item for item in properties if item not in required]
            call.__signature__ = inspect.Signature(  # type: ignore[attr-defined]
                [inspect.Parameter(
                    item, inspect.Parameter.KEYWORD_ONLY,
                    default=inspect.Parameter.empty if item in required else None,
                    annotation=_annotation(properties[item], nullable=item not in required),
                ) for item in ordered],
                return_annotation=dict[str, Any],
            )
            setattr(self, name, call)
