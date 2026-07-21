# Security Boundaries

Red-team actions are simulation checks only. They must be scoped, harmless, rate-limited, auditable, and approved by the policy engine.

Blocked behavior:

- Real exploit payloads.
- Malware or persistence.
- Credential theft.
- Destructive actions.
- Arbitrary host scanning.
- Shell command execution.
- Denial-of-service behavior.
- Production target testing.

The policy engine denies actions by default when input is missing, unsupported, or outside `/demo/**` or approved internal patch routes.

## Identity Boundary

Human users authenticate with signed JWT bearer tokens from the identity service. Passwords are stored as BCrypt hashes in PostgreSQL. Demo accounts are local-development defaults only and must be changed through `.env` before any non-local use.

User JWTs are accepted only when the signature, issuer, expiry, and role rules match the receiving service. During planned rotation, validators may accept both `JWT_SECRET` and `JWT_PREVIOUS_SECRET`, but new tokens are signed only with the active secret and include the active `kid` header.

## Service Boundary

Internal services authenticate with short-lived, audience-restricted service JWTs. The receiving service checks both the machine role and the audience before allowing an internal operation. This limits damage if one token is leaked because the token is valid only briefly and only for one intended destination.

The old runtime shared static service token and obsolete client headers are removed from active workflow calls. User traffic cannot become internal traffic by adding `X-Service-Token`; the gateway strips that header.

## External Target Boundary

External targets are allowed only as explicitly authorized staging targets. Base URLs are rejected when they contain credentials, query strings, fragments, local/private/metadata destinations, single-label hosts, or ambiguous path traversal. Pending external targets require the issued verification token before they can become active.

Registration validation alone is not enough for future outbound HTTP clients. Every outbound connection and redirect must resolve the host immediately before connecting, reject private/local/reserved addresses after resolution, apply timeouts, and cap response size to reduce SSRF and DNS rebinding risk.

## Production Signing Recommendation

The local scaffold uses HS256 shared secrets because it is free, simple, and works in Docker Compose. Production should move user and service JWTs to asymmetric signing, such as RS256 or ES256, using a private signing key in the issuer and public verification keys in consumers. This removes the need to distribute signing secrets to every service and makes key rotation safer.

This can still be implemented with free tooling: Java KeyStore or PEM files, Spring Security, Nimbus JOSE JWT, and Docker secrets or mounted local files. Paid cloud key management is optional, not required.
