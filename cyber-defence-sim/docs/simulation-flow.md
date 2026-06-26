# Simulation Flow

The baseline demo flow is:

1. Start simulation.
2. Red team performs safe sandbox checks.
3. Findings are recorded.
4. Detection events are generated.
5. Blue team triages and recommends fixes.
6. Remediation applies safe sandbox patches.
7. Verification confirms fixed state.
8. Scores and timeline are updated.
9. Final report is generated.

The current implementation returns a completed vertical-slice response. The next phase should turn this into asynchronous RabbitMQ-driven round orchestration.
