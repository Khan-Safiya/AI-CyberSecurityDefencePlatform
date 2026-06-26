# Architecture

The platform is organized as a Spring Boot microservice monorepo. Each service has its own Maven module and package namespace under `com.cybersim.<service>`.

Core vertical-slice flow:

1. The target registry exposes the built-in sandbox target.
2. The simulation orchestrator starts the baseline scenario.
3. Red-team agents plan only simulated actions.
4. The policy engine approves or denies actions with default-deny behavior.
5. The sandbox target exposes safe mock vulnerable endpoints.
6. Vulnerability, detection, remediation, verification, scoring, event-log, dashboard, and reporting services expose the lifecycle API surface.

RabbitMQ is selected as the event broker for this implementation track.
