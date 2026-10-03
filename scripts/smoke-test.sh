#!/usr/bin/env bash
# End-to-end check against a running instance: 202, progress, completion, idempotent rerun, cancellation.
set -uo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1"
PASS=0; FAIL=0
green() { printf '\033[32m%s\033[0m\n' "$*"; }
red()   { printf '\033[31m%s\033[0m\n' "$*"; }
check() { if [[ "$3" == "$2" ]]; then green "PASS  $1 ($3)"; PASS=$((PASS+1)); else red "FAIL  $1 (got $3, want $2)"; FAIL=$((FAIL+1)); fi; }
json()  { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)"; }
start() { curl -s -X POST "$API/bill-runs" -H 'Content-Type: application/json' -H 'X-User: smoke' -d "{\"cycle\":\"$1\"}"; }
wait_final() {
  for _ in $(seq 1 "${2:-600}"); do
    s=$(curl -s "$API/jobs/$1" | json "['status']")
    [[ "$s" == COMPLETED || "$s" == FAILED || "$s" == CANCELLED ]] && { echo "$s"; return; }
    sleep 1
  done; echo TIMEOUT
}

echo "Waiting for $BASE_URL/actuator/health ..."
for _ in $(seq 1 60); do curl -fs "$BASE_URL/actuator/health" >/dev/null 2>&1 && break; sleep 2; done

CYCLE="20$((50 + RANDOM % 40))-$(printf '%02d' $((RANDOM % 12 + 1)))"
echo "== Bill run for $CYCLE =="
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/bill-runs" -H 'Content-Type: application/json' -d "{\"cycle\":\"$CYCLE\"}")
check "submit returns 202" 202 "$code"
ID=$(curl -s "$API/jobs?size=1" | json "['content'][0]['id']")
check "second submit for same cycle" 409 "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/bill-runs" -H 'Content-Type: application/json' -d "{\"cycle\":\"$CYCLE\"}")"
sleep 2
curl -s "$API/jobs/$ID" | python3 -c 'import json,sys; j=json.load(sys.stdin); print(f"  progress: {j[\"status\"]} {j[\"percent\"]}%  eta={j.get(\"etaSeconds\")}s")'
check "run completes" COMPLETED "$(wait_final "$ID")"
INVOICES=$(curl -s "$API/bill-runs/$CYCLE/summary" | json "['invoices']")
echo "  invoices: $INVOICES"

echo "== Idempotent rerun =="
ID2=$(start "$CYCLE" | json "['id']")
check "rerun completes" COMPLETED "$(wait_final "$ID2")"
check "no double billing" "$INVOICES" "$(curl -s "$API/bill-runs/$CYCLE/summary" | json "['invoices']")"

echo "== Cooperative cancellation =="
CYCLE2="20$((50 + RANDOM % 40))-$(printf '%02d' $((RANDOM % 12 + 1)))"
ID3=$(start "$CYCLE2" | json "['id']")
sleep 3
check "cancel accepted" 202 "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/jobs/$ID3/cancel")"
check "job cancelled" CANCELLED "$(wait_final "$ID3" 60)"

echo; echo "Passed: $PASS  Failed: $FAIL"
[[ $FAIL -eq 0 ]]
