#!/usr/bin/env bash
# Shows resume-after-crash with Docker Compose: start a run, kill the app hard, restart, watch it resume.
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1"
CYCLE="${CYCLE:-2026-$(printf '%02d' $((RANDOM % 12 + 1)))}"
ID=$(curl -s -X POST "$API/bill-runs" -H 'Content-Type: application/json' -d "{\"cycle\":\"$CYCLE\"}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])')
echo "Started job $ID for $CYCLE; letting it run 8s..."
sleep 8
curl -s "$API/jobs/$ID" | python3 -c 'import json,sys;j=json.load(sys.stdin);print("before crash:",j["status"],j["percent"],"% checkpoint",j.get("checkpoint"))'
echo "kill -9 (no graceful shutdown)..."
docker kill --signal=KILL billrun-app >/dev/null
docker start billrun-app >/dev/null
echo "Restarted. The job stays RUNNING with a stale heartbeat until StaleJobRecovery requeues it"
echo "(heartbeat-timeout 2m + recovery interval 1m). Watch it with: ./scripts/watch-job.sh $ID"
