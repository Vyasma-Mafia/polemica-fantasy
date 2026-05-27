# Codex Multi-Agent Workflow

This document defines how to use Codex sub-agents for Polemica Fantasy. The goal is to separate product/design discovery from implementation, then keep development coordinated across backend, TMA, admin, migrations, and tests.

## Operating Model

Use one main Codex agent as the owner of the task. The main agent reads project context, sets the plan, delegates bounded work, reviews results, integrates changes, runs verification, and updates `memory-bank/` when needed.

Use sub-agents only for scoped work that can run independently:

- `explorer` for read-only investigation and answers to specific codebase questions.
- `worker` for bounded implementation with an explicit module/file ownership scope.
- Custom roles are expressed in the prompt, not by changing the technical agent type.

Do not let sub-agents become parallel owners of the same feature. Product decisions, API contracts, migration numbering, final integration, and release notes stay with the main agent.

## Sub-Agent Execution

The main agent should first identify the immediate critical-path task it will do locally, then spawn sub-agents only for sidecar work that can run in parallel.

Use this handoff shape:

- **Role**: product, TMA design, admin UX, backend explorer, frontend explorer, QA, backend worker, TMA worker, admin worker.
- **Mode**: read-only explorer or bounded implementation worker.
- **Context**: include the user request, relevant product constraints, and any decisions already made by the main agent.
- **Scope**: list owned modules/files and forbidden areas.
- **Output**: say exactly what the sub-agent must return.

Run independent discovery agents in parallel when their questions do not overlap. Run implementation workers in parallel only when their write scopes are disjoint. Wait for a sub-agent only when its answer is needed for the next critical-path decision.

## Phase 0: Intake

Start broad feature work by reading:

- `AGENTS.md`
- `memory-bank/activeContext.md`
- `memory-bank/progress.md`
- `memory-bank/systemPatterns.md`
- `memory-bank/techContext.md`
- `memory-bank/glossary.md`
- `memory-bank/operationalInsights.md`
- relevant `docs/features/*.md`

Then run `git status --short` and identify whether the work is:

- **Discovery only**: product/design exploration, no code changes.
- **Spec first**: produce a feature brief/design doc before implementation.
- **Implementation**: code is already requested or the spec is clear.
- **Review/diagnosis**: find bugs, regressions, or production causes.

## Phase 1: Product Discovery

Use this phase when a feature is still unclear or affects user behavior, economy, operations, or notifications.

Suggested product agent prompt:

```text
You are the product agent for Polemica Fantasy.
Do not edit files. Read AGENTS.md, memory-bank/glossary.md, memory-bank/operationalInsights.md, and any relevant feature docs.

Task: shape the feature request below into a concise product brief.

Return:
- user problem and target audience
- primary user journey
- acceptance criteria
- non-goals
- edge cases
- economy, abuse, notification, and operational risks
- analytics/events worth tracking
- open questions that must be answered before implementation
```

Expected artifact:

- A short feature brief in chat, or a new `docs/features/DESIGN-*.md` when the feature is large enough to preserve.

Preserve the brief as `docs/features/DESIGN-*.md` when the feature has any of these traits:

- backend plus TMA/admin contract changes;
- database migration or backfill;
- economy, rewards, marketplace, sanctions, or abuse impact;
- notifications, campaigns, onboarding, or analytics events;
- new admin workflow plus user-facing workflow;
- decisions likely to be revisited after this conversation.

If product/design sub-agents read narrower context than Phase 0, the main agent must pass a concise context summary with active constraints and relevant prior decisions.

## Phase 2: Design Discovery

Use this phase for TMA screens, admin workflows, card visuals, onboarding, marketplace UX, achievements, or any feature where layout and states matter.

Suggested TMA design agent prompt:

```text
You are the TMA design agent for Polemica Fantasy.
Do not edit backend or admin. Prefer read-only analysis unless explicitly asked for mockup files.
Study existing TMA pages, shared card components, CSS naming, and mobile constraints.

Task: propose the UX for the feature below.

Return:
- main screen flow
- layout structure
- controls and states: loading, empty, error, disabled, success
- responsive/mobile risks
- copy that appears in UI
- visual reuse from existing components
- files likely affected
```

Suggested admin design agent prompt:

```text
You are the admin UX agent for Polemica Fantasy.
Do not edit backend or TMA. Study existing Ant Design pages and admin API usage.

Task: propose the admin workflow for the feature below.

Return:
- page/table/form/modal flow
- validation and confirmation points
- batch actions if useful
- empty/error/loading states
- auditability and rollback needs
- files likely affected
```

For visual mockups, prefer repo-native artifacts when they will become implementation references: markdown wireframes, screenshots from local UI, or small static HTML/CSS prototypes under `docs/superpowers/plans/` only when explicitly useful.

## Phase 3: Technical Discovery

Use technical explorers before implementation when the feature touches shared contracts or fragile domain rules.

Suggested backend explorer prompt:

```text
You are the backend explorer for Polemica Fantasy.
Do not edit files.

Question: where should this feature fit in the backend?

Return:
- relevant controllers/services/repositories/entities/DTOs
- transaction and locking concerns
- Flyway migration needs
- API contract changes
- tests to add or update
- risks from existing patterns
```

