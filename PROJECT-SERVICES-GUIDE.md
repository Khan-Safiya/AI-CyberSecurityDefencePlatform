# Cyber Defence Simulation Platform: Plain-English Service Guide

This file explains the project in non-technical language and should be updated whenever services, checks, or defensive behavior change.

## Current Project Phase

The repository currently contains a working vertical-slice scaffold and is in the **event-driven core workflow phase**. The target registry, simulation orchestrator, vulnerability registry, detection engine, remediation service, verification service, scoring service, and event log now store state in separate PostgreSQL schemas managed by Flyway. The simulation orchestrator publishes durable RabbitMQ events, and the event log now consumes simulation events safely. Other workflow consumers and some stateful services remain incomplete.

## Progress Snapshot

Estimated SRS completion: **65% complete, 35% remaining** as of 2026-07-01. This is based on implemented behavior, persistence, security controls, integration, and tests, not on the number of service folders. Many folders exist but still contain placeholder behavior.

Estimated remaining effort for one experienced full-time developer: **25 to 45 engineering days**, approximately 5 to 9 working weeks. A smaller local demonstration could be finished sooner, but the estimate here covers the SRS scope: signed JWT/RBAC, stronger service authentication, the scoring workflow, external verification, webhook security, dashboard/reporting, observability, integration tests, and production hardening. The estimate should be revised after each major phase because integration defects and deployment requirements can change it.

All planned resources should stay free or open source. The current stack uses Java, Spring Boot, Maven, Docker Compose, PostgreSQL, Redis, RabbitMQ, Prometheus, and Grafana. These can all be run locally without paid cloud services.

Local passwords and tokens should be kept in `cyber-defence-sim/.env`, copied from `cyber-defence-sim/.env.example`. The `.env` file is intentionally ignored by Git so private local values are not committed.

## What The Platform Does

This project is a safe cyber-defence training simulator. It creates a controlled practice environment where a red team finds known, harmless security weaknesses in a built-in demo target, and a blue team responds by prioritizing, fixing, verifying, and reporting the results.

The red team must never attack real public systems, run malware, steal credentials, perform denial of service, scan arbitrary networks, or execute shell commands. External systems can only be assessed when they are staging/test systems, explicitly registered, authorized, and verified.

## Services Explained

api-gateway-service: The front door of the platform. In the final system, users and tools should call this service first, and it should route requests to the correct internal service.

identity-service: Handles login and user identity. The current version returns a demo token; the final version should issue real signed JWTs and enforce roles.

target-registry-service: Keeps the durable PostgreSQL list of systems that may be tested. Flyway creates its isolated `target_registry` database schema and seeds the built-in sandbox target. External base URLs must match an exact allowed host and port and cannot use embedded credentials, local/private/metadata destinations, ambiguous query or fragment data, or traversal-like scope paths.

policy-engine-service: Decides whether a red-team or blue-team action is allowed. If an action is outside safe scope, it denies the request.

target-system-service: The intentionally vulnerable demo application. It contains mock endpoints that represent common web security problems and safe internal patch endpoints.

The internal patch, rollback, and patch-status endpoints require the configured `SERVICE_AUTH_TOKEN` in the `X-Service-Token` header. This is a local scaffold for service-to-service authentication; the final system should replace it with stronger signed service identity.

simulation-orchestrator-service: Starts a simulation in `RUNNING` state and creates its first durable round instead of fabricating immediate completion. Rounds progress in order through `CREATED`, `RED_TEAM_RUNNING`, `DETECTION_RUNNING`, `BLUE_TEAM_RUNNING`, `VERIFYING`, `SCORING`, and `COMPLETED`. A completed non-terminal round automatically creates the next round. PostgreSQL stores per-round finding, remediation, verification, and risk counts plus simulation configuration, scores, timeline, stop reason, and timestamps.

The engine enforces maximum rounds, maximum duration, consecutive rounds without new findings, all high/critical findings fixed, disabled retesting, manual stop, unsafe action, unavailable target, and policy violation. Internal commands require `SERVICE_AUTH_TOKEN`. The red-team worker now has a dedicated retry-safe completion command: it can move only `RED_TEAM_RUNNING` to `BLUE_TEAM_RUNNING`, and repeated calls cannot skip ahead to verification. The event log is wired to simulation events; automatic red-team, detection, remediation, verification, and scoring workers are not wired yet.

