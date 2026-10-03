# Design

## Flow

```
HTTP thread                         after commit                      job-1 (jobExecutor)
───────────                         ────────────                      ───────────────────
POST /bill-runs
 JobService.submitBillRun  @Tx
   insert job QUEUED
   publish JobSubmittedEvent
 commit ───────────────────────────▶ JobLauncher.onSubmitted
 202 + Location                       worker.run(id)  ── submit ────▶ markRunning (QUEUED→RUNNING, CAS)
                                      keep Future (for force cancel)  loop:
                                                                        cancel flag? interrupted? deadline?
                                                                        nextAccounts(checkpoint)   (keyset)
                                                                        billChunk           @Tx (one chunk)
                                                                        recordProgress      @Tx REQUIRES_NEW
                                                                        notifyChunkBilled   @Async (task-*)
                                                                      finish (COMPLETED/FAILED/CANCELLED, CAS)
```

## Pools

| Bean | Threads / queue | Purpose |
|---|---|---|
| `jobExecutor` | `telecom.jobs.workers` / `queue-capacity` (2 / 20) | Bill runs. Bounded; rejection → job FAILED |
| `taskExecutor` | 4–16 / 500 | Short `@Async` work (notifications); default executor |
| `taskScheduler` | 2 | Progress pushes, stale-job recovery, launcher housekeeping |

Each pool is bounded, copies the MDC (`MdcTaskDecorator`), and waits up to 60 s for tasks on shutdown.

## Transactions

| Rule | Implementation |
|---|---|
| Worker starts only after the job row is committed | `@TransactionalEventListener(AFTER_COMMIT)` in `JobLauncher` |
| No transaction around the long method | `BillRunWorker` is non-transactional |
| One transaction per chunk | `BillingService.billChunk` |
| Job-state writes commit independently | `JobStateService` with `REQUIRES_NEW` (also required inside the AFTER_COMMIT listener) |
| Side effects only after commit | Notifications are sent after `billChunk` returns |

## State machine

```
QUEUED ──markRunning──▶ RUNNING ──▶ COMPLETED | FAILED | CANCELLED
  │  └─cancel──▶ CANCELLED            ▲
  └────────────── requeueIfStale ─────┘ (RUNNING with stale heartbeat → QUEUED)
```

Every transition is a conditional `UPDATE … WHERE status IN (…)`. Concurrent workers, cancellations and recovery on several pods can't produce two owners or two final states.

## Idempotency and resume

- The **checkpoint** is the last account handled; it doubles as the keyset cursor.
- `invoices` has a **unique (account_id, cycle)** constraint, and `billChunk` skips accounts already invoiced. A resumed or re-run job never double-bills, even if a chunk committed but its progress update didn't.
- A **rerun** of a completed cycle is allowed and skips everything already invoiced.

## Cancellation

| Mode | Mechanism | Effect |
|---|---|---|
| Cooperative (default) | `cancel_requested` flag, checked between chunks | Current chunk finishes; works from any pod |
| Forced (`force=true`) | Flag plus `Future.cancel(true)` → interrupt | `Thread.sleep`/I/O stops; current chunk rolls back; only on the pod running the job |
| Queued job | Flag + immediate CANCELLED; `markRunning` refuses it | Never starts |

`BillRunWorker.run` returns **`Future`**, not `CompletableFuture`. With a `Future` return type, Spring submits a `FutureTask`, whose `cancel(true)` interrupts the thread. `CompletableFuture.cancel(true)` does not.

## One active run per cycle

`JobService` checks for an active run (fast path, 409). Two concurrent submissions can still race past the check. In production, also add a database constraint, for example in MySQL:

```sql
ALTER TABLE background_jobs
  ADD active_key VARCHAR(90) AS (CASE WHEN status IN ('QUEUED','RUNNING') THEN CONCAT(type, ':', parameters) END) STORED,
  ADD UNIQUE KEY uk_jobs_active (active_key);
```

## Limitations and when to graduate

- Executor queues live in memory. Queued jobs are recovered only if they were RUNNING (heartbeat). A job that was still QUEUED during a crash stays QUEUED until resubmitted. Extend `StaleJobRecovery` to relaunch old QUEUED jobs if needed.
- Forced cancellation only reaches the pod running the job. Cooperative cancellation works everywhere.
- `@Scheduled` recovery runs on every pod. It's correct thanks to CAS updates; add ShedLock to avoid redundant scans.
- For partitioned, distributed or very large batch workloads, use Spring Batch (remote partitioning), JobRunr/Quartz, or a message queue.
