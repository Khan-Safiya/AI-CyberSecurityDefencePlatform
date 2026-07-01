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

The target registry currently models these controls in memory. Validation uses only local Java code and no paid service. The production implementation should persist targets and implement the `.well-known/cybersim-verification.txt` token check with timeouts and audit events.

Registration checks do not resolve DNS. Every future outbound HTTP connection and redirect must resolve the destination, reject private/local/reserved addresses after resolution, pin the validated address for that connection, and apply strict time and response-size limits. This is required to address DNS rebinding and redirect-based SSRF.
