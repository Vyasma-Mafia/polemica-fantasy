---
name: polemica-feature-delivery
description: Run large Polemica Fantasy features through a multi-agent delivery loop with planning, plan review, vertical-slice implementation, ownership fan-out, code review, local testing, UX review, and final integration. Use when implementing substantial backend/TMA/admin features, cross-module API changes, economy changes, notifications, marketplace, achievements, onboarding, or admin workflows after or alongside feature discovery.
---

# Polemica Feature Delivery

Use this skill for large or risky Polemica Fantasy features that need more than direct implementation.

This skill extends `polemica-feature-discovery`: discovery shapes the feature; delivery turns an accepted feature brief or clear request into reviewed, tested code.

## First Steps

1. Read `AGENTS.md`.
2. Read `.codex/skills/polemica-feature-discovery/SKILL.md`.
3. Read `docs/codex/MULTI_AGENT_WORKFLOW.md`.
4. For broad work, read `memory-bank/activeContext.md`, `memory-bank/progress.md`, `memory-bank/systemPatterns.md`, `memory-bank/techContext.md`, `memory-bank/glossary.md`, and `memory-bank/operationalInsights.md`.
5. Read relevant `docs/features/*.md` when the feature overlaps an existing area.
6. Run `git status --short`.
7. Decide whether the request is discovery-only, plan/spec first, implementation from an existing plan, or review/testing of an existing implementation.

## Operating Model

The main agent owns the feature end to end.

The main agent must:

- preserve the user request and exact constraints;
- own product decisions and tradeoffs;
- own backend/TMA/admin API contracts;
- own Flyway migration numbering;
- assign worker scopes and forbidden areas;
- review and integrate sub-agent output;
- run or coordinate final verification;
- update `memory-bank/activeContext.md` and `memory-bank/progress.md` after meaningful feature, architecture, dependency, or deployment changes.

Sub-agents are helpers, not co-owners. Use them only for bounded work.

## Delivery Loop

For large features, use these stages. Skip stages only when they would add no signal for the current request.

### 1. Plan Writer

Use a planning sub-agent when the implementation path is not trivial.

Prompt shape:

```text
You are the planning agent for Polemica Fantasy.
Do not edit files.

Read the relevant project context and produce an implementation plan for this feature.

Return:
- concise feature summary
- acceptance criteria and non-goals
- backend changes
- TMA changes
- admin changes
- database/migration impact
- API contract changes
- tests and verification commands
- rollout or operational risks
- open questions
- suggested vertical slice
- suggested worker split with file/module ownership
```

The plan must identify the first end-to-end scenario before suggesting parallel work.

### 2. Plan Reviewer

Use an independent reviewer before coding. Treat this reviewer as a gatekeeper.

Prompt shape:

```text
You are the plan reviewer for Polemica Fantasy.
Do not edit files.

Review the implementation plan critically.

Return:
- blockers that must be fixed before coding
- unclear product or API decisions
- missing edge cases
- migration/data consistency risks
- economy, abuse, notification, or operational risks
- missing UX states
- missing tests or verification
- whether the proposed vertical slice is thin enough and useful
- suggested edits to the plan
```

If blockers exist, revise the plan or send it back to the planning agent. Do not start coding until blockers are resolved, explicitly deferred, or accepted as assumptions.

### 3. QA/Risk Reviewer Before Coding

Use a QA/risk reviewer before implementation when the feature touches scoring, economy, marketplace, notifications, admin operations, migrations, deadlines, or data sync.

Prompt shape:

```text
You are the QA/risk reviewer for Polemica Fantasy.
Do not edit files.

Review the planned feature before implementation.

Return:
- behavioral edge cases
- data consistency and migration risks
- economy and abuse risks
- notification and operational risks
- auth/permission concerns
- loading, empty, error, disabled, and success states to verify
- targeted tests and local checks
- release or rollback concerns
```

Feed material findings back into the plan before coding.

### 4. Vertical Slice Worker

For unclear or cross-module features, prefer one vertical-slice worker before parallel ownership workers.

Use this when:

- the API contract is not yet stable;
- UI behavior depends on backend semantics;
- the feature is small but cross-cutting;
- many files are connected through one DTO/client;
- product iteration speed matters more than parallelism.

Prompt shape:

