# Cyber Defence Simulation Platform: Plain-English Service Guide

This file explains the project in non-technical language and should be updated whenever services, checks, or defensive behavior change.

## Current Project Phase

The repository contains a working vertical slice and is now in **Phase 6: identity and access security**. The complete event-driven workflow passes a real Docker Compose round. Identity stores users in PostgreSQL, issues signed JWT access tokens with key IDs, and the gateway, target registry, simulation orchestrator, and dashboard enforce role permissions while accepting the current and previous user-token signing secrets during rotation. Identity also has an opt-in real PostgreSQL integration test that runs against the local Compose database using only free Docker, PostgreSQL, Maven, and Spring tooling.

## Progress Snapshot

Estimated SRS completion: **99.9% complete, 0.1% remaining** as of 2026-07-09.

Estimated remaining effort for one experienced full-time developer: **final handoff review only**. The remaining work is to run whichever final verification gate the release owner wants before packaging or committing.

All planned resources should stay free or open source. The current stack uses Java, Spring Boot, Maven, Docker Compose, PostgreSQL, Redis, RabbitMQ, Prometheus, and Grafana. These can all be run locally without paid cloud services.

Local passwords and tokens should be kept in `cyber-defence-sim/.env`, copied from `cyber-defence-sim/.env.example`. The `.env` file is intentionally ignored by Git so private local values are not committed.

## What The Platform Does

This project is a safe cyber-defence training simulator. It creates a controlled practice environment where a red team finds known, harmless security weaknesses in a built-in demo target, and a blue team responds by prioritizing, fixing, verifying, and reporting the results.

The red team must never attack real public systems, run malware, steal credentials, perform denial of service, scan arbitrary networks, or execute shell commands. External systems can only be assessed when they are staging/test systems, explicitly registered, authorized, and verified.

## Services Explained

api-gateway-service: The front door of the platform. It validates the JWT signature, issuer, and expiry using the current user-token signing secret, and can also accept the previous signing secret during a controlled rotation window. Any valid platform role may read `/api/**`. Only `ADMIN` and `SIMULATION_OPERATOR` may send POST, PUT, PATCH, or DELETE requests there. `/api/admin/**` accepts only `ADMIN`. Missing, forged, or expired tokens are rejected before a backend is contacted. It forwards four fixed families to identity, target registry, simulation orchestrator, and dashboard services. Unknown families return 404, unavailable destinations return 502, and the destination list comes only from trusted configuration rather than user input. Only bearer, accept, content-type, idempotency, and correlation headers cross the boundary; user-supplied `X-Service-Token` values are stripped.

identity-service: Stores platform users in PostgreSQL under its own Flyway-managed `identity_service` schema. On startup it seeds the three local development accounts only if they are missing. Passwords are stored as BCrypt hashes, never as plaintext. Login returns a real HS256-signed JWT that expires after 15 minutes by default. New tokens include a `kid` header from `JWT_ACTIVE_KEY_ID`, which tells operators which signing key produced the token. The token contains only a user ID, username, roles, issuer, and timestamps. `/auth/me` requires a valid bearer token and accepts both current and previous user-token secrets during rotation. `/auth/users` is restricted to `ADMIN`; `AUDITOR` and `SIMULATION_OPERATOR` cannot list users. Admins can enable accounts, disable accounts, and reset passwords through protected endpoints. Passwords and signing secrets are configured through environment variables. The default tests use free H2 for fast feedback, and `scripts/run-postgres-integration-tests.ps1` runs the same authentication lifecycle against a disposable Compose PostgreSQL database. Refresh tokens and asymmetric signing remain future work.

