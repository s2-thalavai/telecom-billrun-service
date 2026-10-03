# CLAUDE.md

Context for Claude Code (and other AI assistants) working in this repository.

## What this is

`telecom-billrun-service`: a Spring Boot 3.5 / Java 21 service demonstrating long-running background jobs with
Spring's async support: a resumable, cancellable, observable monthly bill run. Read `docs/design.md` first.

## Commands

| Task | Command |
|---|---|
| Run (H2, 5,000 demo accounts, console at `/`) | `make run` |
| Unit + slice + JPA tests | `make test` |
| Integration tests | `make it` |
| Everything + coverage | `make verify` |
| Compose / smoke / metrics / crash demo | `make up`, `make smoke`, `make metrics`, `make crash-demo` |

## Invariants (keep them true)

1. **The HTTP request never does the work.** It inserts a QUEUED job and returns 202. The worker starts AFTER_COMMIT.
2. **No long transactions.** `BillRunWorker` stays non-transactional; business work is one transaction per chunk (`BillingService`); job state uses `JobStateService` (`REQUIRES_NEW`).
3. **Every state change is a conditional UPDATE** in `JobRepository` (compare-and-set). Never load-modify-save job status.
4. **Idempotent work.** Unique `(account_id, cycle)` plus skip-if-invoiced. Resume and rerun must never double-bill.
5. **Checkpoint after each committed chunk**, heartbeat with every progress update. A chunk must finish well within `heartbeat-timeout`.
6. **Cancellation is cooperative first**: check the durable flag between chunks. Interrupt-aware code restores the interrupt flag (`RatingEngine`); the worker clears it before its final DB update.
7. **`BillRunWorker.run` returns `Future`**, not `CompletableFuture`, so `Future.cancel(true)` can interrupt.
8. **Bounded executors** (`BackgroundConfig`); new background work uses a named pool, never the common pool.
9. Side effects (notifications) run only after the chunk's commit.
10. Call `@Async`/`@Transactional` methods through other beans (proxies); never via `this`.

## Testing conventions

- Worker logic: call `run(id)` directly with mocks (`BillRunWorkerTest`); use the Clock constructor for time.
- ITs: one cycle per test (`nextCycle()`); `AbstractJobIT` cancels active jobs after each test; assert invariants (invoices vs recorded progress), not timings.

## Known limitations

See "Limitations and when to graduate" in `docs/design.md`.
