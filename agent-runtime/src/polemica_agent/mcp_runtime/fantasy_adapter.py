from __future__ import annotations

import inspect
from typing import Any, Mapping


def _annotation(schema: Mapping[str, Any]) -> Any:
    kind = schema.get("type")
    if kind == "integer":
        return int
    if kind == "string":
        return str
    if kind == "boolean":
        return bool
    if kind == "array":
        return list[_annotation(schema.get("items", {}))]
    return Any


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
                return registry.call(_name, kwargs)

            call.__name__ = name
            call.__doc__ = description["description"]
            ordered = required_order + [item for item in properties if item not in required]
            call.__signature__ = inspect.Signature(  # type: ignore[attr-defined]
                [inspect.Parameter(
                    item, inspect.Parameter.KEYWORD_ONLY,
                    default=inspect.Parameter.empty if item in required else None,
                    annotation=_annotation(properties[item]),
                ) for item in ordered],
                return_annotation=dict[str, Any],
            )
            setattr(self, name, call)