Every simulation start, round stage change, round completion, next-round creation, and terminal stop now writes a PostgreSQL outbox event in the same transaction as the business state. A scheduled publisher sends ready events to the free RabbitMQ `cybersim.events` topic exchange, waits up to five seconds for broker acknowledgement, marks confirmed events published, and retries failed sends with a bounded exponential delay. Delivery is intentionally at least once, so the next consumers must deduplicate by the RabbitMQ message ID/outbox UUID.

agent-orchestrator-service: Lists and coordinates agents. Agents are the automated workers that perform red-team and blue-team tasks.

red-team-agent-service: Plans safe simulated checks and now consumes durable `simulation.round.red-team.requested` messages. It accepts only the built-in sandbox target, reads the six fixed sandbox patch states, asks the policy engine to approve every corresponding action, and creates findings with deterministic idempotency keys. It calls the retry-safe round completion command, records the successful message in its PostgreSQL inbox, and only then acknowledges RabbitMQ. A failed dependency or denied policy is retried at most three times before RabbitMQ moves the message to `cybersim.red-team.dead-letter`. It never accepts user-provided commands, arbitrary hosts, exploit payloads, or paid scanning services.

blue-team-agent-service: Exposes the current defensive planning surface for triage, remediation recommendations, patching, and verification. Durable execution is owned by the remediation service so proposal state, retries, and patch outcomes stay in one database-backed boundary.

vulnerability-registry-service: Stores discovered weaknesses in PostgreSQL with stable IDs, severity, evidence, safe reproduction steps, affected endpoint, expected fix, agent assignment, and lifecycle timestamps. Finding creation accepts an optional `Idempotency-Key`: an identical retry returns the existing finding, while reuse for different finding data is rejected. This prevents a retried red-team message from creating duplicate findings. It supports lookup, filtering by simulation or target, and validated status updates. Flyway seeds the six baseline sandbox findings.

detection-engine-service: Stores monitoring rules and observed security events in PostgreSQL. It now consumes durable `simulation.round.detection.requested` messages after red-team processing. The worker loads only findings from the requested round, maps each supported finding type to one of the fixed safe detection rules, and commits deterministic detection events before releasing the blue-team stage. PostgreSQL inbox records prevent repeated work; partial failures retry three times and then move to `cybersim.detection.dead-letter`. Users can still create, list, inspect, update, disable, or delete rules, and detection creation supports idempotency keys. Rule patterns are descriptive matching expressions, not executable scripts.

remediation-service: Stores blue-team proposals and their full lifecycle in PostgreSQL and now consumes durable `simulation.round.blue-team.requested` messages. It loads only current-round findings and detections, maps them to six fixed remediation types, creates deterministic proposals, obtains policy approval for every patch call, and independently enforces the built-in sandbox target UUID. Failed patches remain `FAILED` and are retried without repeating successful patches. Verification is released only after all applicable sandbox patches succeed. A PostgreSQL inbox and `cybersim.remediation.dead-letter` queue protect duplicate and permanently failed deliveries. Applied patches can still be rolled back through the token-protected target operation; external targets are never patched automatically.

verification-service: Loads applied remediations, reads the token-protected sandbox patch state, and stores evidence-backed `PASSED`, `FAILED`, or `INCONCLUSIVE` results in PostgreSQL. It now consumes durable `simulation.round.verification.requested` messages and verifies only current-round remediations that reached an applied state. Each deterministic result is synchronized to remediation and vulnerability records before commit; synchronization failure retries the Rabbit message instead of releasing inconsistent data. A passed result marks both records `VERIFIED`; failed or inconclusive checks do not close the finding. The PostgreSQL inbox and `cybersim.verification.dead-letter` queue handle duplicate and permanently failed deliveries. Scoring starts only after all verification evidence is durable and synchronized.

scoring-service: Stores immutable scoring events in PostgreSQL and derives totals from them. Internal callers submit a named rule and source-event ID; they cannot choose arbitrary points, team, or reason. All 13 SRS rewards and penalties are enforced by code. Repeating the same request is idempotent, while reusing its source ID with different details returns HTTP 409. Unsafe red-team actions deduct 100 points and record the agent as blocked. The future durable agent orchestrator must enforce that block before execution because current agents are still placeholders.

