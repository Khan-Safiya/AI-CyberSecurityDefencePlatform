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
