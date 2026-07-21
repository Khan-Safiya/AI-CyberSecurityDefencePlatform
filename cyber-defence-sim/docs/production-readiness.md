# Production Readiness

This project is ready as a local, free, Docker Compose cyber defence simulation platform. It is not yet a production multi-tenant SaaS deployment.

## Current Release Decision

Keep HS256 JWT signing for the local release and document asymmetric signing as the production upgrade path.

Reason:

- The current local platform already has signed JWTs, `kid` headers, short token lifetimes, previous-secret rotation support, role checks, and service audience checks.
- Replacing HS256 with RS256 or ES256 now would touch identity, gateway, target registry, simulation orchestrator, dashboard, reporting, notification, target system, scoring, remediation, verification, and vulnerability synchronization paths.
- That change is security-sensitive and should be done with a focused implementation cycle and full regression pass, not as late release polish.
- The production path can still use only free resources: Java KeyStore or PEM files, Spring Security, Nimbus JOSE JWT, Docker secrets or mounted local files, Maven, and Docker Compose.

## Must Change Before Real Production Use

- Replace all demo passwords and local secrets in `.env`.
- Move user and service JWT signing to asymmetric keys.
- Store private signing keys only in issuer services.
- Give consumer services public verification keys only.
- Keep service JWTs short-lived and audience-restricted.
- Add backups and restore drills for PostgreSQL.
- Monitor RabbitMQ dead-letter queues.
- Export logs and metrics to a central location.
- Review generated OpenAPI output before publishing it.
- Re-run the complete Docker workflow check after any security change.

## Current Free Verification Gates

```bash
mvn test
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-postgres-integration-tests.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-e2e-tests.ps1
```

Recent focused checks also passed:

```bash
mvn -q test
mvn -q -pl shared/common-observability -am test
mvn -q -pl services/reporting-service -am test
mvn -q -pl services/identity-service,services/target-registry-service,services/notification-service,services/reporting-service -am test
docker compose build identity-service target-registry-service notification-service reporting-service
powershell -ExecutionPolicy Bypass -File .\scripts\run-e2e-tests.ps1
```

## Final Recommendation

Treat the current state as a strong resume-ready and demo-ready platform. For a real production deployment, schedule asymmetric signing as the first dedicated hardening task, then run the full Maven and Docker Compose test gates before release.
