# Cyber Defence Simulation Platform

Production-oriented Java/Spring Boot monorepo for a safe multi-agent cyber defence simulation platform.

This repository starts the SRS implementation with a working vertical slice:

- Identity service with PostgreSQL-backed users, signed JWT login, key IDs, rotation support, and role-based access control.
- API gateway with JWT validation, route-level role enforcement, and allowlisted backend forwarding.
- Target registry with downstream JWT/RBAC, a built-in sandbox target, and external target safety checks.
- Policy engine with default-deny evaluation.
- Built-in sandbox target with intentionally vulnerable mock endpoints and safe patch endpoints.
- Simulation orchestrator with user JWT/RBAC, token-protected worker operations, and a durable round workflow.
- Red-team and blue-team agent service APIs.
- Vulnerability, detection, remediation, verification, scoring, event-log, dashboard, notification, and reporting APIs.
- Docker Compose infrastructure for PostgreSQL, Redis, RabbitMQ, Prometheus, and Grafana.

## Safety Boundaries

The platform is a simulation system. Red-team behavior is limited to harmless, scoped checks against the built-in sandbox or explicitly verified staging targets. It does not implement malware, credential theft, persistence, destructive payloads, unrestricted scanning, shell execution, or denial-of-service logic.

When the policy engine cannot prove an action is safe and scoped, it denies the action.

## Run Locally

Prerequisites:

- Docker and Docker Compose.
- Maven 3.9+ and Java 21+ if building outside Docker.

Create local environment values:

```bash
cp .env.example .env
```

The example values are for local development only. Change `POSTGRES_PASSWORD`, `JWT_SECRET`, all `DEMO_*_PASSWORD` values, and `GRAFANA_ADMIN_PASSWORD` in `.env` before using the platform beyond a local demo. Set `JWT_ACTIVE_KEY_ID` to a label for the current signing key. Leave `JWT_PREVIOUS_SECRET` empty unless rotating keys.

Start everything:

```bash
docker compose up --build
```

Flyway automatically creates the identity, target-registry, simulation-orchestrator, vulnerability-registry, detection-engine, remediation, verification, scoring, and append-only event-log schemas. It seeds the built-in sandbox target, six baseline findings, six safe detection rules, three linked detection events, and six remediation proposals. The identity service seeds three demo accounts on startup only when they are missing. Verification and scoring records are created only from real workflow requests, so none are falsely pre-seeded. PostgreSQL files are retained in the `postgres-data` Docker volume across normal container recreation.

Useful endpoints after startup:

- API gateway: `http://localhost:8080/`
- Identity login: `POST http://localhost:8101/auth/login`
- Target registry: `http://localhost:8102/targets`
- Policy engine: `http://localhost:8103/policies`
- Sandbox target: `http://localhost:8104/demo/admin/report`
- Default scenario: `http://localhost:8105/scenarios/default`
- Detection rules: `http://localhost:8110/detection-rules`
- Simulation detections: `http://localhost:8110/simulations/00000000-0000-0000-0000-000000000201/detections`
- Simulation remediations: `http://localhost:8111/simulations/00000000-0000-0000-0000-000000000201/remediations`
- Simulation verifications: `http://localhost:8112/simulations/00000000-0000-0000-0000-000000000201/verifications`
- Simulation scores: `http://localhost:8113/simulations/00000000-0000-0000-0000-000000000201/scores`
- Score events: `http://localhost:8113/simulations/00000000-0000-0000-0000-000000000201/score-events`
- Dashboard overview: `http://localhost:8115/dashboard/simulations/00000000-0000-0000-0000-000000000201/overview`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

## Demo Flow

Obtain a 15-minute signed bearer token with one local development account:

```bash
curl -X POST http://localhost:8101/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo-operator","password":"local-operator-change-me"}'
```

The built-in users have separate `ADMIN`, `SIMULATION_OPERATOR`, and `AUDITOR` roles. They are seeded into PostgreSQL only when missing, and their passwords are stored as BCrypt hashes. New tokens include the `JWT_ACTIVE_KEY_ID` value in the JWT `kid` header. `/auth/me` requires any valid bearer token, while `/auth/users` and `/auth/users/{username}/enable|disable|password` require `ADMIN`. Override every demo password through `.env`; defaults are local-only.

The gateway validates the same issuer, signature, and expiry as identity. During signing-key rotation, user-token validators accept `JWT_SECRET` and `JWT_PREVIOUS_SECRET`; new tokens are signed only with `JWT_SECRET`. All roles may read `/api/**`; only `ADMIN` and `SIMULATION_OPERATOR` may use write methods; `/api/admin/**` requires `ADMIN`. It forwards only four fixed route families: `/api/auth/**`, `/api/targets/**`, `/api/simulations/**`, and `/api/dashboard/**`. The gateway preserves bearer, content, idempotency, and correlation headers but deliberately strips `X-Service-Token` so a user cannot impersonate an internal worker.

