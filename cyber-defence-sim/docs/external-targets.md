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

The target registry currently models these controls in memory. The production implementation should persist targets and implement the `.well-known/cybersim-verification.txt` token check with timeouts and audit events.
