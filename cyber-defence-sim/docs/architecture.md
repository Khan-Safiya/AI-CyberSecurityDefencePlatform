# Architecture

The platform is organized as a Spring Boot microservice monorepo. Each service has its own Maven module and package namespace under `com.cybersim.<service>`.

Core vertical-slice flow:

1. Users authenticate through identity and receive signed JWT access tokens.
2. The API gateway validates JWTs, enforces route roles, and forwards only approved route families.
3. The target registry exposes the built-in sandbox target and persists authorized targets in PostgreSQL.
4. The simulation orchestrator starts a durable round and publishes outbox events through RabbitMQ.
5. Red-team, detection, remediation, verification, and scoring workers consume stage requests from durable queues.
6. The policy engine approves or denies every simulated offensive or patching action with default-deny behavior.
7. The sandbox target exposes safe mock vulnerable endpoints and token-protected patch operations.
8. Vulnerability, detection, remediation, verification, scoring, event-log, dashboard, notification, and reporting services expose the lifecycle API surface.

RabbitMQ is the event broker for the active workflow. PostgreSQL stores service-owned state, worker inboxes, and simulation outboxes. Internal workflow calls use two-minute, audience-restricted service JWTs. User-facing reads and writes use platform JWTs with role-based access control.

The production signing path should move from local HS256 shared secrets to asymmetric JWT signing. That can be done with free Java and Spring tooling by giving issuers private keys and consumers public verification keys.
