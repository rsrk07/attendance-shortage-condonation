#!/usr/bin/env bash
# End-to-end test against the running stack and the real Camunda 8 cluster.
# Runs six scenarios through the REST API (the same calls the React app makes):
#   1. happy path: FA, AA, HOD approve, fee paid, SIS updated, condoned
#   2. DMN rejection (attendance too low)
#   3. document rejected by the validate-document worker (DOC_INVALID)
#   4. Faculty Advisor rejects with a remark
#   5. fee underpaid -> verify-payment worker throws PAYMENT_INVALID
#   6. simulated SIS failure -> admin fixes it -> retried -> condoned
#
# It needs a fresh database (no requests yet): docker compose down -v && docker compose up -d
# Run it from the repository root while `docker compose up` is running:
#   docker run --rm --network host -v "$PWD/scripts:/scripts" alpine:3.20 \
#     sh -c "apk add -q bash curl jq && bash /scripts/e2e-test.sh"
set -uo pipefail

API=${API:-http://localhost:3000/api}
PASSWORD=${DEMO_PASSWORD:-demo123}
TIMEOUT=${TIMEOUT:-180}
PASSED=0
FAILED=0

pass() { echo "  PASS  $1"; PASSED=$((PASSED + 1)); }
fail() { echo "  FAIL  $1"; FAILED=$((FAILED + 1)); }
check() { if [ "$2" = "$3" ]; then pass "$1 ($2)"; else fail "$1: expected '$3', got '$2'"; fi; }

login() {
  curl -sf -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$PASSWORD\"}" "$API/auth/login" | jq -r .token
}
get() { curl -sf -H "Authorization: Bearer $1" "$API$2"; }
post() { curl -s -o /tmp/resp -w '%{http_code}' -X "${4:-POST}" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d "$3" "$API$2"; }
upload() { curl -sf -H "Authorization: Bearer $1" -F "file=@$2" "$API/files" | jq -r .fileId; }

submit() { # token course category fileId -> requestId
  local body
  body=$(jq -nc --arg c "$2" --arg r "$3" --arg f "$4" \
    '{courseCode:$c, reasonCategory:$r, reason:"End-to-end test: absent because of a documented reason for two weeks.", documents:[{type:"MEDICAL_CERTIFICATE", fileId:$f, description:"e2e"}]}')
  local code
  code=$(post "$1" /requests "$body")
  if [ "$code" != 201 ]; then echo "submit failed ($code): $(cat /tmp/resp)" >&2; return 1; fi
  jq -r .requestId /tmp/resp
}

wait_task() { # token requestId kind -> taskKey
  local start=$SECONDS key
  while [ $((SECONDS - start)) -lt "$TIMEOUT" ]; do
    key=$(get "$1" /tasks | jq -r --arg r "$2" --arg k "$3" '[.[] | select(.requestId==$r and .kind==$k)][0].taskKey // empty')
    if [ -n "$key" ]; then echo "$key"; return 0; fi
    sleep 2
  done
  return 1
}

wait_final() { # token requestId -> final status JSON in /tmp/status
  local start=$SECONDS st
  while [ $((SECONDS - start)) -lt "$TIMEOUT" ]; do
    get "$1" "/requests/$2/status" > /tmp/status 2>/dev/null
    st=$(jq -r '.status + "/" + .processState' /tmp/status 2>/dev/null)
    case "$st" in APPROVED/COMPLETED|REJECTED/COMPLETED) return 0 ;; esac
    sleep 3
  done
  return 1
}

review() { # token taskKey decision remarks
  post "$1" "/tasks/$2/review" "$(jq -nc --arg d "$3" --arg m "$4" '{decision:$d, remarks:$m}')"
}

approve_all() { # requestId
  for who in fa aa hod; do
    local tok_var=${who^^}
    local tok=${!tok_var}
    local t
    if t=$(wait_task "$tok" "$1" REVIEW); then
      check "$who review task completed" "$(review "$tok" "$t" APPROVED "Checked the documents - approved by $who")" 204
    else
      fail "$who review task did not appear"; return 1
    fi
  done
}

pay() { # studentToken requestId amount
  local t
  if t=$(wait_task "$1" "$2" PAY_FEE); then
    local ref="UPI$(date +%s)$RANDOM"
    check "pay-fee task completed" \
      "$(post "$1" "/tasks/$t/pay-fee" "$(jq -nc --arg r "${ref:0:20}" --argjson a "$3" '{paymentMode:"UPI", paymentReference:$r, amountPaid:$a, paymentConfirmed:true}')")" 204
  else
    fail "pay-fee task did not appear"; return 1
  fi
}

outcome() { # label expectedStatus expectedStage
  local status stage
  status=$(jq -r .status /tmp/status); stage=$(jq -r '.request.rejectionStage // "-"' /tmp/status)
  check "$1: final status" "$status" "$2"
  [ -n "${3:-}" ] && check "$1: rejection stage" "$stage" "$3"
  echo "        route slip: $(jq -r '[.steps[] | .key + "=" + .state] | join("  ")' /tmp/status)"
  local reason; reason=$(jq -r '.request.rejectionReason // empty' /tmp/status)
  [ -n "$reason" ] && echo "        reason: $reason"
  return 0
}

# ---------------------------------------------------------------- setup
printf '%%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%%%EOF\n' > /tmp/medical-certificate.pdf
printf 'This is a text file pretending to be a PDF.\n' > /tmp/fake-certificate.pdf

echo "API: $API"
FA=$(login fa); AA=$(login aa); HOD=$(login hod); ADMIN=$(login admin)
S1=$(login RA2311004010101); S2=$(login RA2311004010102); S3=$(login RA2311004010103); S4=$(login RA2311004010104)
for v in FA AA HOD ADMIN S1 S2 S3 S4; do
  if [ -z "${!v}" ] || [ "${!v}" = null ]; then echo "Login failed for $v - is the app running?"; exit 1; fi
