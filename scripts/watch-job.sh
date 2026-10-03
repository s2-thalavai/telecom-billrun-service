#!/usr/bin/env bash
# Follow a job's SSE progress stream:  ./scripts/watch-job.sh <job-id>
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
[[ $# -eq 1 ]] || { echo "usage: $0 <job-id>"; exit 1; }
curl -sN "$BASE_URL/api/v1/jobs/$1/progress" | while IFS= read -r line; do
  [[ "$line" == data:* ]] && echo "${line#data:}" | python3 -c '
import json,sys; j=json.load(sys.stdin)
print(f"{j[\"status\"]:<9} {j[\"percent\"]:>5}%  done={j[\"processedItems\"]+j[\"failedItems\"]}/{j[\"totalItems\"]}  failed={j[\"failedItems\"]}  eta={j.get(\"etaSeconds\",\"-\")}s")'
done