target-registry-service: Keeps the durable PostgreSQL list of systems that may be tested. Flyway creates its isolated `target_registry` database schema and seeds the built-in sandbox target. Direct reads require a valid `ADMIN`, `SIMULATION_OPERATOR`, or `AUDITOR` JWT; changes require `ADMIN` or `SIMULATION_OPERATOR`. It accepts the current and previous user-token signing secrets during rotation. External base URLs must match an exact allowed host and port and cannot use embedded credentials, local/private/metadata destinations, ambiguous query or fragment data, or traversal-like scope paths. Pending external targets cannot become active by ID alone; the ownership verification request must include the exact issued `verificationToken`, and wrong tokens leave the target pending.

policy-engine-service: Decides whether a red-team or blue-team action is allowed. Sandbox paths (`/demo/**`, `/internal/patches/**`) are approved directly. Any other action is checked against the target's own declared scope, fetched live from target-registry-service's internal, service-JWT-protected endpoint: the target must be an `ACTIVE` `EXTERNAL_STAGING_TARGET`, and the request's host, path, excluded paths, and HTTP method must all fall within what the target owner declared at registration. Anything outside that scope, or any target the policy engine cannot look up, is denied by default.

target-system-service: The intentionally vulnerable demo application. It contains mock endpoints that represent common web security problems and safe internal patch endpoints.

Patch application, status, and rollback endpoints now require short-lived JWTs intended specifically for the target-system service. Red-team, remediation, and verification receive only the permissions they need. The end-to-end reset harness creates a two-minute `SERVICE_REMEDIATION` JWT locally instead of sending the shared token.

simulation-orchestrator-service: Starts a simulation in `RUNNING` state and creates its first durable round instead of fabricating immediate completion. Rounds progress in order through `CREATED`, `RED_TEAM_RUNNING`, `DETECTION_RUNNING`, `BLUE_TEAM_RUNNING`, `VERIFYING`, `SCORING`, and `COMPLETED`. A completed non-terminal round automatically creates the next round. PostgreSQL stores per-round finding, remediation, verification, and risk counts plus simulation configuration, scores, timeline, stop reason, and timestamps.

The engine enforces maximum rounds, maximum duration, consecutive rounds without new findings, all high/critical findings fixed, disabled retesting, manual stop, unsafe action, unavailable target, and policy violation. Direct user reads accept all three platform roles; starting and stopping simulations requires `ADMIN` or `SIMULATION_OPERATOR`. User-token reads and writes accept the current and previous signing secrets during rotation. Internal commands use two-minute service JWTs with an exact destination audience and a narrowly scoped machine role. Each worker has a retry-safe completion command and can advance only its own stage. Red-team, detection, remediation, verification, scoring, and event-log consumers are wired through durable RabbitMQ queues and database inboxes.

Every simulation start, round stage change, round completion, next-round creation, and terminal stop now writes a PostgreSQL outbox event in the same transaction as the business state. A scheduled publisher sends ready events to the free RabbitMQ `cybersim.events` topic exchange, waits up to five seconds for broker acknowledgement, marks confirmed events published, and retries failed sends with a bounded exponential delay. Delivery is intentionally at least once, so the next consumers must deduplicate by the RabbitMQ message ID/outbox UUID.

agent-orchestrator-service: Lists and coordinates agents. Agents are the automated workers that perform red-team and blue-team tasks.

red-team-agent-service: Plans safe simulated checks and now consumes durable `simulation.round.red-team.requested` messages. For the built-in sandbox target, it reads the six fixed sandbox patch states, asks the policy engine to approve every corresponding action, and creates findings with deterministic idempotency keys, exactly as before. For a verified, `ACTIVE` external staging target, and only when `red-team.external-checks.enabled` is turned on (off by default), it instead runs four safe, read-only black-box probes - missing authentication, missing security headers, exposed debug detail, and missing rate limiting - against the target's own declared scope, using a hardened outbound HTTP client (`shared/common-http`) that re-resolves and rejects private/local/reserved addresses before every connection and never follows redirects automatically. Every probe is still approved by the policy engine first, and a probe that is denied, out of scope, or fails to connect is skipped rather than treated as a hard failure. External targets are still never patched automatically; this remains a detect-and-report capability. For simulation reads and stage completion it creates a fresh two-minute JWT with the `SERVICE_RED_TEAM` role and a simulation-orchestrator (or target-registry) audience; the orchestrator rejects this identity from other worker commands. It records successful processing in its PostgreSQL inbox before acknowledging RabbitMQ. A failed dependency or denied policy is retried at most three times before RabbitMQ moves the message to `cybersim.red-team.dead-letter`. It never accepts user-provided commands, arbitrary hosts, exploit payloads, or paid scanning services.

