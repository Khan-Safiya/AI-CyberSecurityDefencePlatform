# Local Development

Run with Docker Compose:

```bash
docker compose up --build
```

Run one service with Maven:

```bash
mvn -pl services/policy-engine-service -am spring-boot:run
```

Create local environment values before starting services:

```bash
cp .env.example .env
```

Change local-only defaults in `.env`, especially `POSTGRES_PASSWORD`, `JWT_SECRET`, demo account passwords, and `GRAFANA_ADMIN_PASSWORD`. Set `JWT_ACTIVE_KEY_ID` to a short label for the current signing key. Use `JWT_PREVIOUS_SECRET` only during token rotation.

Local development uses only free tooling:

- Java 21
- Maven 3.9+
- Docker and Docker Compose
- PostgreSQL
- RabbitMQ
- Redis
- Prometheus
- Grafana

The default Maven tests use H2 for speed. Docker is needed for the full Compose workflow and the opt-in PostgreSQL identity integration check.