Final risk score: The scoring API uses a documented temporary formula: `50 + red score - blue score`, clamped to 0 through 100. Zero means lower residual risk and 100 means higher residual risk. Scenario-specific weighting is future work because the SRS does not define a formula.

event-log-service: Stores durable timeline and audit events in timestamp order. It binds a durable RabbitMQ queue to `simulation.#` events, converts accepted messages into immutable timeline entries, and records each outbox UUID in a PostgreSQL inbox table in the same transaction. A repeated delivery is acknowledged without creating another event. Invalid messages are not requeued forever; RabbitMQ moves them to the durable `cybersim.event-log.dead-letter` queue for investigation. The HTTP API still supports append and read operations, duplicate event IDs return HTTP 409, and PostgreSQL rejects updates or deletes.

dashboard-backend-service: Provides dashboard-ready summary data such as status, scores, open vulnerabilities, and timeline.

notification-service: Sends internal notifications and tracks webhook delivery attempts.

reporting-service: Generates final assessment reports.

## Red-Team Vulnerability Checks

Missing authentication: Checks whether `/demo/admin/report` can be reached without proving the user is allowed to see it.

Missing object-level authorization: Checks whether `/demo/users/{id}/documents` exposes another user document by changing the user id in the path.

Missing rate limit: Checks whether `/demo/login` allows repeated login attempts without limiting request frequency.

Exposed debug configuration: Checks whether `/demo/debug/config` reveals mock internal configuration details.

Missing input validation: Checks whether `/demo/billing/search` accepts unchecked input instead of validating the search query.

Simulated dependency risk: Checks whether `/demo/dependencies` reports an intentionally outdated mock dependency version.

Endpoint discovery: Safely identifies known sandbox endpoints inside allowed scope.

Policy-boundary check: Confirms actions only run when the policy engine approves the host, path, method, and action type.

## Blue-Team Corrective Measures

Require authentication: Protects admin-only endpoints so unauthenticated users receive an unauthorized response.

Enforce object authorization: Makes sure users can only access objects, records, or documents they are allowed to see.

Add rate limiting: Limits repeated requests such as login attempts to reduce brute-force risk.

Disable debug endpoints: Removes debug/config endpoints from normal access.

Validate input: Checks and rejects malformed or unsafe user input before processing it. Target registration, policy evaluation, simulation creation, and event ingestion now enforce required fields, bounded text and list sizes, numeric limits, and expected host, path, URL, environment, and HTTP-method shapes.

Update dependency metadata: Marks the simulated risky dependency as updated to a safe mock version.

Create detection rules: Adds monitoring logic so red-team activity is visible to defenders.

Link detections to findings: Records which observed activity relates to which vulnerability, giving blue-team triage and later scoring an auditable connection instead of an isolated message.

Triage findings: Sorts vulnerabilities by severity and business risk so urgent issues are handled first.

Verify remediation: Rechecks the vulnerable behavior after a fix to confirm the weakness is gone.

Require evidence before closure: A vulnerability is marked `VERIFIED` only when the sandbox reports the expected patch as applied. No verification results are pre-seeded because that would claim checks happened when they did not.

Preserve uncertain results: If the target is unsupported, the remediation type is unknown, patch status is missing, or the sandbox is unavailable, verification records `INCONCLUSIVE` instead of guessing.

Enforce finite simulations: Every round is bounded by maximum round and duration limits, and the engine evaluates configured stopping conditions before creating another round. This prevents an infinite simulation loop.

Protect round commands: Only internal callers with the service token can advance or complete a round. Manual user stop remains a separate operation that will receive JWT/RBAC protection in the identity phase.

Require approval before patching: Keeps a proposed blue-team change from modifying even the sandbox until an explicit lifecycle action approves it.

Allowlist automated patches: Maps six known remediation types to fixed sandbox patch names. User text is descriptive only and cannot become a URL, command, script, or arbitrary patch name.

Support honest rollback: Reverses the sandbox target's patch state before recording `ROLLED_BACK`, rather than changing only the database label.

Enforce target scope: Rejects external targets whose base URL, host, port, or paths do not exactly match the declared assessment boundary. This reduces the chance that the platform is tricked into contacting an internal service or testing an unapproved endpoint.

Create reports and timelines: Preserves what happened, what was found, what was fixed, and what remains.

Preserve append-only audit evidence: Stores each accepted event once and prevents later replacement or deletion through the service. This makes investigations and simulation timelines more trustworthy.