```text
You are the vertical-slice worker for Polemica Fantasy.
You are not alone in the codebase. Do not revert or overwrite unrelated changes.

Implement the thinnest useful end-to-end scenario for this feature:
[scenario]

Own the minimal backend contract, DTOs, one endpoint or service path, one admin or TMA path, and the API client/types required for the scenario.

Keep the slice narrow. Do not complete every edge case or every screen unless explicitly assigned.

Return:
- changed files
- finalized or proposed API contract
- assumptions and deferred work
- verification commands run
- what ownership workers should do next
```

The main agent must review the slice and freeze or revise the contract before fan-out.

### 5. Ownership Workers

After the vertical scenario and API contract are stable, fan out by ownership when it reduces conflict risk or accelerates large work.

Use ownership fan-out when:

- the contract is accepted;
- backend/TMA/admin tasks are independent enough;
- the remaining work is large or repetitive;
- merge conflicts are likely without file ownership;
- different parts require different expertise.

Common rules for all workers:

```text
You are not alone in the codebase.
Do not revert unrelated changes.
Only edit files inside your ownership scope.
Do not change API contracts unless explicitly assigned.
Return changed files, important decisions, and verification results.
```

Typical scopes:

- Backend worker: `polemica-fantasy-backend/**`.
- TMA worker: `polemica-fantasy-webapp/src/**`.
- Admin worker: `polemica-fantasy-admin/src/**`.
- Docs/test worker: `docs/**`, `memory-bank/**`, or focused tests only when assigned.

Do not run multiple workers against the same DTOs, API clients, migrations, large CSS files, or tightly coupled service/entity code unless the scopes are explicitly separated.

### 6. Code Reviewer

After implementation, use an independent code reviewer. Treat this reviewer as a gatekeeper.

Prompt shape:

```text
You are the code reviewer for Polemica Fantasy.
Do not edit files.

Review the implemented changes against the accepted plan and project conventions.

Focus on:
- behavioral bugs and regressions
- backend/API/frontend contract mismatches
- Flyway and data consistency issues
- transaction boundaries
- auth and permissions
- loading, empty, error, disabled, and success states
- missing or weak tests
- build/runtime risks
- unrelated changes

Return findings ordered by severity with file/line references.
```

If findings require changes, send them back to the relevant implementation worker or fix them in the main agent before moving to final verification.

### 7. Local Tester

Use a local testing sub-agent when the feature has user-visible behavior or integration risk.

Prompt shape:

```text
You are the local tester for Polemica Fantasy.

Use `.codex/skills/polemica-local-testing` when UI testing is needed.

Test the new feature locally.

Return:
- environment started
- exact commands run
- user/admin flows tested
- screenshots or observations when useful
- console/network/backend errors
- failed cases
- remaining risks
```

Prefer targeted verification first, then `./scripts/codex-check.sh quick` for broad cross-module changes when dependencies are available.

### 8. UX Designer / UX Reviewer

Use UX review twice for user-visible features:

- before implementation, to check the proposed flow, states, and copy;
- after implementation, to check the actual UI and interaction.

Prompt shape:

```text
You are the UX reviewer for Polemica Fantasy.
Do not edit backend code.

Review whether the feature is understandable, efficient, and consistent with existing TMA/admin UX.

Return:
- confusing or unnecessary steps
- missing states: loading, empty, error, disabled, success
- unclear copy
- mobile/responsive issues
- admin workflow friction
- visual inconsistencies
- recommended changes with affected files
```

If UX findings are material, send them back to the relevant vertical-slice or ownership worker.

## Final Integration

The main agent must finish by checking:

- backend DTOs match frontend `src/api/types.ts`;
- API clients and call sites are synchronized;
- migrations are correctly numbered and scoped;
- relevant backend tests, frontend builds, or local checks passed or failed with reasons;
- user changes from `git status --short` were preserved;
- `memory-bank/activeContext.md` and `memory-bank/progress.md` were updated when required.

## When To Skip The Full Loop

Keep the work local or use only selected stages when:

- the task is a small single-file fix;
- the next step is blocked on one investigation result;
- multiple agents would edit the same file or tightly coupled code;
- the work requires tight migration/entity/service iteration;
- the user asked for a direct answer rather than a plan or implementation;
- the change is urgent production work with an explicit operational path.