Rotate the user JWT signing secret:

1. Set `JWT_PREVIOUS_SECRET` to the old `JWT_SECRET`.
2. Set `JWT_SECRET` to the new 32+ byte secret.
3. Set `JWT_ACTIVE_KEY_ID` to a new label, such as `local-2026-07`.
4. Restart identity, gateway, target registry, simulation orchestrator, and dashboard services.
5. Wait longer than `JWT_ACCESS_TOKEN_LIFETIME`.
6. Clear `JWT_PREVIOUS_SECRET` and restart those services again.

Use the access token returned by gateway login for subsequent requests:

```bash
curl http://localhost:8080/api/targets \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Create a running demo simulation and its first durable round:

```bash
curl -X POST http://localhost:8105/simulations \
  -H "Content-Type: application/json" \
  -d '{"name":"Baseline Web Application Defence Simulation","mode":"INTERNAL_SANDBOX","targetId":"00000000-0000-0000-0000-000000000101","maxRounds":5,"maxDurationMinutes":60,"stopWhenNoNewFindingsForRounds":2}'
```

Review demo findings:

```bash
curl http://localhost:8109/vulnerabilities
```

List a simulation's durable rounds using the ID returned by the create request:

```bash
curl http://localhost:8105/simulations/{simulationId}/rounds
```

Round stage advancement, completion, patch operations, verification synchronization, and scoring submission are internal operations protected by two-minute, audience-restricted service JWTs with narrowly scoped machine roles. Every committed transition creates a transactional outbox event. Event-log, red-team, detection, remediation, verification, and scoring workers use separate durable queues, PostgreSQL inboxes, deterministic IDs, bounded retries, and dead-letter queues. The scoring worker derives fixed-rule score events and completes the round through the stop-condition engine.

Run the complete Compose workflow check from PowerShell after the services are healthy:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-e2e-tests.ps1
```

The check creates a one-round sandbox simulation and requires six findings, six detections, six verified remediations, six passed verifications, 36 score events, zero residual risk, and a completed simulation.

Run the opt-in real PostgreSQL identity integration check from PowerShell when Docker is running:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-postgres-integration-tests.ps1
```

This free local check starts the Compose PostgreSQL service, creates a disposable `cybersim_it` database and `cybersim_it` role, then runs the identity authentication lifecycle test from the free Maven Docker image on the Compose network. The default Maven test suite still uses H2 for fast feedback.

Evaluate a safe red-team action:

```bash
curl -X POST http://localhost:8103/policies/evaluate-action \
  -H "Content-Type: application/json" \
  -d '{"simulationId":"00000000-0000-0000-0000-000000000201","targetId":"00000000-0000-0000-0000-000000000101","agentId":"00000000-0000-0000-0000-000000000301","actionType":"SIMULATED_AUTH_REQUIRED_CHECK","host":"target-system-service","path":"/demo/admin/report","httpMethod":"GET"}'
```

## External Targets

External targets start disabled or pending verification. A target must:

- Be registered as `EXTERNAL_STAGING_TARGET`.
- Confirm written authorization.
- Avoid `PRODUCTION` environment type.
- Provide allowed hosts, paths, methods, and rate limits.
- Pass ownership verification before becoming active. The verification request must include the target's issued `verificationToken`; knowing only the target ID is not enough to activate a pending external target.

## Status

This is a vertical slice scaffold, not the final production system. Identity stores users in PostgreSQL, issues signed user JWTs with `kid` headers, and the gateway and downstream APIs enforce roles while accepting current and previous user-token secrets during rotation. The active internal workflow uses two-minute, audience-restricted service JWTs; the runtime shared static service token and obsolete client headers have been removed. The complete Docker workflow passes, identity has an opt-in real PostgreSQL integration check against the Compose database, pending external targets require token-backed ownership verification before activation, webhook delivery routes require audience-restricted service JWTs plus safe HTTPS destinations, and reporting routes require platform JWTs with read/write role separation. Focused Maven tests and Docker Compose builds pass for identity, target registry, notification, and reporting after the latest security changes. The release docs now describe user JWTs, internal service JWTs, external-target safety, free local deployment, and the asymmetric-signing production path. Generated Springdoc OpenAPI metadata includes shared `userBearerAuth` and `serviceBearerAuth` JWT schemes. See `docs/production-readiness.md` for the final release decision and production hardening checklist.
