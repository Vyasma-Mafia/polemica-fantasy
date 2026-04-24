#!/usr/bin/env python3
"""
Sanity check: docs/features/DESIGN-CARD-VALUE-AND-LEAGUES.md §14.

Default economy: achievement 10, bases 25/40/80/370 → std totals C=25, R=50, E=100, L=400.
Accepted: league.budget.value_cap = 175 (3-card allowed: 6/20).

Run: python3 scripts/budget_league_cap_check.py
"""
from __future__ import annotations

from itertools import combinations_with_replacement

VAL = {"C": 25, "R": 50, "E": 100, "L": 400}
ORDER = ("C", "R", "E", "L")
DEFAULT_CAP = 175

# (cap, expected_allowed_3, 20 total)
CAPS_3: tuple[tuple[int, int], ...] = (
    (100, 2),
    (125, 3),
    (150, 5),
    (175, 6),
    (200, 7),
    (225, 8),
    (250, 9),
)


def _sum(t: tuple[str, ...]) -> int:
    return sum(VAL[x] for x in t)


def _allowed_3(cap: int) -> int:
    return sum(1 for t in combinations_with_replacement(ORDER, 3) if _sum(t) <= cap)


def main() -> None:
    teams3 = list(combinations_with_replacement(ORDER, 3))
    assert len(teams3) == 20

    min_l = min(_sum(t) for t in teams3 if "L" in t)
    assert min_l == 25 * 2 + 400 == 450, min_l

    assert _allowed_3(DEFAULT_CAP) == 6, f"default cap {DEFAULT_CAP}"

    for cap, exp in CAPS_3:
        got = _allowed_3(cap)
        assert got == exp, f"3-card cap {cap}: got {got}, want {exp}"

    for cap in (c for c, _ in CAPS_3):
        a = [t for t in teams3 if _sum(t) <= cap]
        b = [t for t in teams3 if _sum(t) > cap]
        for t in a:
            assert _sum(t) <= cap, t
        for t in b:
            assert _sum(t) > cap, t
        if a and b:
            assert _sum(max(a, key=_sum)) < _sum(min(b, key=_sum))

    print(
        f"budget_league_cap_check: OK (COMMON=25, cap {DEFAULT_CAP}, 3-card {_allowed_3(DEFAULT_CAP)}/20)"
    )


if __name__ == "__main__":
    main()
