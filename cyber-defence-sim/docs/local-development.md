# Local Development

Run with Docker Compose:

```bash
docker compose up --build
```

Run one service with Maven:

```bash
mvn -pl services/policy-engine-service -am spring-boot:run
```

The current host environment used to create this repository had Java available but did not have Maven or Gradle installed.