done
post "$ADMIN" /admin/settings '{"simulateSisFailure":false}' PUT > /dev/null
if [ "$(get "$S3" /requests | jq length)" != 0 ] || [ "$(get "$S1" /requests | jq length)" != 0 ]; then
  echo "The demo students already have requests. The end-to-end test needs a fresh database:"
  echo "  docker compose down -v && docker compose up -d     (then wait ~30 s and run the test again)"
  exit 1
fi

# ---------------------------------------------------------------- 1
echo; echo "1. Happy path: RA2311004010103, 21ECC302J (69.44%), medical"
F=$(upload "$S3" /tmp/medical-certificate.pdf)
if R=$(submit "$S3" 21ECC302J MEDICAL "$F"); then
  echo "        request $R"
  if approve_all "$R" && pay "$S3" "$R" 1000 && wait_final "$S3" "$R"; then
    outcome "happy path" APPROVED
    check "slab" "$(jq -r .request.slab /tmp/status)" SLAB_B
    check "fee" "$(jq -r .request.fee /tmp/status)" 1000
    check "document verified" "$(jq -r '.request.documents[0].verificationStatus' /tmp/status)" VERIFIED
    check "approvals recorded" "$(jq -r '[.request.approvals[].role] | join(",")' /tmp/status)" "FA,AA,HOD"
    check "SIS record written" "$(get "$ADMIN" /admin/sis-records | jq -r --arg r "$R" '[.[] | select(.requestId==$r)] | length')" 1
  else fail "happy path did not finish"; fi
else fail "happy path request was not submitted"; fi

# ---------------------------------------------------------------- 2
echo; echo "2. DMN rejection: RA2311004010104, 21ECC303T (41.67%)"
F=$(upload "$S4" /tmp/medical-certificate.pdf)
if R=$(submit "$S4" 21ECC303T MEDICAL "$F") && wait_final "$S4" "$R"; then
  outcome "not eligible" REJECTED ELIGIBILITY
  check "slab" "$(jq -r .request.slab /tmp/status)" NOT_ELIGIBLE
else fail "DMN rejection scenario did not finish"; fi

# ---------------------------------------------------------------- 3
echo; echo "3. Fake document: RA2311004010102, 21ECC301T (62.50%), text file renamed to .pdf"
F=$(upload "$S2" /tmp/fake-certificate.pdf)
if R=$(submit "$S2" 21ECC301T MEDICAL "$F") && wait_final "$S2" "$R"; then
  outcome "document rejected" REJECTED DOCUMENTS
  check "document marked rejected" "$(jq -r '.request.documents[0].verificationStatus' /tmp/status)" REJECTED
  check "worker's note reaches the student" "$(jq -r '.request.rejectionReason | contains("not a real PDF")' /tmp/status)" true
else fail "document rejection scenario did not finish"; fi

# ---------------------------------------------------------------- 4
echo; echo "4. FA rejects: RA2311004010103, 21ECC303T (68.06%), family emergency"
F=$(upload "$S3" /tmp/medical-certificate.pdf)
if R=$(submit "$S3" 21ECC303T FAMILY_EMERGENCY "$F") && T=$(wait_task "$FA" "$R" REVIEW); then
  check "FA rejection saved" "$(review "$FA" "$T" REJECTED "The letter does not cover the absence dates")" 204
  if wait_final "$S3" "$R"; then outcome "FA rejected" REJECTED FA_APPROVAL; else fail "FA rejection did not finish"; fi
else fail "FA rejection scenario did not reach the FA"; fi

# ---------------------------------------------------------------- 5
echo; echo "5. Underpaid fee: RA2311004010101, 21ECC302J (65.28%), medical, pays Rs. 100 of 1000"
F=$(upload "$S1" /tmp/medical-certificate.pdf)
if R=$(submit "$S1" 21ECC302J MEDICAL "$F") && approve_all "$R" && pay "$S1" "$R" 100 && wait_final "$S1" "$R"; then
  outcome "payment rejected" REJECTED FEE
  check "worker's note reaches the student" "$(jq -r '.request.rejectionReason | contains("less than the fee")' /tmp/status)" true
else fail "underpayment scenario did not finish"; fi

# ---------------------------------------------------------------- 6
echo; echo "6. SIS failure and admin fix: RA2311004010101, 21ECC301T (72.22%), personal reason"
post "$ADMIN" /admin/settings '{"simulateSisFailure":true}' PUT > /dev/null
F=$(upload "$S1" /tmp/medical-certificate.pdf)
R=$(submit "$S1" 21ECC301T PERSONAL "$F"); SUBMITTED=$?
post "$ADMIN" /admin/settings '{"simulateSisFailure":false}' PUT > /dev/null
if [ $SUBMITTED -eq 0 ] && approve_all "$R" && pay "$S1" "$R" 500; then
  if T=$(wait_task "$ADMIN" "$R" ADMIN_FIX); then
    pass "SIS failure reached the admin inbox"
    check "admin fix completed" \
      "$(post "$ADMIN" "/tasks/$T/admin-fix" '{"adminNote":"Unlocked the course record","sisRecordFixed":true}')" 204
    if wait_final "$S1" "$R"; then
      outcome "SIS fixed" APPROVED
      check "slab" "$(jq -r .request.slab /tmp/status)" SLAB_A
    else fail "SIS retry did not finish"; fi
  else fail "admin fix task did not appear"; fi
else fail "SIS scenario did not reach the SIS step"; fi

echo; echo "Passed: $PASSED  Failed: $FAILED"
[ "$FAILED" -eq 0 ]
