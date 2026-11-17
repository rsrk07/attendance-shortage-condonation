#!/usr/bin/env bash
# Rebuilds the stack on a fresh database, runs the end-to-end test, captures the report screenshots
# and saves the logs under docs/evidence. Run from the repository root in the Codespace.
set -uo pipefail
mkdir -p docs/evidence docs/screenshots
docker compose down -v --remove-orphans
docker compose up --build -d
echo "Waiting for the backend..."; sleep 60
docker run --rm --network host -v "$PWD/scripts:/scripts" alpine:3.20 \
  sh -c "apk add -q bash curl jq && bash /scripts/e2e-test.sh" 2>&1 | tee docs/evidence/e2e-test-output.txt
docker run --rm --network host -v "$PWD:/work" -w /work/docs/capture node:22-bookworm \
  bash -c "npm i --silent --no-save --no-package-lock playwright@1 >/dev/null 2>&1 && npx playwright install --with-deps chromium >/dev/null 2>&1 && node capture.mjs" 2>&1 | tee docs/evidence/capture-output.txt
docker compose ps > docs/evidence/docker-compose-ps.txt
docker compose logs --no-color backend 2>&1 \
  | grep -E "Started Condonation|Seed data|Deployed to Camunda|  process |Started process instance|document .* verified|Document rejected|payment .* verified|payment rejected|SIS updated|SIS rejected|outcome .* recorded|APPROVED task|REJECTED task" \
  | sed -E 's/^backend-1 +\| //' > docs/evidence/backend-log.txt
rm -rf docs/capture/node_modules
echo "Evidence saved under docs/evidence and docs/screenshots"
