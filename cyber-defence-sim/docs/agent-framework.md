# Agent Framework

Agents follow this contract:

- `planActions(context)`
- `requestPolicyApproval(action)`
- `executeApprovedAction(action)`
- `publishResult(result)`
- `stop()`

No red-team agent may execute an action without policy approval. The scaffold exposes red-team and blue-team planning APIs and names the allowed action types.
