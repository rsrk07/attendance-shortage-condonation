#!/usr/bin/env bash
# Starts the backend jar without a real Camunda connection and checks the REST API end to end
# (login, seeded data, validation, upload, authorization). Camunda calls are expected to fail (503).
set -euo pipefail
API=http://localhost:8080/api
pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; exit 1; }
code() { curl -s -o /tmp/body -w '%{http_code}' "$@"; }

for i in $(seq 1 90); do
  if curl -sf "$API/auth/demo-users" > /dev/null; then break; fi
  sleep 2
done
curl -sf "$API/auth/demo-users" > /tmp/users || fail "app did not start"
[ "$(jq length /tmp/users)" -eq 8 ] && pass "8 demo users seeded" || fail "demo users: $(cat /tmp/users)"

[ "$(code "$API/requests")" = 401 ] && pass "no token -> 401" || fail "expected 401"
[ "$(code -H 'Content-Type: application/json' -d '{"username":"fa","password":"wrong"}' "$API/auth/login")" = 401 ] \
  && pass "wrong password -> 401" || fail "expected 401 for wrong password"

login() { curl -sf -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"demo123\"}" "$API/auth/login" | jq -r .token; }
ST=$(login RA2311004010101); FA=$(login fa)
[ -n "$ST" ] && [ "$ST" != null ] && pass "student login" || fail "student login"

curl -sf -H "Authorization: Bearer $ST" "$API/meta/my-courses" > /tmp/courses
jq -e '.[] | select(.courseCode=="21ECC302J" and .percentage==65.28 and .shortage==true)' /tmp/courses > /dev/null \
  && pass "courses with attendance % (21ECC302J = 65.28%)" || fail "courses: $(cat /tmp/courses)"
curl -sf -H "Authorization: Bearer $ST" "$API/meta/options" | jq -e '.reasonCategories | length == 5' > /dev/null \
  && pass "meta options" || fail "meta options"

[ "$(code -H "Authorization: Bearer $FA" "$API/meta/my-courses")" = 403 ] && pass "FA cannot use student endpoint -> 403" || fail "expected 403"

printf '%%PDF-1.4\n%% smoke test\n' > /tmp/cert.pdf
printf 'not an image' > /tmp/fake.docx
[ "$(code -H "Authorization: Bearer $ST" -F file=@/tmp/fake.docx "$API/files")" = 400 ] \
  && pass "upload of .docx rejected -> 400" || fail "expected 400 for docx: $(cat /tmp/body)"
FILE=$(curl -sf -H "Authorization: Bearer $ST" -F file=@/tmp/cert.pdf "$API/files" | jq -r .fileId)
[ -n "$FILE" ] && [ "$FILE" != null ] && pass "PDF uploaded ($FILE)" || fail "upload"
[ "$(code -H "Authorization: Bearer $FA" "$API/files/$FILE")" = 200 ] && pass "FA can open the document" || fail "FA download"

[ "$(code -H "Authorization: Bearer $ST" -H 'Content-Type: application/json' \
  -d '{"courseCode":"21ECC302J","reasonCategory":"MEDICAL","reason":"short","documents":[]}' "$API/requests")" = 400 ] \
  && pass "invalid request -> 400 ($(jq -c .fieldErrors /tmp/body))" || fail "expected 400: $(cat /tmp/body)"
[ "$(code -H "Authorization: Bearer $ST" -H 'Content-Type: application/json' \
  -d "{\"courseCode\":\"21ECC303T\",\"reasonCategory\":\"MEDICAL\",\"reason\":\"Hospitalised with fever for two weeks\",\"documents\":[{\"type\":\"MEDICAL_CERTIFICATE\",\"fileId\":\"$FILE\"}]}" "$API/requests")" = 400 ] \
  && pass "course with 91.67% -> 400 ($(jq -r .message /tmp/body))" || fail "expected 400 for >=75%: $(cat /tmp/body)"

STATUS=$(code -H "Authorization: Bearer $ST" -H 'Content-Type: application/json' \
  -d "{\"courseCode\":\"21ECC302J\",\"reasonCategory\":\"MEDICAL\",\"reason\":\"Hospitalised with fever for two weeks\",\"documents\":[{\"type\":\"MEDICAL_CERTIFICATE\",\"fileId\":\"$FILE\",\"description\":\"Hospital certificate\"}]}" "$API/requests")
[ "$STATUS" = 503 ] && pass "valid request without Camunda -> 503 ($(jq -r .message /tmp/body))" || fail "expected 503, got $STATUS: $(cat /tmp/body)"
[ "$(curl -sf -H "Authorization: Bearer $ST" "$API/requests" | jq length)" = 0 ] \
  && pass "failed start rolled back (no orphan request)" || fail "request not rolled back"

[ "$(code -H "Authorization: Bearer $ST" "$API/admin/settings")" = 403 ] && pass "student cannot open admin settings" || fail "expected 403"
AD=$(login admin)
curl -sf -X PUT -H "Authorization: Bearer $AD" -H 'Content-Type: application/json' -d '{"simulateSisFailure":true}' "$API/admin/settings" \
  | jq -e '.simulateSisFailure == true' > /dev/null && pass "admin toggles SIS failure" || fail "admin settings"

echo "All smoke checks passed."