Prevent duplicate scoring: Uses the originating event's UUID as an idempotency key within a simulation, so retries do not award or deduct points twice.

## Important Cybersecurity Terms

Authentication: Proving who a user or service is, usually with a password, token, or certificate.

Authorization: Deciding what an authenticated user or service is allowed to do.

JWT: A signed token used to carry identity and role information between services.

Role-based access control: A permission model where access is granted based on roles such as ADMIN or AUDITOR.

Red team: The side that safely tests for weaknesses.

Blue team: The side that detects, prioritizes, fixes, and verifies weaknesses.

Vulnerability: A weakness that could allow unauthorized access, data exposure, abuse, or disruption.

Severity: A rating that explains how serious a vulnerability is, such as LOW, MEDIUM, HIGH, or CRITICAL.

Remediation: The action taken to fix or reduce a vulnerability.

Remediation proposal: A suggested defensive change linked to a vulnerability. It begins in `PROPOSED` state and cannot run until approved.

Approval gate: A required decision point before a sensitive operation. The remediation service rejects direct application of unapproved proposals with HTTP 409.

Allowlist: A closed list of explicitly permitted values. Automated remediation accepts only six known types, each mapped by code to a harmless sandbox patch.

State machine: Rules controlling which lifecycle changes are legal. Remediation follows `PROPOSED -> APPROVED -> APPLIED`, while failures can be retried and applied actions can become `ROLLED_BACK`.

Rollback: Reversing an applied change. In this project rollback resets the corresponding sandbox patch state and records when it happened.

Verification: The follow-up check that confirms remediation worked.

Verification evidence: A short factual explanation of what the safe check observed. It supports audit and triage without storing secrets or an exploit payload.

Inconclusive: A verification result meaning the platform could not safely prove success or failure. It keeps the vulnerability open and requires another check or human review.

False closure: Incorrectly marking a vulnerability fixed without evidence. The verification workflow prevents this by requiring an applied remediation and a positive sandbox patch-state check.

Best-effort synchronization: An immediate attempt to update related services. If one is unavailable, the verification result remains durable and records that synchronization is pending; later event/outbox work must add automatic retries.

Score event: An immutable record explaining which team gained or lost points, which rule applied, and which source event caused it.

Idempotency key: A unique identifier that makes repeated delivery safe. Scoring uses `(simulationId, sourceEventId)` so a retried message returns the original award instead of changing totals again.

Duplicate award: Applying points more than once for the same source action. Both application logic and a database unique constraint prevent this.

Risk score: A normalized 0-to-100 summary of remaining risk. It is not a vulnerability severity and must always be interpreted with the underlying score events and findings.

Score clamping: Restricting a calculated number to a defined range. Risk below 0 becomes 0 and risk above 100 becomes 100.

Simulation round: One complete red-team and blue-team cycle with its own status, timestamps, finding counts, fix counts, and risk before/after values.

Stage transition: A controlled move from one round status to the next. Skipping stages or advancing a completed round returns HTTP 409.

Stopping condition: A rule that ends a simulation, such as reaching the round limit, exceeding duration, finding nothing new for several rounds, fixing all serious findings, or detecting unsafe behavior.

Terminal state: A final status that cannot continue, such as `COMPLETED`, `FAILED`, or `CANCELLED`.

Outbox pattern: A reliability design where a database transaction stores both business state and an event to publish. A worker later sends the event to RabbitMQ and retries safely, preventing a committed round change from losing its integration message.

Transactional outbox: The concrete outbox table owned by the simulation orchestrator. State and event records either commit together or roll back together, avoiding the gap where a round changes but its message is lost.

Publisher confirmation: RabbitMQ's acknowledgement that it accepted a message. The outbox row becomes `PUBLISHED` only after this acknowledgement.

Exponential backoff: Increasing the delay after each failed delivery attempt, capped here at five minutes. It avoids continuously overwhelming an unavailable broker.

At-least-once delivery: A message may be delivered more than once if the publisher crashes after RabbitMQ accepts it but before PostgreSQL records success. Consumers must use the message UUID as an idempotency key.

Idempotent consumer: A message worker that produces the same final result whether RabbitMQ delivers a message once or several times. The event-log consumer checks the outbox UUID before appending an event.

Idempotency key: A stable UUID attached to a command so the receiver can recognize a retry. The future red-team worker will derive one key per message and safe check; the vulnerability registry returns the original finding instead of inserting a duplicate.

