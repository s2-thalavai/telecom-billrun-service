# Telecom Bill Run Service

A Spring Boot 3.5 / Java 21 service that runs **long-running background jobs with Spring's async support**.
A monthly bill run invoices every subscriber account of a billing cycle. It takes minutes, runs on a dedicated
`@Async` executor, reports progress, can be cancelled, and resumes after a crash without billing anyone twice.

```
POST /api/v1/bill-runs {"cycle":"2026-09"}  → 202 Accepted + Location: /api/v1/jobs/{id}
GET  /api/v1/jobs/{id}                      → status, percent, items/s, ETA, checkpoint
GET  /api/v1/jobs/{id}/progress             → server-sent progress events
POST /api/v1/jobs/{id}/cancel[?force=true]  → cooperative (or interrupting) cancellation
GET  /api/v1/jobs?status=RUNNING            → job list
GET  /api/v1/bill-runs/{cycle}/summary      → invoices produced
```

| Concern | How |
|---|---|
| Background execution | `@Async("jobExecutor")` on a bounded pool; started **after commit** (`@TransactionalEventListener`) |
| Transactions | No long transaction: one transaction per chunk, separate short transactions for job state |
| Progress | Durable job row (processed/failed/checkpoint/heartbeat), REST, SSE, Micrometer metrics, `[job=…]` in logs |
| Cancellation | Durable flag checked between chunks; `force=true` interrupts via `Future.cancel(true)` |
| Failures | Per-account failures counted; max runtime; full queue → FAILED "queue is full" |
| Restart safety | Heartbeat + `StaleJobRecovery` re-queue (compare-and-set) + resume from checkpoint + unique `(account, cycle)` |

```bash
make run       # http://localhost:8080 → operations console
make test      # unit, MockMvc and JPA tests
make verify    # + integration tests with real background jobs + coverage
make up && make smoke && make crash-demo
```

See [HOW_TO_RUN.md](HOW_TO_RUN.md), [docs/design.md](docs/design.md), [docs/testing.md](docs/testing.md) and
[CLAUDE.md](CLAUDE.md).
# telecom-billrun-service
