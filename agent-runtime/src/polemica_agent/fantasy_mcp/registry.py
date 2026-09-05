from __future__ import annotations

import dataclasses
from collections.abc import Callable, Mapping
from typing import Any

from polemica_agent.common.canonical import canonical_json

from .service import FantasyService


ToolHandler = Callable[..., Any]


@dataclasses.dataclass(frozen=True)
class ToolSpec:
    """Transport-neutral description of one closed Fantasy MCP tool."""

    name: str
    description: str
    input_schema: Mapping[str, Any]
    read_only: bool
    handler: ToolHandler = dataclasses.field(repr=False, compare=False)

    def public_description(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "inputSchema": dict(self.input_schema),
            "annotations": {"readOnlyHint": self.read_only},
        }


class FantasyToolRegistry:
    """Fixed allowlist; intentionally exposes no URL, method, or generic request tool."""

    def __init__(self, tools: tuple[ToolSpec, ...]) -> None:
        indexed = {tool.name: tool for tool in tools}
        if len(indexed) != len(tools):
            raise ValueError("duplicate Fantasy tool name")
        self._tools = indexed

    def list_tools(self) -> tuple[dict[str, Any], ...]:
        return tuple(tool.public_description() for tool in self._tools.values())

    def names(self) -> tuple[str, ...]:
        return tuple(self._tools)

    def call(self, name: str, arguments: Mapping[str, Any] | None = None) -> dict[str, Any]:
        try:
            tool = self._tools[name]
        except KeyError as exc:
            raise KeyError(f"Unknown Fantasy tool: {name}") from exc
        if arguments is None:
            arguments = {}
        if not isinstance(arguments, Mapping):
            raise TypeError("Fantasy tool arguments must be an object")
        _validate_schema(arguments, tool.input_schema)
        result = tool.handler(**dict(arguments))
        if not hasattr(result, "as_dict"):
            raise TypeError(f"Fantasy tool {name} returned an invalid envelope")
        return result.as_dict()


