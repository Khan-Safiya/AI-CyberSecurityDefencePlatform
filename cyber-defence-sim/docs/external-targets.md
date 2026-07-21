# External Targets

External staging targets must be explicitly registered and verified before assessment.

Required controls:

- Written authorization confirmation.
- Non-production environment type.
- Allowed hosts.
- Allowed paths.
- Excluded paths.
- Allowed HTTP methods.
- Request rate limit.
- Ownership verification.
- Exact base URL host and port match against the allowed-host list.
- Rejection of URL credentials, query strings, fragments, private/local addresses, metadata destinations, and ambiguous traversal paths.

The target registry persists targets in PostgreSQL and Flyway seeds the built-in sandbox target. Validation uses only local Java code and no paid service. External target activation requires the target's issued `verificationToken`; knowing only the target ID is not enough. Wrong tokens leave the target pending, and disabled or failed-ownership targets cannot be activated later. A future production HTTP ownership client should fetch `.well-known/cybersim-verification.txt` with timeouts and audit events before activation.

Registration checks do not resolve DNS; the outbound probe client described below performs its own resolution and address-safety check immediately before every connection.

## Scanning an active external target

Once a target is `ACTIVE`, automated red-team checks against it are opt-in and disabled by default
(`red-team.external-checks.enabled=false`). When enabled, `red-team-agent-service` runs a small,
fixed set of safe, read-only, black-box probes against the target's own declared scope
(`allowedHosts`/`allowedPaths`/`excludedPaths`/`allowedHttpMethods`) rather than against the
built-in sandbox:

- Missing authentication (GET on the declared path with no credentials succeeds).
- Missing recommended security headers (`Strict-Transport-Security`, `X-Content-Type-Options`).
- Exposed debug/error detail (stack traces, verbose error pages) in the response body.
- Missing rate limiting (repeated requests never return `429`).

Every probe is still gated per-request by `policy-engine-service`, which now resolves the target's
declared scope from `target-registry-service` (via a new internal, service-JWT-protected
`GET /internal/targets/{id}` endpoint) instead of only recognizing the built-in sandbox path
prefixes. A request outside the target's declared host, path, excluded-path, or HTTP-method scope
is denied by default.

The actual outbound HTTP call is made by `shared/common-http`'s `SafeOutboundHttpClient`, which
re-resolves the hostname and rejects private/loopback/link-local/multicast/reserved/CGNAT address
ranges immediately before every connection, never follows redirects automatically, and enforces a
short timeout and a response-size cap. **Known limitation:** resolution and connection are not
atomic - the JDK `HttpClient` does not expose a pinned-address connect hook - so a narrow
DNS-rebinding window remains between the address check and the TCP connect. Closing that gap
fully would require a custom socket-level resolver/connector; this is a good next hardening step,
not yet implemented.

External targets remain **report-only**: nothing in this platform automatically patches a real
external site. `remediation-service` continues to auto-patch only the built-in sandbox.