Inbox table: A consumer-owned database table containing message IDs that were already handled. The event-log inbox record and timeline event commit together, so a failed transaction can be retried without losing the event.

Dead-letter queue: A durable holding queue for messages that cannot be processed, such as malformed JSON or missing identifiers. Operators can inspect these messages without trapping the main consumer in an endless retry loop.

Detection: A security observation that suspicious or important activity happened.

Detection rule: Declarative monitoring logic that describes activity to recognize, its severity, and whether the rule is enabled. The current platform stores the rule safely but does not execute user-provided code.

Detection event: A durable record that a monitoring rule or policy observation matched activity during a simulation. It can reference the simulation round, target, red-team action, and related vulnerability.

Telemetry: Operational data such as request events, application logs, or policy decisions that monitoring systems inspect for security-relevant behavior. Real telemetry consumers are a later event-driven integration step.

Event correlation: Connecting multiple observations that belong to the same action, target, vulnerability, or time period so defenders can understand a sequence rather than unrelated alerts.

False positive: A detection that reports suspicious behavior when no real security issue occurred. Rules must be testable and tunable to reduce false positives.

Policy engine: A safety decision service that approves or denies actions before they run.

Default deny: A security rule where unknown or uncertain actions are blocked unless explicitly allowed.

Scope: The exact systems, hosts, paths, and methods that testing is allowed to touch.

Sandbox: A safe demo environment designed for testing and training.

Staging target: A non-production environment owned or authorized by the user for testing.

Production target: A real live system. This platform must block production targets by default.

Rate limiting: Restricting how often requests can happen in a time window.

Service-to-service authentication: A way for internal services to prove their identity to each other.

Audit log: A record of important actions used for accountability and investigation.

Correlation ID: A safe request identifier that helps follow one action through API responses and service logs. Every service now accepts a valid `X-Correlation-Id` header or creates a new UUID when the header is missing or unsafe. The same ID is returned in the response header and included in explicit standard error bodies. The filter places it in the logging MDC while the request runs, allowing log patterns to print it without sending data to a paid monitoring service.

MDC (Mapped Diagnostic Context): Temporary request information attached to logs. Here it holds the correlation ID only while one HTTP request is being processed, then it is cleared to prevent one user's ID from leaking into another request's logs.

UUID: A randomly generated identifier with an extremely low chance of duplication. It is used when a request does not already have a safe correlation ID.

Standard error response: A consistent error body containing timestamp, HTTP status, error name, safe message, path, and correlation ID. Controllers use the shared `ApiErrors` helper for explicit errors. The global exception handler now applies the same format to validation failures, malformed request bodies, invalid parameters, unsupported HTTP methods, missing endpoints, and unexpected server failures.

Global exception handler: One shared safety layer that catches API failures for every service and converts them into the standard response. Unexpected errors return a generic message instead of exposing source code details, database information, credentials, or stack traces. Server logs record only the exception type, request path, and correlation ID by default so operators can investigate without automatically copying a potentially sensitive exception message.

Stack trace: A technical list showing which methods were running when software failed. It is useful during private debugging but must not be returned to API users because it can reveal internal implementation details.

Malformed request: Input that cannot be read in the expected format, such as broken JSON. The platform returns HTTP 400 rather than treating it as an internal server failure.

Bean Validation: The free Jakarta Validation standard used to declare input rules directly on request fields. Spring checks these rules before controller business logic runs.

Validation constraint: A rule such as "must not be blank," "must be between 1 and 100," or "must begin with `/`." Constraints reduce crashes, excessive resource use, and ambiguous data, but they do not replace authorization or target-scope checks.

Cross-field validation: A rule involving more than one value. Simulation requests now require the "stop after no new findings" round count to be no greater than the simulation's maximum round count.

SSRF (Server-Side Request Forgery): A vulnerability where an attacker tricks a server into making a network request to an unintended destination, such as localhost, a private database, or a cloud metadata endpoint. External target registration now blocks common SSRF destinations before storing the target.

Cloud metadata endpoint: A special local network address used by some cloud platforms to provide machine credentials and configuration. It must never be reachable through user-controlled target URLs.

Path traversal: Input such as `../` or encoded separator sequences that may escape an intended path boundary. Target scope paths containing these ambiguous sequences are rejected.