def build_tool_registry(service: FantasyService) -> FantasyToolRegistry:
    integer = {"type": "integer", "minimum": 1}
    nonnegative = {"type": "integer", "minimum": 0}
    string = {"type": "string", "minLength": 1, "maxLength": 128}
    strings = {"type": "array", "items": string, "maxItems": 100}
    ids = {"type": "array", "items": integer, "minItems": 1, "maxItems": 100, "uniqueItems": True}
    card_ids = {"type": "array", "items": integer, "minItems": 1, "maxItems": 3, "uniqueItems": True}
    write_common = {"run_id": string, "decision_id": integer, "operation_id": string}

    def schema(properties: Mapping[str, Any] | None = None, required: tuple[str, ...] = ()) -> dict[str, Any]:
        return {
            "type": "object",
            "properties": dict(properties or {}),
            "required": list(required),
            "additionalProperties": False,
        }

    def spec(
        name: str,
        description: str,
        handler: ToolHandler,
        properties: Mapping[str, Any] | None = None,
        required: tuple[str, ...] = (),
        *,
        read_only: bool,
        requires_write_context: bool | None = None,
    ) -> ToolSpec:
        if requires_write_context is None:
            requires_write_context = not read_only
        if requires_write_context:
            required = tuple(dict.fromkeys(("run_id", "decision_id", "operation_id", *required)))
        return ToolSpec(name, description, schema(properties, required), read_only, handler)

    tools = (
        spec("fantasy_get_my_profile", "Read the authenticated Fantasy profile.", service.get_my_profile, read_only=True),
        spec(
            "fantasy_get_my_cards",
            "Read cards owned by the authenticated player.",
            service.get_my_cards,
            {"tournament_id": integer, "series_id": integer, "rarity": string, "perk_ids": strings},
            read_only=True,
        ),
        spec("fantasy_get_my_teams", "Read teams owned by the authenticated player.", service.get_my_teams, read_only=True),
        spec("fantasy_list_open_series", "List series currently open for team submission.", service.list_open_series, read_only=True),
        spec("fantasy_get_series", "Read one series.", service.get_series, {"series_id": integer}, ("series_id",), read_only=True),
        spec("fantasy_list_series_leagues", "List leagues for one series.", service.list_series_leagues, {"series_id": integer}, ("series_id",), read_only=True),
        spec(
            "fantasy_get_my_team",
            "Read the authenticated player's team for one series and league.",
            service.get_my_team,
            {"series_id": integer, "league_code": string},
            ("series_id",),
            read_only=True,
        ),
        spec("fantasy_list_store_packs", "List packs and the authenticated player's pack state.", service.list_store_packs, read_only=True),
        spec(
            "fantasy_list_marketplace",
            "Search active public marketplace listings with bounded filters.",
            service.list_marketplace,
            {
                "fantasy_player_id": integer,
                "tournament_id": integer,
                "series_id": integer,
                "rarity": string,
                "min_price": integer,
                "max_price": integer,
                "perk_ids": strings,
                "sort_by": string,
                "page": nonnegative,
                "size": {"type": "integer", "minimum": 1, "maximum": 100},
            },
            read_only=True,
        ),
        spec(
            "fantasy_get_marketplace_analytics",
            "Read marketplace summary or player-rarity detail analytics.",
            service.get_marketplace_analytics,
            {"fantasy_player_ids": ids, "fantasy_player_id": integer, "rarity": string},
            read_only=True,
        ),
        spec("fantasy_get_my_listings", "Read marketplace listings owned by the authenticated player.", service.get_my_listings, read_only=True),
        spec("fantasy_get_economy_info", "Read economy rules and authenticated balance context.", service.get_economy_info, read_only=True),
        spec("fantasy_get_card_value_info", "Read card-value rules.", service.get_card_value_info, read_only=True),
        spec("fantasy_get_merge_options", "Read eligible cards and current merge options.", service.get_merge_options, read_only=True),
        spec("fantasy_get_legendary_upgrade_info", "Read legendary-upgrade candidates, rules, and costs.", service.get_legendary_upgrade_info, read_only=True),
        spec("fantasy_get_achievement_catalog", "Read achievement catalog and authenticated claim state.", service.get_achievement_catalog, read_only=True),
        spec("fantasy_get_periodic_rating_current", "Read the current periodic rating.", service.get_periodic_rating_current, read_only=True),
        spec(
            "fantasy_get_periodic_rating_me",
            "Read the authenticated player's position in one periodic rating.",
            service.get_periodic_rating_me,
            {"period_id": integer},
            ("period_id",),
            read_only=True,
        ),
        spec(
            "fantasy_get_periodic_rating_rewards",
            "Read the authenticated player's periodic reward list or one reward.",
            service.get_periodic_rating_rewards,
            {"reward_id": integer},
            read_only=True,
        ),
        spec(
            "fantasy_search_periodic_reward_players",
            "Search eligible player candidates for one periodic reward.",
            service.search_periodic_reward_players,
            {
                "reward_id": integer,
                "query": {"type": "string", "maxLength": 128},
                "page": nonnegative,
                "size": {"type": "integer", "minimum": 1, "maximum": 100},
            },
            ("reward_id",),
            read_only=True,
        ),
        spec(
            "fantasy_create_team",
            "Create a team and verify its exact ordered card slots.",
            service.create_team,
            {**write_common, "series_id": integer, "league_code": string, "user_card_ids": card_ids},
            ("run_id", "operation_id", "series_id", "league_code", "user_card_ids"),
            read_only=False,
        ),
        spec(
            "fantasy_update_team",
            "Update a team and verify its exact ordered card slots.",
            service.update_team,
            {**write_common, "series_id": integer, "league_code": string, "user_card_ids": card_ids},
            ("run_id", "operation_id", "series_id", "league_code", "user_card_ids"),
            read_only=False,
        ),
        spec(
            "fantasy_reconcile_operation",
            "Read back and durably resolve an existing ambiguous operation without resending it.",
            service.reconcile_operation,
            {"operation_id": string},
            ("operation_id",),
            read_only=False,
            requires_write_context=False,
        ),
        spec("fantasy_buy_pack", "Buy a pack with the operation id as its idempotency key.", service.buy_pack, {**write_common, "pack_id": integer}, ("run_id", "operation_id", "pack_id"), read_only=False),
        spec(
            "fantasy_select_pack_choice",
            "Resolve a pending pack choice and verify that cards materialized.",
            service.select_pack_choice,
            {**write_common, "choice_id": integer, "option_id": string},
            ("run_id", "operation_id", "choice_id", "option_id"),
            read_only=False,
        ),
        spec(
            "fantasy_create_marketplace_listing",
            "List one owned card for sale and verify the listing.",
            service.create_marketplace_listing,
            {**write_common, "user_card_id": integer, "price": integer},
            ("run_id", "operation_id", "user_card_id", "price"),
            read_only=False,
        ),
        spec(
            "fantasy_update_marketplace_listing_price",
            "Reprice one owned listing and verify the new price.",
            service.update_marketplace_listing_price,
            {**write_common, "listing_id": integer, "price": integer},
            ("run_id", "operation_id", "listing_id", "price"),
            read_only=False,
        ),
        spec("fantasy_cancel_marketplace_listing", "Cancel one owned listing and verify its absence.", service.cancel_marketplace_listing, {**write_common, "listing_id": integer}, ("run_id", "operation_id", "listing_id"), read_only=False),
        spec("fantasy_buy_marketplace_listing", "Buy one listing and verify the recorded buyer.", service.buy_marketplace_listing, {**write_common, "listing_id": integer}, ("run_id", "operation_id", "listing_id"), read_only=False),
        spec("fantasy_renew_card", "Renew one owned card and verify its counters.", service.renew_card, {**write_common, "user_card_id": integer}, ("run_id", "operation_id", "user_card_id"), read_only=False),
        spec("fantasy_recycle_card", "Recycle one owned card and verify its absence.", service.recycle_card, {**write_common, "user_card_id": integer}, ("run_id", "operation_id", "user_card_id"), read_only=False),
        spec(
            "fantasy_merge_cards_preview",
            "Create a merge preview; the outcome remains UNKNOWN because no exact preview read endpoint exists.",
            service.merge_cards_preview,
            {**write_common, "operation": string, "input_user_card_ids": ids, "selected_skin_source_user_card_id": integer},
            ("run_id", "operation_id", "operation", "input_user_card_ids"),
            read_only=False,
        ),
        spec(
            "fantasy_merge_cards_confirm",
            "Confirm a merge and verify consumed inputs plus one new card.",
            service.merge_cards_confirm,
            {**write_common, "operation": string, "input_user_card_ids": ids, "preview_id": integer, "selected_perk_ids": strings, "selected_skin_source_user_card_id": integer},
            ("run_id", "operation_id", "operation", "input_user_card_ids", "preview_id"),
            read_only=False,
        ),
        spec(
            "fantasy_legendary_upgrade",
            "Upgrade a card and verify legendary rarity plus selected perk.",
            service.legendary_upgrade,
            {**write_common, "user_card_id": integer, "perk_id": string},
            ("run_id", "operation_id", "user_card_id", "perk_id"),
            read_only=False,
        ),
        spec("fantasy_claim_achievement", "Claim an achievement and verify claim state where observable.", service.claim_achievement, {**write_common, "code": string}, ("run_id", "operation_id", "code"), read_only=False),
        spec(
            "fantasy_select_achievement_reward",
            "Select generated achievement rewards and verify final claim state.",
            service.select_achievement_reward,
            {**write_common, "code": string, "reward_id": integer, "option_ids": strings},
            ("run_id", "operation_id", "code", "reward_id", "option_ids"),
            read_only=False,
        ),
        spec(
            "fantasy_save_periodic_reward_draft",
            "Save and exactly verify a periodic reward draft.",
            service.save_periodic_reward_draft,
            {**write_common, "reward_id": integer, "player_id": integer, "perk_ids": strings, "skin_code": string, "version": nonnegative},
            ("run_id", "operation_id", "reward_id", "player_id", "perk_ids", "skin_code", "version"),
            read_only=False,
        ),
        spec(
            "fantasy_submit_periodic_reward",
            "Submit and verify fulfillment of a periodic reward.",
            service.submit_periodic_reward,
            {**write_common, "reward_id": integer, "version": nonnegative},
            ("run_id", "operation_id", "reward_id", "version"),
            read_only=False,
        ),
    )
    return FantasyToolRegistry(tools)


