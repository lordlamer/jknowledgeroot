#!/bin/sh
# Seeds a fresh Knowledgeroot instance with the example content of the README screenshots
# through the client API (docs/client-api.md). Use a disposable instance only.
#   sh scripts/screenshots/seed.sh http://localhost:8099 admin PASSWORD
# Page ids are printed; shoot.mjs expects the ids of a fresh database (1 = handbook, 3 = review, 6 = backup).
set -eu
BASE="$1"; LOGIN="$2"; PASSWORD="$3"
API="$BASE/api/v1"
DIR="$(dirname "$0")/content"
JAR="$(mktemp)"; BODY="$(mktemp)"
trap 'rm -f "$JAR" "$BODY"' EXIT

token() { grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4; }
TOKEN=$(curl -sS -c "$JAR" -b "$JAR" "$API/session" | token)
TOKEN=$(curl -sS -c "$JAR" -b "$JAR" -H "X-XSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$LOGIN\",\"password\":\"$PASSWORD\"}" "$API/auth/login" | token)
[ -n "$TOKEN" ] || { echo "login failed" >&2; exit 1; }

page() { # parent name labels-json content-file -> prints the new id
  content=$(sed 's/\\/\\\\/g; s/"/\\"/g' "$4" | tr '\n' ' ')
  printf '{"parent":%s,"space":1,"name":"%s","content":"%s","labels":%s}' "$1" "$2" "$content" "$3" > "$BODY"
  curl -sS -c "$JAR" -b "$JAR" -H "X-XSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
    --data-binary @"$BODY" "$API/pages" | grep -o '[0-9]*' | head -1
}

HANDBOOK=$(page 0 "Engineering Handbook" '["handbook"]' "$DIR/handbook.html")
page "$HANDBOOK" "Onboarding checklist" '["handbook","onboarding"]' "$DIR/onboarding.html" > /dev/null
REVIEW=$(page "$HANDBOOK" "Code review guidelines" '["handbook","process"]' "$DIR/review.html")
page "$HANDBOOK" "Release process" '["handbook","process","release"]' "$DIR/release.html" > /dev/null
RUNBOOKS=$(page 0 "Runbooks" '["runbook"]' "$DIR/runbooks.html")
BACKUP=$(page "$RUNBOOKS" "Database backup and restore" '["runbook","database"]' "$DIR/backup.html")
page "$RUNBOOKS" "Incident response" '["runbook","on-call"]' "$DIR/incident.html" > /dev/null
ADR=$(page 0 "Architecture decisions" '["adr"]' "$DIR/adr.html")
page "$ADR" "ADR-001: MariaDB as the primary store" '["adr","database"]' "$DIR/adr1.html" > /dev/null
page "$ADR" "ADR-002: htmx instead of a SPA framework" '["adr","frontend"]' "$DIR/adr2.html" > /dev/null

curl -sS -o /dev/null -c "$JAR" -b "$JAR" -H "X-XSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"content":"Added the section on draft pull requests. Should we also mention the 400-line limit from the retro?"}' \
  "$API/pages/$REVIEW/comments"
curl -sS -o /dev/null -c "$JAR" -b "$JAR" -X PUT -H "X-XSRF-TOKEN: $TOKEN" "$API/pages/$REVIEW/star"
curl -sS -o /dev/null -c "$JAR" -b "$JAR" -X PUT -H "X-XSRF-TOKEN: $TOKEN" "$API/pages/$HANDBOOK/star"
echo "handbook=$HANDBOOK review=$REVIEW backup=$BACKUP"
echo "Create a second space (key OPS, name Operations) under Spaces → New space before shooting."