Suggested frontend explorer prompt:

```text
You are the frontend explorer for Polemica Fantasy.
Do not edit files.

Question: where should this feature fit in the frontend?

Return:
- relevant pages/components/api clients/types/CSS
- existing UX patterns to reuse
- query cache keys and invalidation needs
- build or runtime risks
```

Suggested QA/risk explorer prompt:

```text
You are the QA/risk explorer for Polemica Fantasy.
Do not edit files.

Question: what could break or be missed in this feature?

Return:
- behavioral edge cases
- missing loading/empty/error/disabled states
- economy or abuse risks
- migration/backfill/data consistency risks
- notification or analytics risks
- targeted tests and build checks to run
- release or rollback concerns
```

## Phase 4: Implementation

Only enter implementation after the main agent has a clear contract.

Implementation gate:

- unresolved open questions are answered, explicitly deferred, or accepted as assumptions;
- product acceptance criteria are clear;
- backend API contract is known when frontend/admin work depends on it;
- migration ownership and next Flyway version are assigned if needed;
- worker write scopes are disjoint;
- verification commands are chosen before coding starts.

Split workers by disjoint ownership:

- **Backend worker**: Kotlin entities, DTOs, services, controllers, repositories, Flyway, backend tests.
- **TMA worker**: `polemica-fantasy-webapp/src/**`, TMA API clients/types, pages, components, CSS.
- **Admin worker**: `polemica-fantasy-admin/src/**`, admin API clients/types, pages, Ant Design flows.
- **Docs worker**: feature spec, memory-bank draft notes, rollout notes.
- **QA explorer**: independent risk review or missing-test pass.

Worker prompt rules:

- Say the worker is not alone in the codebase.
- Give exact ownership and forbidden areas.
- Include the API contract if frontend work depends on backend work.
- Ask the worker to list changed files and verification commands.
- Tell the worker not to revert unrelated changes.

Backend worker template:

```text
You are the backend worker for Polemica Fantasy.
You are not alone in the codebase. Do not revert or overwrite unrelated changes.

Ownership: backend only: polemica-fantasy-backend/**.
Forbidden: polemica-fantasy-webapp/**, polemica-fantasy-admin/**, memory-bank/** unless explicitly needed.

Implement the backend part of this feature:
[contract]

Follow existing Controller -> Service -> Repository patterns. Entities must not leave the service layer. Keep Polemica HTTP calls outside long transactions. Add or update focused tests.

In the final answer, list changed files, important decisions, and verification results.
```

Frontend worker template:

```text
You are the TMA/frontend worker for Polemica Fantasy.
You are not alone in the codebase. Do not revert or overwrite unrelated changes.

Ownership: polemica-fantasy-webapp/src/**.
Forbidden: backend/admin changes.

Backend contract:
[contract]

Implement the UI with existing React Query, Router, card image, rarity, and skin patterns. Include loading, empty, error, and disabled states where relevant.

In the final answer, list changed files and verification results.
```

Admin worker template:

```text
You are the admin worker for Polemica Fantasy.
You are not alone in the codebase. Do not revert or overwrite unrelated changes.

Ownership: polemica-fantasy-admin/src/**.
Forbidden: backend/TMA changes.

Backend contract:
[contract]

Implement the admin workflow with Ant Design 6 patterns already used in the app. Include validation, confirmation, loading, error, and success states.

In the final answer, list changed files and verification results.
```

## Integration Rules

The main agent must review and integrate all outputs:

- Check DTOs, frontend `src/api/types.ts`, API clients, and UI call sites together.
- Keep Flyway migration numbering single-owner.
- Resolve terminology using `memory-bank/glossary.md`.
- Preserve user changes from `git status --short`.
- Update `memory-bank/activeContext.md` and `memory-bank/progress.md` after meaningful feature, architecture, dependency, or deployment changes.
- Use `./scripts/codex-check.sh quick` for broad cross-module changes when dependencies are available.

## Verification Defaults

Choose the narrowest useful verification:

- Backend compile: `cd polemica-fantasy-backend && ./gradlew compileKotlin compileTestKotlin`
- Backend targeted test: `cd polemica-fantasy-backend && ./gradlew test --tests "io.github.mralex1810.fantasy.XXX"`
- TMA build: `cd polemica-fantasy-webapp && npm run build`
- Admin build: `cd polemica-fantasy-admin && npm run build`
- Cross-module quick check: `./scripts/codex-check.sh quick`
- Interactive TMA/admin local testing: use `.codex/skills/polemica-local-testing`
- Production DB read-only diagnosis: use `.codex/skills/polemica-prod-db-readonly`

## When Not To Use Sub-Agents

Keep the work local when:

- the task is a small single-file fix;
- the next step is blocked on one investigation result;
- multiple agents would edit the same large file;
- the work requires tight migration/entity/service iteration;
- the user asked for a direct answer rather than a plan or implementation.

## Feature Brief Template

```markdown
# Feature Brief: <name>

## Problem

## Users

## Proposed Experience

## Acceptance Criteria

## Non-Goals

## Data/API Impact

## Admin Impact

## TMA Impact

## Economy/Abuse Risks

## Notifications/Analytics

## Rollout And Verification

## Open Questions
```
