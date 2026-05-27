---
name: polemica-feature-discovery
description: Guide Polemica Fantasy feature discovery with product, design, technical, and QA sub-agent roles before implementation. Use when the user wants to plan, shape, spec, design, review, or de-risk a substantial feature before coding, especially for cross-module backend/TMA/admin work, product flows, economy changes, notifications, achievements, marketplace, or admin workflows.
---

# Polemica Feature Discovery

Use this skill to run a structured product/design discovery pass before implementation.

## First Steps

1. Read `AGENTS.md`.
2. For broad work, read `memory-bank/activeContext.md`, `memory-bank/progress.md`, `memory-bank/systemPatterns.md`, `memory-bank/techContext.md`, `memory-bank/glossary.md`, and `memory-bank/operationalInsights.md`.
3. Read relevant `docs/features/*.md` when the feature overlaps an existing area.
4. Run `git status --short` before editing.
5. Decide whether the current turn is discovery-only, spec-first, implementation, or review/diagnosis.

## Use The Workflow Document

Open `docs/codex/MULTI_AGENT_WORKFLOW.md` for:

- product agent prompts;
- TMA/admin design agent prompts;
- backend/frontend technical explorer prompts;
- backend/TMA/admin worker templates;
- integration and verification rules;
- feature brief template.

Keep this skill small; put detailed changes to the workflow in that document.

## Discovery Rules

- Do not edit code during product/design discovery unless the user explicitly asks for implementation.
- Prefer a short feature brief before code for large product changes.
- Keep the main agent responsible for product decisions, API contracts, Flyway migration numbering, integration, verification, and memory-bank updates.
- Use sub-agents only for concrete, bounded tasks that can run independently.
- Express custom agents through prompts. Technical agent types remain `explorer` for read-only investigation and `worker` for bounded implementation.

## Polemica-Specific Checks

Always check terminology against `memory-bank/glossary.md`:

- `telegram_user` is a user, not a Mafia player.
- `fantasy_player` is the real Mafia player on cards.
- `tournament` is internal; `polemica_competition_id` is external.
- `series`, `game`, `sync`, `scoring`, and `finalize` are distinct concepts.
- Distinguish `card_template` from `user_card`.

Challenge assumptions from `memory-bank/operationalInsights.md`, especially same-day series setup and short team-deadline windows.

## Expected Outputs

For discovery, return one of:

- a concise product brief;
- a design flow with states and affected files;
- a technical impact map;
- a preserved design doc under `docs/features/` when the feature is large enough.

For implementation handoff, produce:

- backend contract;
- TMA/admin impact;
- migration/test plan;
- worker scopes with forbidden areas;
- verification commands.