blue-team-agent-service: Exposes the current defensive planning surface for triage, remediation recommendations, patching, and verification. Durable execution is owned by the remediation service so proposal state, retries, and patch outcomes stay in one database-backed boundary.

vulnerability-registry-service: Stores discovered weaknesses in PostgreSQL with stable IDs, severity, evidence, safe reproduction steps, affected endpoint, expected fix, agent assignment, and lifecycle timestamps. Verification-result synchronization now accepts only a `SERVICE_VERIFICATION` JWT intended specifically for this service; legacy tokens, wrong roles, and tokens for other audiences are rejected. Finding creation accepts an optional `Idempotency-Key`: an identical retry returns the existing finding, while reuse for different finding data is rejected. This prevents a retried red-team message from creating duplicate findings. It supports lookup, filtering by simulation or target, and validated status updates. Flyway seeds the six baseline sandbox findings.

detection-engine-service: Stores monitoring rules and observed security events in PostgreSQL. It consumes durable `simulation.round.detection.requested` messages after red-team processing. The worker loads only findings from the requested round, maps each supported finding type to one of the fixed safe detection rules, and commits deterministic detection events before releasing the blue-team stage. Its orchestrator completion call issues a fresh two-minute `SERVICE_DETECTION` JWT restricted to the simulation-orchestrator audience. Focused authorization tests and the complete Docker workflow pass with this identity. PostgreSQL inbox records prevent repeated work; partial failures retry three times and then move to `cybersim.detection.dead-letter`. Rule patterns are descriptive matching expressions, not executable scripts.

remediation-service: Stores blue-team proposals and their full lifecycle in PostgreSQL and consumes durable `simulation.round.blue-team.requested` messages. It loads only current-round findings and detections, maps them to six fixed remediation types, creates deterministic proposals, obtains policy approval for every patch call, and independently enforces the built-in sandbox target UUID. Its orchestrator completion call now uses a fresh two-minute `SERVICE_REMEDIATION` JWT restricted to the simulation-orchestrator audience; focused tests and the complete Docker workflow pass. Failed patches remain `FAILED` and are retried without repeating successful patches. Verification is released only after all applicable sandbox patches succeed. A PostgreSQL inbox and `cybersim.remediation.dead-letter` queue protect duplicate and permanently failed deliveries. Applied patches can still be rolled back through the token-protected target operation; external targets are never patched automatically.

verification-service: Loads applied remediations, reads the token-protected sandbox patch state, and stores evidence-backed `PASSED`, `FAILED`, or `INCONCLUSIVE` results in PostgreSQL. It consumes durable `simulation.round.verification.requested` messages and verifies only current-round remediations that reached an applied state. Its orchestrator completion call uses a fresh two-minute `SERVICE_VERIFICATION` JWT restricted to the simulation-orchestrator audience; focused tests and the complete Docker workflow pass. Each deterministic result is synchronized to remediation and vulnerability records before commit; synchronization failure retries the Rabbit message instead of releasing inconsistent data. A passed result marks both records `VERIFIED`; failed or inconclusive checks do not close the finding. The PostgreSQL inbox and `cybersim.verification.dead-letter` queue handle duplicate and permanently failed deliveries. Scoring starts only after all verification evidence is durable and synchronized.