def _validate_schema(value: Any, schema: Mapping[str, Any], path: str = "arguments") -> None:
    kind = schema.get("type")
    if kind == "object":
        if not isinstance(value, Mapping):
            raise ValueError(f"{path} must be an object")
        properties = schema.get("properties", {})
        required = schema.get("required", ())
        missing = [key for key in required if key not in value]
        if missing:
            raise ValueError(f"{path} is missing required fields: {', '.join(missing)}")
        unknown = [str(key) for key in value if key not in properties]
        if unknown and schema.get("additionalProperties") is False:
            raise ValueError(f"{path} contains unknown fields: {', '.join(sorted(unknown))}")
        for key, item in value.items():
            if key in properties:
                _validate_schema(item, properties[key], f"{path}.{key}")
        return
    if kind == "integer":
        if isinstance(value, bool) or not isinstance(value, int):
            raise ValueError(f"{path} must be an integer")
        if "minimum" in schema and value < schema["minimum"]:
            raise ValueError(f"{path} is below its minimum")
        if "maximum" in schema and value > schema["maximum"]:
            raise ValueError(f"{path} exceeds its maximum")
        return
    if kind == "string":
        if not isinstance(value, str):
            raise ValueError(f"{path} must be a string")
        if "minLength" in schema and len(value) < schema["minLength"]:
            raise ValueError(f"{path} is too short")
        if "maxLength" in schema and len(value) > schema["maxLength"]:
            raise ValueError(f"{path} is too long")
        return
    if kind == "boolean":
        if not isinstance(value, bool):
            raise ValueError(f"{path} must be a boolean")
        return
    if kind == "array":
        if not isinstance(value, list):
            raise ValueError(f"{path} must be an array")
        if "minItems" in schema and len(value) < schema["minItems"]:
            raise ValueError(f"{path} has too few items")
        if "maxItems" in schema and len(value) > schema["maxItems"]:
            raise ValueError(f"{path} has too many items")
        if schema.get("uniqueItems") and len({canonical_json(item) for item in value}) != len(value):
            raise ValueError(f"{path} must contain unique items")
        item_schema = schema.get("items", {})
        for index, item in enumerate(value):
            _validate_schema(item, item_schema, f"{path}[{index}]")
