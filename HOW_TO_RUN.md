# How to Run

## Prerequisites

JDK 21, Maven 3.9+ (or Docker only), Docker + Compose v2 for the stack and the MySQL IT, kubectl + minikube/kind for Kubernetes, and make/bash/curl/python3 for the scripts (on Windows, use WSL2 or Git Bash).

## 1. Local

```bash
make run          # H2 with 5,000 demo accounts; console at http://localhost:8080
```

A bill run takes about 100 s (5,000 accounts × 20 ms). Every 250th account fails rating on purpose, to show per-item failures.

| Try | Expect |
|---|---|
| Start a run for the last month in the console | 202 immediately; progress bar, rate and ETA update every second |
| "Watch" | SSE events with the checkpoint advancing per chunk |
| "Cancel" | Status shows "(cancelling)", then CANCELLED after the current chunk |
| "Force stop" | CANCELLED almost immediately; the current chunk is rolled back |
| Start the same cycle twice | 409 while the first is active |
| Re-run a completed cycle | COMPLETED quickly; invoice count unchanged (idempotent) |
| `make metrics` | `telecom.jobs.active`, items billed/failed, `executor.active/queued` |

## 2. Tests

```bash
make test         # unit, MockMvc, JPA
make it           # real background jobs: cancellation, queue rejection, crash recovery; MySQL IT needs Docker
make verify       # all + coverage
```

## 3. Docker Compose (MySQL, Flyway seeds 2,000 accounts)

```bash
make up && make logs          # note [job=<id>] on job-* and task-* threads
make smoke
make crash-demo               # kill -9 mid-run; after ~2-3 min the job resumes from its checkpoint
make down / make nuke
```

## 4. Kubernetes

```bash
minikube start && minikube addons enable ingress
make k8s-deploy && make k8s-port-forward
make k8s-delete
```

Two replicas can each run jobs. If a pod dies, the other resumes its jobs. `terminationGracePeriodSeconds: 90` exceeds the 60 s graceful shutdown, so running chunks can finish on rollouts.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `telecom.jobs.workers` / `queue-capacity` | 2 / 20 | Concurrent jobs / waiting jobs (beyond → FAILED "queue is full") |
| `telecom.jobs.chunk-size` | 100 | Accounts per transaction and checkpoint |
| `telecom.jobs.max-runtime` | 2h | Job fails after this |
| `telecom.jobs.heartbeat-timeout` | 2m | RUNNING job without a heartbeat for this long is resumed elsewhere |
| `telecom.jobs.recovery-interval-ms` | 60000 | Stale-job scan interval |
| `telecom.jobs.progress-push-interval-ms` | 1000 | SSE push interval |
| `telecom.billing.rating-latency` | 20ms | Simulated rating time per account |
| `telecom.billing.failing-every` | 250 (0 in prod) | Every Nth account fails rating |
| `telecom.billing.seed-accounts` | 5000 | Demo accounts (not prod) |
| `server.shutdown` / `spring.lifecycle.timeout-per-shutdown-phase` | graceful / 60s | Let chunks finish on shutdown |

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Job FAILED "queue is full" | Workers and queue are busy. Raise `workers`/`queue-capacity`, or resubmit later |
| Job stays QUEUED | All workers busy (see `executor.queued`), or the app restarted while it was queued: resubmit |
| RUNNING job stops moving | Worker died. It is resumed after `heartbeat-timeout` + recovery interval |
| Healthy job resumed twice / a resumed job runs alongside the original | A chunk takes longer than `heartbeat-timeout`. Lower `chunk-size` or raise the timeout |
| Force stop doesn't work | The job runs on another pod (interrupts are local). Cooperative cancel still applies |
| SSE progress arrives in bursts | Proxy buffering. See the ingress annotations |
