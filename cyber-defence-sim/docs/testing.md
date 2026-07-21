# Testing

Implemented test layers:

- Unit and controller tests for policy decisions, validation, error format, authentication, authorization, target verification, scoring, remediation, verification, notification, and reporting behavior.
- Persistence integration tests using free H2 for fast default feedback.
- Opt-in real PostgreSQL identity integration testing through Docker Compose and the free Maven Docker image.
- Docker Compose end-to-end workflow testing for the one-round sandbox scenario.
- Focused security verification sweeps for services touched by JWT/RBAC and service-JWT changes.
- Shared OpenAPI security-scheme regression coverage for generated user and service bearer JWT metadata.

Useful commands:

```bash
mvn test
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-postgres-integration-tests.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-e2e-tests.ps1
```

The complete Compose workflow check requires six findings, six detections, six verified remediations, six passed verifications, 36 score events, zero residual risk, and a completed simulation.

The latest focused verification sweep passed:

```bash
mvn -q -pl services/identity-service,services/target-registry-service,services/notification-service,services/reporting-service -am test
docker compose build identity-service target-registry-service notification-service reporting-service
```

The latest generated-documentation check passed:

```bash
mvn -q -pl shared/common-observability -am test
mvn -q -pl services/reporting-service -am test
```

Remaining release test work is small: add regression tests if asymmetric signing is implemented.
