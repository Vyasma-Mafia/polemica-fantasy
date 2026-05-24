#!/usr/bin/env python3
"""
Perk balance calculator.

Takes the raw report from POST /api/v1/admin/perk-statistics/collect
and computes balanced bonus_points so that every perk has the same
expected contribution per game for a random seat at a classic 10-player table.

Role priors (classic mafia 10):
  PEACE=6/10, MAFIA=2/10, DON=1/10, SHERIFF=1/10
"""

import json
import sys
from pathlib import Path

ROLE_PRIORS = {
    "PEACE": 6 / 10,
    "MAFIA": 2 / 10,
    "DON": 1 / 10,
    "SHERIFF": 1 / 10,
}

PERK_APPLICABLE_ROLES = {
    # 10/10 seats (report uses same count as all players in game)
    "crowned": ["PEACE", "MAFIA", "DON", "SHERIFF"],
    "lastHeroGuess": ["PEACE", "MAFIA", "DON", "SHERIFF"],
    "ninja": ["PEACE", "MAFIA", "DON", "SHERIFF"],
    "sniper": ["DON", "MAFIA"],
    "winThreeToThree": ["DON", "MAFIA"],
    "findSheriff": ["DON"],
    "voteForBlack": ["PEACE", "SHERIFF"],
    "strongCity": ["PEACE"],
    "firstKickedFullGuess": ["PEACE", "SHERIFF"],
    "votingOnlyForBlack": ["PEACE"],
    "winWithoutCritic": ["PEACE", "SHERIFF"],
}


def p_applicable(perk_id: str) -> float:
    roles = PERK_APPLICABLE_ROLES.get(perk_id, [])
    return sum(ROLE_PRIORS.get(r, 0) for r in roles)


def main():
    if len(sys.argv) < 2:
        print("Usage: python balance_perks.py <report.json> [target_E]")
        print("  report.json  — output of POST .../perk-statistics/collect")
        print("  target_E     — desired E[bonus per game] for each perk (default: auto = median of current)")
        sys.exit(1)

    report = json.loads(Path(sys.argv[1]).read_text())
    rows = {r["perkId"]: r for r in report["byPerk"]}

    print(f"Games: {report['uniqueGamesLoaded']}")
    print(f"Players in DB: {report['fantasyPlayerCount']}")
    print()

    results = []
    for ach_id, row in sorted(rows.items()):
        applicable_slots = row["applicableSlots"]
        applied = row["sumAppliedOccurrences"]
        positive = row["slotsWithPositiveRaw"]

        e_applied_given_applicable = applied / applicable_slots if applicable_slots else 0
        p_app = p_applicable(ach_id)
        e_bonus_per_game = p_app * e_applied_given_applicable  # with bonus_points=1

        results.append({
            "id": ach_id,
            "applicable_roles": PERK_APPLICABLE_ROLES.get(ach_id, []),
            "p_applicable": p_app,
            "applicable_slots": applicable_slots,
            "applied_total": applied,
            "positive_slots": positive,
            "e_applied_given_applicable": e_applied_given_applicable,
            "e_bonus_per_game_bp1": e_bonus_per_game,
        })

    print("=== Current E[bonus per game] with bonus_points=1 ===")
    print(f"{'Perk':<25} {'P(appl)':<10} {'E[appl|appl]':<14} {'E[bonus/game]':<14} {'P(trigger/game)':<16}")
    print("-" * 80)
    for r in sorted(results, key=lambda x: x["e_bonus_per_game_bp1"]):
        p_trigger = r["positive_slots"] / r["applicable_slots"] if r["applicable_slots"] else 0
        print(
            f"{r['id']:<25} "
            f"{r['p_applicable']:<10.3f} "
            f"{r['e_applied_given_applicable']:<14.6f} "
            f"{r['e_bonus_per_game_bp1']:<14.6f} "
            f"{p_trigger * r['p_applicable']:<16.6f}"
        )

    e_values = [r["e_bonus_per_game_bp1"] for r in results if r["e_bonus_per_game_bp1"] > 0]
    median_e = sorted(e_values)[len(e_values) // 2]

    if len(sys.argv) >= 3:
        target = float(sys.argv[2])
    else:
        target = median_e

    print()
    print(f"=== Balanced bonus_points (target E = {target:.6f}, from {'argument' if len(sys.argv) >= 3 else 'median'}) ===")
    print(f"{'Perk':<25} {'Raw bp':<12} {'Rounded bp':<12} {'New E[bonus/game]':<18}")
    print("-" * 68)
    for r in sorted(results, key=lambda x: x["id"]):
        if r["e_bonus_per_game_bp1"] > 0:
            raw_bp = target / r["e_bonus_per_game_bp1"]
        else:
            raw_bp = float("inf")
        rounded_bp = round(raw_bp, 2)
        new_e = r["e_bonus_per_game_bp1"] * rounded_bp
        print(f"{r['id']:<25} {raw_bp:<12.4f} {rounded_bp:<12.2f} {new_e:<18.6f}")

    print()
    print("=== Summary table (copy-paste for docs) ===")
    print(f"| {'Perk':<23} | {'bonus_points':>12} | {'E[bonus/game]':>14} |")
    print(f"|{'-'*25}|{'-'*14}|{'-'*16}|")
    for r in sorted(results, key=lambda x: x["id"]):
        if r["e_bonus_per_game_bp1"] > 0:
            raw_bp = target / r["e_bonus_per_game_bp1"]
        else:
            raw_bp = 0
        rounded_bp = round(raw_bp, 2)
        new_e = r["e_bonus_per_game_bp1"] * rounded_bp
        print(f"| {r['id']:<23} | {rounded_bp:>12.2f} | {new_e:>14.6f} |")


if __name__ == "__main__":
    main()