DNS rebinding: A hostname can resolve to a safe public address during one check and later resolve to a private address when the server connects. Registration validation cannot fully prevent this; the future HTTP verification client must resolve and recheck every destination immediately before each connection and redirect.

Event-driven architecture: A design where services publish and consume events such as `vulnerability.discovered`.

Webhook: An HTTP callback sent to another system when an event happens.

Prometheus: A free monitoring tool that collects metrics.

Grafana: A free dashboard tool that visualizes metrics.

RabbitMQ: A free message broker used to pass events between services.

PostgreSQL: A free relational database.

Persistence: Saving information outside a running service process so it remains available after the service restarts. Registered targets and simulations are now persistent; most other service data is not yet persistent.

JPA (Java Persistence API): A standard Java mapping between objects and relational database tables. The target registry uses Spring Data JPA and Hibernate, both free and open source.

Flyway migration: A numbered SQL file that changes a database schema in a controlled, repeatable order. Flyway records which versions ran and automatically applies missing target-registry migrations during startup.

Database schema: A named area inside a database that owns a service's tables. Target and simulation tables use isolated `target_registry` and `simulation_orchestrator` schemas so each service owns its data without table-name collisions.

H2: A free in-memory relational database used only by automated tests. It lets tests verify migrations and persistence without requiring a running Docker database; production and local Compose use PostgreSQL.

Docker named volume: Storage managed by Docker outside a container's temporary filesystem. The `postgres-data` volume keeps database files when containers restart or are recreated.

Health check: A recurring readiness test. Docker uses `pg_isready` to wait for PostgreSQL before starting persistent services, reducing database startup race failures.

Append-only log: A record where new entries may be added but existing entries cannot be changed or removed. The event service enforces this in its API/storage code and PostgreSQL trigger.

Database trigger: Logic executed automatically by the database. The event-log trigger rejects every SQL `UPDATE` or `DELETE` against stored events, providing protection even if future application code accidentally attempts mutation.

HTTP 409 Conflict: A response meaning the request conflicts with existing state. Reusing an event ID returns 409 instead of overwriting the original audit event.

Finding lifecycle: The controlled status history of a vulnerability, from `OPEN` or `DETECTED` through triage and remediation to a resolved state such as `VERIFIED`, `ACCEPTED_RISK`, or `FALSE_POSITIVE`. Resolved findings receive a resolution timestamp.

Redis: A free in-memory data store used for fast temporary state such as rate-limit counters.

## Known Bugs Fixed In This Update

Service containers exited with code 1 because Maven produced plain jars without an executable Spring Boot manifest. The service parent now runs Spring Boot `repackage`, so `java -jar app.jar` can start each service.

The editor could mark the `services` folder as erroneous even while Maven passed because VS Code's Java auto-builder wrote stale unresolved classes into Maven `target` directories after shared DTO changes. Workspace settings now force Java 21, refresh Maven configuration automatically, exclude all `target` directories from import, and disable competing IDE auto-build output. Maven clean builds remain authoritative.

Unauthorized or disabled external targets could be verified later and changed to active. Verification now rejects targets that already failed ownership or are disabled.

An empty policy evaluation request could cause a server error. The policy engine now denies empty requests by default.

The `PRODUCTION_BLOCKED` environment label was not handled by the same activation guard as `PRODUCTION`. Both labels now create disabled targets and cannot pass ownership verification.

## Next Phase

Phase 2 hardening includes regression tests around policy denial, target verification, target-scope SSRF controls, sandbox patch and rollback behavior, simulation creation, event ingestion, baseline vulnerability findings, detection-rule lifecycle, linked detection events, remediation transitions, evidence-backed verification, and all scoring rules. The target registry, simulation orchestrator, vulnerability registry, detection engine, remediation service, verification service, scoring service, and append-only event log now use PostgreSQL-backed storage with Flyway-owned schemas. Docker persists PostgreSQL files in a named volume and waits for database health before launching these services.

Phase 4 is complete. Phase 5 now has durable rounds, an append-only event log, a confirmed/retried RabbitMQ outbox publisher, and idempotent event-log, red-team, detection, remediation, and verification consumers. Every active stage waits for durable results from the previous stage. The next step is the scoring consumer: derive current-round counts and scores from durable findings, remediations, and verification evidence, submit idempotent scoring events, and complete the round through the existing stop-condition engine.
