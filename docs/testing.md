# Testing Strategy

| Layer | Naming | Command |
|---|---|---|
| Unit, MockMvc slice, `@DataJpaTest` | `*Test` | `make test` |
| Integration: real background jobs (+ MySQL with Docker) | `*IT` | `make it` |
| All + coverage | | `make verify` |

The worker is tested by calling `run(id)` **directly**, with no proxy and no thread. The loop logic is then deterministic and needs no sleeps. A package-private constructor takes a controllable `Clock` for the max-runtime path.

## Unit and slice tests

| Class | Covers |
|---|---|
| `JobViewTest` | Percent (processed + failed), rate and ETA, 100% when completed, no ETA when finished/queued, capped at 100% |
| `BillRunWorkerTest` | Chunks + progress + checkpoint + metrics + notification; resume from checkpoint; not started when cancelled while queued; cooperative cancel keeps the finished chunk; max runtime → FAILED; interrupt → CANCELLED **and flag cleared**; unexpected error → FAILED |
| `JobLauncherTest` | Launch after the event; `interrupt` calls `Future.cancel(true)`; rejection → FAILED "queue is full"; housekeeping |
| `JobCancellationServiceTest` | Queued → immediate CANCELLED; running → flag only; force → interrupt; finished → 409 |
| `JobServiceTest` | Queued job + event; duplicate active run rejected; unknown job |
| `RatingEngineTest` | Deterministic amounts; every Nth account fails; interrupt → `JobInterruptedException` with the flag kept |
| `JobProgressBroadcasterTest` | Subscribers completed and removed once the job is final |
| `JobControllerTest` | 202 + Location + user header; 400 with field error; 409; 404; force flag; summary validation |
| `JobRepositoryTest` | CAS semantics: `markRunning` once and not after cancel; progress accumulates; finish once; only stale jobs requeued, once; keyset pagination skips inactive accounts |

## Integration tests (test profile: 600 accounts × 5 ms, chunk 50, 1 worker, queue 1)

| Class | Covers |
|---|---|
| `BillRunIT` | Submit returns in < 1 s; RUNNING with progress and ETA; COMPLETED with 594 billed / 6 failed / 100%; invoices match; idempotent rerun; 409 for a duplicate active run; **SSE stream ends with COMPLETED**; 400/404 |
| `CancellationIT` | Cooperative cancel: invoices == recorded progress (stopped at a chunk boundary); forced cancel: CANCELLED, gap ≤ one chunk; queued job cancelled and never started; finished job → 409 |
| `QueueAndRecoveryIT` | Third job beyond 1 worker + queue 1 → FAILED "queue is full"; **orphaned RUNNING job (stale heartbeat, progress behind committed data) resumed from its checkpoint → exactly 594 invoices**; healthy job untouched by recovery |
| `MySqlFlywayIT` | Prod profile on MySQL 8.4: Flyway V1+V2 (2,000 seeded accounts) and a full bill run. Skipped without Docker |

Every IT uses its own billing cycle, and `AbstractJobIT` cancels all active jobs after each test. The single worker is therefore free for the next test.
