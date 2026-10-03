#!/usr/bin/env bash
# Job and executor metrics from Actuator.
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
show() {
  curl -s "$BASE_URL/actuator/metrics/$1${2:+?tag=$2}" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(f'  {\"$1\":<28} ${2:-}', ', '.join(f'{m[\"statistic\"]}={m[\"value\"]:.0f}' for m in d.get('measurements',[])))"
}
show telecom.jobs.active
show telecom.jobs.items outcome:billed
show telecom.jobs.items outcome:failed
show executor.active name:jobExecutor
show executor.queued name:jobExecutor
show executor.active name:taskExecutor
