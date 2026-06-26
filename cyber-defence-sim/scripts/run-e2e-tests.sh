#!/usr/bin/env sh
set -eu
curl -s -X POST http://localhost:8105/simulations \
  -H "Content-Type: application/json" \
  -d '{"name":"Baseline Web Application Defence Simulation","mode":"INTERNAL_SANDBOX","targetId":"00000000-0000-0000-0000-000000000101","maxRounds":5,"maxDurationMinutes":60,"stopWhenNoNewFindingsForRounds":2}'
