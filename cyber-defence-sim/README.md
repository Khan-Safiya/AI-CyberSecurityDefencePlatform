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

Start everything:

```bash
docker compose up --build
```

Useful endpoints after startup:

- API gateway: `http://localhost:8080/`
- Target registry: `http://localhost:8102/targets`
- Policy engine: `http://localhost:8103/policies`
- Sandbox target: `http://localhost:8104/demo/admin/report`
- Default scenario: `http://localhost:8105/scenarios/default`
- Dashboard overview: `http://localhost:8115/dashboard/simulations/00000000-0000-0000-0000-000000000201/overview`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

## Demo Flow

Create a demo simulation:

```bash
curl -X POST http://localhost:8105/simulations \
  -H "Content-Type: application/json" \
  -d '{"name":"Baseline Web Application Defence Simulation","mode":"INTERNAL_SANDBOX","targetId":"00000000-0000-0000-0000-000000000101","maxRounds":5,"maxDurationMinutes":60,"stopWhenNoNewFindingsForRounds":2}'
```

Review demo findings:

```bash
curl http://localhost:8109/vulnerabilities
```

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

This is a vertical slice scaffold, not the final production system. Persistence, JWT signing, service-to-service auth enforcement across all services, RabbitMQ event producers/consumers, Flyway migrations, Testcontainers, and full OpenAPI/security hardening are the next implementation phases.