scoring-service: Stores immutable scoring events in PostgreSQL and derives totals from them. It consumes `simulation.round.scoring.requested`, loads current-round findings, detections, remediations, and verification evidence, and submits only the existing fixed scoring rules. Its simulation read and round-completion calls use a fresh two-minute `SERVICE_SCORING` JWT restricted to the simulation-orchestrator audience; focused tests and the complete Docker workflow pass. A deterministic source UUID per entity and rule prevents duplicate awards. It calculates cumulative team totals and residual risk, builds the round counts, and calls the existing round completion and stop-condition engine. Its PostgreSQL inbox, bounded retries, and `cybersim.scoring.dead-letter` queue protect message delivery.

Final risk score: The scoring API uses a documented temporary formula: `50 + red score - blue score`, clamped to 0 through 100. Zero means lower residual risk and 100 means higher residual risk. Scenario-specific weighting is future work because the SRS does not define a formula.

event-log-service: Stores durable timeline and audit events in timestamp order. It binds a durable RabbitMQ queue to `simulation.#` events, converts accepted messages into immutable timeline entries, and records each outbox UUID in a PostgreSQL inbox table in the same transaction. A repeated delivery is acknowledged without creating another event. Invalid messages are not requeued forever; RabbitMQ moves them to the durable `cybersim.event-log.dead-letter` queue for investigation. The HTTP API still supports append and read operations, duplicate event IDs return HTTP 409, and PostgreSQL rejects updates or deletes.

dashboard-backend-service: Provides dashboard-ready summary data such as status, scores, open vulnerabilities, and timeline. Direct dashboard reads require a valid JWT with any platform role and accept the current and previous signing secrets during rotation; unsupported writes are denied.

notification-service: Sends internal notifications and tracks webhook delivery attempts. Internal notification writes, webhook delivery reads, and webhook delivery creation now require a short-lived service JWT with the `SERVICE_NOTIFICATION` role and `notification-service` audience. Webhook destinations are accepted only when they use HTTPS, have a public hostname, and do not contain embedded credentials, query strings, fragments, localhost, private IP ranges, metadata hosts, or internal-only hostnames. This prevents the notification service from being used as a blind request tool against internal systems.

reporting-service: Generates final assessment reports. Report reads require a valid platform JWT from `ADMIN`, `SIMULATION_OPERATOR`, or `AUDITOR`. Report generation requires `ADMIN` or `SIMULATION_OPERATOR`, so auditors can inspect reports without creating new ones. The service accepts the active and previous user-token signing secrets during rotation.

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

Protect round commands: Only internal callers with short-lived audience-restricted service JWTs can advance or complete a round. Manual user stop is protected by user JWT/RBAC.

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

JWT claim: One named fact inside a token. This project uses claims for the username, user ID, roles, issuer, issue time, and expiry time; passwords are never token claims.

Bearer token: A credential sent as `Authorization: Bearer <token>`. Anyone possessing an unexpired token can use its permissions, so tokens must not be logged or committed.

HS256: A JWT signature algorithm based on HMAC-SHA256. The identity service signs tokens with one active secret of at least 32 bytes. User-token validators can temporarily trust the active and previous secrets to allow safe rotation. HS256 is free and appropriate for this local phase, but every validating service shares the secret; asymmetric keys are safer for a larger deployment.

Key ID (`kid`): A JWT header value naming the signing key used for a token. New identity tokens use `JWT_ACTIVE_KEY_ID`, so operators can see which key version issued the token without exposing the secret.

Signing-key rotation: Replacing a JWT signing secret without immediately breaking users who already have short-lived tokens. In this project, set `JWT_PREVIOUS_SECRET` to the old secret, set `JWT_SECRET` to the new secret, set `JWT_ACTIVE_KEY_ID` to a new label, restart the identity, gateway, target registry, simulation orchestrator, and dashboard services, wait longer than the access-token lifetime, then clear `JWT_PREVIOUS_SECRET` and restart those services again.

Token expiry: The time after which a token is rejected. Short expiry limits damage if a token leaks; access tokens currently last 15 minutes by default.

