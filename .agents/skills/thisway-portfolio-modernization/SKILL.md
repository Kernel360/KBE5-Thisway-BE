---
name: thisway-portfolio-modernization
description: Modernize the Thisway Java/Spring fleet backend while preserving team provenance and producing tested portfolio, learning, interview, performance, and AI-use evidence. Use for implementation, refactoring, security, data, messaging, batch, observability, performance, or AI feature work in KBE5-Thisway-BE; do not use for unrelated repositories.
---

# Thisway Portfolio Modernization

## Start from evidence

Before changing code:

1. Read [`AGENTS.md`](../../../AGENTS.md).
2. Read [`docs/portfolio/baseline-audit.md`](../../../docs/portfolio/baseline-audit.md) and the relevant section of [`docs/portfolio/modernization-roadmap.md`](../../../docs/portfolio/modernization-roadmap.md).
3. Inspect the current branch, status, recent commits, and relevant tests. Treat existing dirty changes as user-owned.
4. Label each claim as observed, verified by execution, measured, or planned. Do not turn a static inference into a runtime fact.

## Keep one auditable change unit

For every material change, create or update one file under `docs/portfolio/work-logs/` using [`assets/change-record-template.md`](assets/change-record-template.md). Keep code, tests, and its record in the same change unit.

The record must separate:

- original team implementation;
- the user's original contribution, supported by Git/PR evidence;
- the new personal modernization;
- AI assistance and the human decision that accepted or rejected it.

Do not claim a performance, reliability, security, or AI outcome until its acceptance criteria have passed. Record failed commands and skipped validation rather than omitting them.

## Apply the relevant quality gate

- For tenant or authorization work, test both permitted access and cross-tenant/role denial at the repository/service/API boundary.
- For schema or query work, use an actual MySQL-compatible integration test and a versioned migration; H2-only success is insufficient.
- For RabbitMQ work, cover duplicate delivery, retry classification, DLQ routing, replay, and partial failure. Verify idempotency at the database boundary.
- For Trip processing, define ON/OFF/duplicate/out-of-order/late-event state transitions before implementation.
- For Batch work, verify identifying parameters, partial failure status, restart/backfill, same-date idempotency, and concurrent launch behavior.
- For SSE work, verify authorization, exact subscription-key matching, reconnect/lifecycle cleanup, bounded buffering, and multi-instance behavior.
- For performance work, preserve the environment, data generator seed, thresholds, raw result, and before/after comparison. VU count alone is not a result.
- For an AI feature, first read [`docs/ai/usage-policy.md`](../../../docs/ai/usage-policy.md). Establish a non-AI baseline and fixed evaluation set before selecting a model.

## Finish with understanding

Before declaring completion:

1. Run the narrow regression tests and the broadest safe relevant suite.
2. Update the work log with exact commands, counts, failures, and limitations.
3. Add the concepts the user should study and at least three likely interview questions with answer checkpoints.
4. Explain the request-to-storage/event execution flow in plain Korean so the user can reproduce it without AI.
5. Update portfolio wording only with evidence from this completed change.
