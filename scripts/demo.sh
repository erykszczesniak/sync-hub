#!/usr/bin/env bash
# The scripted demo: normal sync → an incremental change → a schema-drift event quarantined →
# the fix, a backfill and automatic resolution. Runs against a live stack (docker compose up).
#
#   HUB_URL=http://localhost:8080 SYSTEM_A_URL=http://localhost:8081 ./scripts/demo.sh
#
# Requires curl and python3. Exits non-zero if any step does not produce the expected outcome,
# which is what makes it usable as a smoke test in CI.
set -euo pipefail

HUB_URL="${HUB_URL:-http://localhost:8080}"
SYSTEM_A_URL="${SYSTEM_A_URL:-http://localhost:8081}"
API_KEY="${SYSTEM_A_API_KEY:-system-a-dev-key}"
ADMIN="${SYNCHUB_ADMIN_USERNAME:-admin}:${SYNCHUB_ADMIN_PASSWORD:-change-me}"

step() { printf '\n\033[1m== %s\033[0m\n' "$1"; }
json() { python3 -c "import sys, json; d = json.load(sys.stdin); print($1)"; }
hub_post() { curl -fsS -u "$ADMIN" -X POST -H "Content-Type: application/json" "$HUB_URL$1" ${2:+-d "$2"}; }
hub_get() { curl -fsS "$HUB_URL$1"; }
sysa() { curl -fsS -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" -X "$1" "$SYSTEM_A_URL$2" ${3:+-d "$3"}; }
expect() { # expect <actual> <expected> <label>
  if [ "$1" != "$2" ]; then printf '\033[31mFAIL: %s: expected %s, got %s\033[0m\n' "$3" "$2" "$1"; exit 1; fi
  printf '  ok: %s = %s\n' "$3" "$1"
}

step "0. Reset System A to the seed data, no drift active; close leftovers in the hub from earlier demos"
sysa POST /admin/reset | json "f\"{d['customers']} customers, {d['orders']} orders in System A\""
for id in $(hub_get "/api/status/drift?status=OPEN&size=200" | json "' '.join(e['id'] for e in d['content'])"); do
  hub_post "/api/drift/$id/resolve" '{"note":"closed by demo reset"}' > /dev/null
done
for id in $(hub_get "/api/status/quarantine?status=OPEN&size=200" | json "' '.join(e['id'] for e in d['content'])"); do
  hub_post "/api/quarantine/$id/discard" '{"note":"discarded by demo reset"}' > /dev/null
done
echo "  hub: open drift events and quarantined records from earlier demos closed"

step "1. Normal sync: both feeds, incremental (a first run reads everything)"
for feed in customers orders; do
  hub_post "/api/sync/$feed/run" | json "f\"  {d['feed']}: {d['status']} extracted={d['extracted']} loaded={d['loaded']} skipped={d['skipped']} quarantined={d['quarantined']} watermark={d['watermarkAfter']}\""
done
health=$(hub_get /api/status/feeds/customers | json "d['health']")
expect "$health" HEALTHY "customers feed health"

step "2. Re-run with nothing changed: idempotent, nothing loaded, nothing duplicated"
loaded=$(hub_post /api/sync/customers/run | json "d['loaded']")
expect "$loaded" 0 "records loaded on a no-change run"

step "3. An incremental change: rename one customer in System A, then sync"
sysa PATCH /admin/customers/cus_00001 '{"firstName":"Renamed","tags":["vip","demo"]}' | json "f\"  System A: {d['id']} firstName={d['firstName']} updatedAt={d['updatedAt']}\""
run=$(hub_post /api/sync/customers/run)
echo "$run" | json "f\"  hub: {d['status']} extracted={d['extracted']} loaded={d['loaded']} skipped={d['skipped']}\""
expect "$(echo "$run" | json "d['loaded']")" 1 "records loaded after one change"

step "4. Schema drift: System A renames 'email' to 'emailAddress' and touches a few customers"
sysa PUT /admin/drift '{"scenarios":["RENAME_EMAIL"]}' | json "f\"  active drift scenarios: {d['active']}\""
sysa POST "/admin/activity?customers=3&orders=0&seed=7" | json "f\"  touched customers: {d['customersUpdated']}\""
run=$(hub_post /api/sync/customers/run)
echo "$run" | json "f\"  hub: {d['status']} extracted={d['extracted']} loaded={d['loaded']} quarantined={d['quarantined']} driftDetected={d['driftDetected']}\""
expect "$(echo "$run" | json "d['status']")" PARTIAL "run status under blocking drift"
# At least the 3 touched customers; the watermark overlap may re-read the customer from step 3 too.
quarantined=$(echo "$run" | json "d['quarantined']")
[ "$quarantined" -ge 3 ] || { echo "FAIL: expected at least 3 quarantined records, got $quarantined"; exit 1; }
echo "  ok: records quarantined = $quarantined"
hub_get "/api/status/drift?status=OPEN" | json "'  drift events: ' + '; '.join(f\"{e['kind']} {e['field']} -> {e['actual']} ({e['affectedRecords']} records, {e['severity']})\" for e in d['content'])"
expect "$(hub_get /api/status/feeds/customers | json "d['health']")" DRIFT "customers feed health"
echo "  dashboard: open Drift & quarantine to see the event and the raw payloads"

step "5. The fix: System A rolls the rename back; a backfill of the window re-syncs the quarantined records"
sysa PUT /admin/drift '{"scenarios":[]}' | json "f\"  active drift scenarios: {d['active']}\""
from=$(python3 -c "import datetime; print((datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(minutes=10)).isoformat().replace('+00:00','Z'))")
to=$(python3 -c "import datetime; print(datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'))")
run=$(hub_post /api/sync/customers/backfill "{\"from\":\"$from\",\"to\":\"$to\"}")
echo "$run" | json "f\"  backfill: {d['status']} extracted={d['extracted']} loaded={d['loaded']} skipped={d['skipped']} quarantined={d['quarantined']}\""
expect "$(echo "$run" | json "d['status']")" SUCCEEDED "backfill status"
expect "$(hub_get '/api/status/quarantine?status=OPEN' | json "d['totalElements']")" 0 "open quarantined records"
expect "$(hub_get '/api/status/drift?status=OPEN' | json "d['totalElements']")" 0 "open drift events"
hub_get "/api/status/drift?status=RESOLVED" | json "'  resolved: ' + '; '.join(f\"{e['kind']} {e['field']} — {e['resolutionNote']} (time to resolve {e['timeToResolveSeconds']}s)\" for e in d['content'])"

step "6. Where it all shows up"
hub_get /api/status/overview | json "f\"  runs={d['totals']['runs']} loaded={d['totals']['loaded']} skipped={d['totals']['skipped']} quarantined={d['totals']['quarantined']} MTTD={d['mttdSeconds']}s MTTR={d['mttrSeconds']}s change events published={d['outboxPublished']} pending={d['outboxPending']}\""
echo "  dashboard: ${DASHBOARD_URL:-http://localhost:5173}   swagger: $HUB_URL/swagger-ui.html"
printf '\n\033[32mDemo scenario completed.\033[0m\n'