BCrypt: A deliberately slow password-hashing function that makes password guessing more expensive. The identity service compares passwords through BCrypt and never stores plaintext inside JWTs.

Role-based access control: A permission model where access is granted based on roles such as ADMIN or AUDITOR.

Gateway: The controlled front door that checks a request before passing it to an internal service. A gateway security decision does not replace checks inside the destination service.

Reverse proxy: A server that receives a client request and makes the corresponding request to an internal destination. The client sees the gateway address instead of each service address.

Header allowlist: A fixed list of HTTP headers permitted to cross a boundary. This prevents untrusted internal credentials and connection-specific headers from being smuggled through the gateway.

HTTP 502 Bad Gateway: The gateway accepted the request but could not contact or read the configured backend service. It indicates a dependency problem rather than invalid user input.

Read-only role: A role allowed to view information but not change it. `AUDITOR` can use gateway GET requests but receives 403 for write methods.

HTTP 401 Unauthorized: The request has no acceptable proof of identity, such as when a bearer token is missing, expired, or has an invalid signature.

HTTP 403 Forbidden: The identity is valid, but its role does not permit the requested operation. For example, an auditor cannot start or modify a simulation.

Defence in depth: Applying security at more than one boundary. The gateway and downstream target, simulation, and dashboard services now independently check user access, so bypassing the gateway does not bypass those permission checks.

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

Service-to-service authentication: A way for internal services to prove their identity to each other. The shared security module issues short-lived signed service JWTs containing a machine name, intended receiver, service role, and two-minute expiry. The active simulation workflow uses this mechanism for orchestrator commands, target patch operations, verification synchronization, and scoring submission. The runtime shared static service token has been removed.

Audience: A JWT field naming the service that should accept the token. It prevents a token issued for one internal destination from being treated as valid everywhere.

Webhook: An HTTP callback sent to another system when something happens. Webhooks must be treated like outbound network access: the platform should only send them to safe, approved, public HTTPS destinations.

Webhook destination validation: Checking a webhook URL before delivery so it cannot point to localhost, private networks, metadata services, embedded credentials, or confusing URL forms. This protects the platform from SSRF-style misuse.

Service role: A narrowly scoped machine permission such as `SERVICE_SCORING` or `SERVICE_RED_TEAM`. It identifies what one worker may do without giving it human administrator permissions.

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

Persistence: Saving information outside a running service process so it remains available after the service restarts. Platform users, registered targets, simulations, findings, detections, remediations, verification results, scoring events, inboxes, outboxes, and audit events are now persistent.

JPA (Java Persistence API): A standard Java mapping between objects and relational database tables. Persistent services use Spring Data JPA and Hibernate, both free and open source.

Flyway migration: A numbered SQL file that changes a database schema in a controlled, repeatable order. Flyway records which versions ran and automatically applies missing service migrations during startup.

Database schema: A named area inside a database that owns a service's tables. Identity, target, simulation, vulnerability, detection, remediation, verification, scoring, and event-log tables use isolated schemas so each service owns its data without table-name collisions.

H2: A free in-memory relational database used by the default automated tests. It gives fast feedback without requiring Docker. The identity service now also has an opt-in real PostgreSQL integration test for database behavior that H2 may not perfectly match.

PostgreSQL integration test: A slower automated check that runs against a real PostgreSQL container instead of H2. The current identity check creates a disposable `cybersim_it` database, runs Flyway migrations, verifies seeded users, checks JWT `kid` headers and roles, confirms non-admin access is forbidden, and tests admin enable/disable login behavior.

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

Pending external staging targets could previously be activated by anyone allowed to call the verification endpoint if they knew the target ID. Verification now requires the target's issued token and compares it with a constant-time check before changing the target to active.

An empty policy evaluation request could cause a server error. The policy engine now denies empty requests by default.

The `PRODUCTION_BLOCKED` environment label was not handled by the same activation guard as `PRODUCTION`. Both labels now create disabled targets and cannot pass ownership verification.

