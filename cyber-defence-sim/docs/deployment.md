# Deployment

The local deployment target is Docker Compose. All required infrastructure uses free local resources: PostgreSQL, Redis, RabbitMQ, Prometheus, Grafana, Maven, and Spring Boot.

Local Compose deployment provides:

- Independently packaged service containers.
- Per-service PostgreSQL schemas managed by Flyway.
- RabbitMQ durable queues, retries, and dead-letter queues for the active workflow.
- Prometheus scrape configuration and Grafana for local dashboards.
- Environment-driven secrets through `.env`.

Production deployment should keep the same container boundaries while adding stronger operational controls:

- Use a real secret source for `POSTGRES_PASSWORD`, demo account passwords, JWT signing material, and Grafana credentials.
- Replace HS256 shared JWT secrets with asymmetric signing such as RS256 or ES256.
- Give each service only public verification keys, not private signing keys, unless that service is an issuer.
- Keep service JWT lifetimes short and audience-restricted.
- Run PostgreSQL with backups, restore drills, and least-privilege database users.
- Keep RabbitMQ dead-letter queues monitored and retained long enough for investigation.
- Export logs and metrics to a centralized stack.
- Publish OpenAPI descriptions that clearly separate user JWT routes from internal service JWT routes.

No paid cloud service is required for the project scope. A production-like local or self-hosted deployment can use Docker Compose, mounted PEM or Java KeyStore files, PostgreSQL, RabbitMQ, Prometheus, Grafana, and GitHub Actions or another free CI runner.
