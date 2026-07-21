# API

Implemented route groups:

- `/auth/**`
- `/targets/**`
- `/integration/**`
- `/policies/**`
- `/demo/**`
- `/internal/patches/**`
- `/simulations/**`
- `/scenarios/**`
- `/agents/**`
- `/red-team/**`
- `/blue-team/**`
- `/vulnerabilities/**`
- `/detection-rules/**`
- `/remediations/**`
- `/verifications/**`
- `/simulations/{id}/scores`
- `/simulations/{id}/events`
- `/dashboard/**`
- `/reports/**`

Each service includes Springdoc dependencies for OpenAPI UI when running. The shared observability module auto-registers the common generated OpenAPI security schemes below.

## User Authentication

Human users log in through `POST /auth/login`. The identity service returns a short-lived signed JWT. The token contains the user ID, username, roles, issuer, issued/expiry timestamps, and a `kid` header from `JWT_ACTIVE_KEY_ID`.

User-facing services validate:

- `Authorization: Bearer <access-token>`
- HS256 signature using `JWT_SECRET`
- optional previous secret from `JWT_PREVIOUS_SECRET` during rotation
- expected issuer
- token expiry
- route roles

Current role behavior:

- `ADMIN`: full platform administration and write access.
- `SIMULATION_OPERATOR`: simulation and target workflow write access.
- `AUDITOR`: read-only visibility where supported.

Gateway rules:

- Any valid platform role may read `/api/**`.
- Only `ADMIN` and `SIMULATION_OPERATOR` may write to `/api/**`.
- `/api/admin/**` requires `ADMIN`.
- Unknown gateway route families return 404.
- User-supplied `X-Service-Token` is stripped and never forwarded.

## Service Authentication

Internal workflow operations use two-minute signed service JWTs, not the old shared static service token. These tokens include a service role and an exact audience naming the receiving service.

Examples:

- Red-team stage completion uses `SERVICE_RED_TEAM` for the `simulation-orchestrator-service` audience.
- Detection completion uses `SERVICE_DETECTION` for the `simulation-orchestrator-service` audience.
- Remediation patching uses a remediation service token for the `target-system-service` audience.
- Verification synchronization uses `SERVICE_VERIFICATION` for vulnerability and remediation update endpoints.
- Scoring submission uses `SERVICE_SCORE_PRODUCER` for the `scoring-service` audience.
- Webhook delivery management uses `SERVICE_NOTIFICATION` for the `notification-service` audience.

Wrong audience, wrong role, missing token, expired token, and legacy shared-token style calls are rejected.

## Security Scheme For OpenAPI

Generated OpenAPI descriptions expose a bearer JWT scheme for user-facing routes:

```yaml
components:
  securitySchemes:
    userBearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

Internal-only routes expose a separate service JWT scheme. They should not be presented as normal user operations.

```yaml
components:
  securitySchemes:
    serviceBearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
      description: Short-lived audience-restricted service JWT.
```
