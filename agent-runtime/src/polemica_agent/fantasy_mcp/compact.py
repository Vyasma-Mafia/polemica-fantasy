"""Loss-aware presentation only: never used as persisted or authorizing evidence."""
from __future__ import annotations

from typing import Any


_VISUAL = frozenset({"imageUrl", "playerPhotoUrl", "photoUrl", "iconUrl", "accentColor"})


def compact_observations(observations: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Copy observations, retaining their full-payload hashes and every source/card.

    Known presentation fields alone are removed. Unknown fields survive schema
    evolution. Skin/provenance, perks, prices, eligibility and choice IDs survive.
    """
    return [{**item, "data": _compact(item["data"], item["objectId"])} for item in observations]


def _compact(value: Any, source: str) -> Any:
    if isinstance(value, list):
        return [_compact(item, source) for item in value]
    if not isinstance(value, dict):
        return value
    excluded = set(_VISUAL)
    # Card flavour text is not a perk description; keep perk descriptions/rules.
    if "fantasyPlayerId" in value and "rarity" in value and "perks" in value:
        excluded.add("description")
    if source == "/api/v1/achievements" and "conditionType" in value:
        # Completed claims remain individually discoverable (including card reward
        # metadata that may require claim-state lookup for pending choices).
        if value.get("state") == "CLAIMED":
            excluded.update({"title", "description", "category", "historyPolicy", "rarity", "visibility"})
        else:
            # Keep the title, condition text, progress and reward economics for all
            # unfinished milestones, including presently unclaimable ones.
            excluded.update({"category", "historyPolicy", "rarity", "visibility"})
    return {key: _compact(item, source) for key, item in value.items() if key not in excluded}
