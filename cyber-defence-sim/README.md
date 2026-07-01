# Cyber Defence Simulation Platform

Production-oriented Java/Spring Boot monorepo for a safe multi-agent cyber defence simulation platform.

This repository starts the SRS implementation with a working vertical slice:

- Identity service with demo auth endpoints.
- API gateway placeholder route index.
- Target registry with built-in sandbox target and external target safety checks.
- Policy engine with default-deny evaluation.
- Built-in sandbox target with intentionally vulnerable mock endpoints and safe patch endpoints.
- Simulation orchestrator with seeded baseline scenario and completed demo flow.
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

The example values are for local development only. Change `POSTGRES_PASSWORD`, `JWT_SECRET`, `SERVICE_AUTH_TOKEN`, and `GRAFANA_ADMIN_PASSWORD` in `.env` before using the platform beyond a local demo.

Start everything:

```bash
docker compose up --build
```

Flyway automatically creates the target-registry, simulation-orchestrator, vulnerability-registry, detection-engine, remediation, verification, scoring, and append-only event-log schemas. It seeds the built-in sandbox target, six baseline findings, six safe detection rules, three linked detection events, and six remediation proposals. Verification and scoring records are created only from real workflow requests, so none are falsely pre-seeded. PostgreSQL files are retained in the `postgres-data` Docker volume across normal container recreation.

Useful endpoints after startup:

- API gateway: `http://localhost:8080/`
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

Round stage advancement and completion are internal operations protected by `X-Service-Token`. Every committed simulation and round transition now creates a transactional outbox event. A scheduled publisher sends pending events to the durable `cybersim.events` RabbitMQ topic exchange, waits for broker confirmation, and retries failures with bounded exponential backoff. Event-log, red-team, detection, remediation, and verification workers use separate durable queues, PostgreSQL inboxes, deterministic result IDs, bounded retries, and dead-letter queues. Detection evidence commits before blue-team work; sandbox patch outcomes commit before verification; synchronized verification evidence commits before `SCORING`. The scoring consumer is the next integration step.

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
- Pass ownership verification before becoming active.

## Status

This is a vertical slice scaffold, not the final production system. The target registry, iterative simulation rounds, vulnerability registry, detection engine, remediation service, verification service, scoring service, event log, and worker inboxes now have isolated PostgreSQL schemas and Flyway migrations. Round changes and outbound RabbitMQ events commit together; event-log, red-team, detection, remediation, and verification processing are idempotent from the application's perspective. The scoring stage consumer, JWT signing, stronger service identity, persistence for remaining stateful services, PostgreSQL container integration tests, and full OpenAPI/security hardening remain later implementation steps.