The simulation database rejected valid `DETECTION_RUNNING` and `SCORING` transitions because its status constraint still described an older workflow. Flyway migration V4 expands the constraint, and a database-backed regression test now persists every running stage.

Docker builds sent generated Maven output in a roughly 392 MB context and repeatedly downloaded dependencies. `.dockerignore` now excludes generated files, and BuildKit keeps a free local Maven dependency cache across service builds.

Adding the shared security module to token-issuing workers initially pulled in Spring's resource-server starter and silently protected their existing APIs. Remediation then received HTTP 401 when reading detection results. Red-team and detection now exclude that server auto-configuration and depend only on the JWT/Jose library needed to issue tokens.

The end-to-end script checked service health only once and could fail while a newly started container was still initializing. It now polls each required health endpoint until the configured timeout and names the port that failed readiness.

The first PostgreSQL integration attempt tried to connect from the Windows host through the published Postgres port and then through the Docker-network IP. Host password authentication and Docker Desktop networking made that unreliable. The final script runs Maven inside the free Maven Docker image on the Compose network, where the test can reach `postgres:5432` consistently, and uses a disposable test database so local service data is not touched.

Webhook delivery state and delivery creation were previously public placeholder routes. They now require a `SERVICE_NOTIFICATION` JWT for the `notification-service` audience, and delivery creation rejects unsafe outbound destinations before a webhook can be queued.

Report generation and report reads were previously public placeholder routes. They now require platform JWTs, enforce read/write role separation, and accept the previous signing secret during short rotation windows.

## Next Phase

Phase 2 hardening includes regression tests around policy denial, target verification, target-scope SSRF controls, sandbox patch and rollback behavior, simulation creation, event ingestion, baseline vulnerability findings, detection-rule lifecycle, linked detection events, remediation transitions, evidence-backed verification, and all scoring rules. The target registry, simulation orchestrator, vulnerability registry, detection engine, remediation service, verification service, scoring service, and append-only event log now use PostgreSQL-backed storage with Flyway-owned schemas. Docker persists PostgreSQL files in a named volume and waits for database health before launching these services.

Phase 4 and Phase 5 are complete. The Docker Compose integration gate completed a real sandbox round with six findings, six detections, six verified remediations, six passed verifications, 36 score events, zero residual risk, no service errors, and no dead-letter messages. The reusable check is `cyber-defence-sim/scripts/run-e2e-tests.ps1`.

The project is approximately 99.9% complete, with 0.1% remaining and only final handoff review left. Scoring submission now requires `SERVICE_SCORE_PRODUCER` with the `scoring-service` audience. Identity accounts now persist in PostgreSQL with BCrypt hashes, admin account enable/disable controls, admin password reset, `kid` headers, current-plus-previous user-token signing-secret validation, and an opt-in real PostgreSQL integration test. Pending external targets now require token-backed ownership verification before activation. Notification and webhook routes require audience-restricted service JWTs and reject unsafe webhook URLs. Reporting routes require platform JWTs and role-based read/write access. The runtime shared static service token and obsolete client headers are removed. The complete Docker round passes with six findings, detections, remediations, and verifications, 36 score events, and zero residual risk. A focused final verification sweep also passes Maven tests and Docker Compose builds for identity, target registry, notification, and reporting. Release documentation now describes the user JWT scheme, internal service JWT scheme, external-target boundary, free local deployment model, and asymmetric-signing production upgrade path. Generated OpenAPI metadata now includes shared `userBearerAuth` and `serviceBearerAuth` JWT schemes through free Springdoc auto-configuration. `cyber-defence-sim/docs/production-readiness.md` records the final decision to keep HS256 for the local release and schedule asymmetric signing as the first production hardening task.

The next step is final handoff verification: run either the full Maven suite, the complete Docker end-to-end script, or both depending on available time. All remaining work should stay on free local Spring, Maven, Docker, PostgreSQL, and test tooling.
