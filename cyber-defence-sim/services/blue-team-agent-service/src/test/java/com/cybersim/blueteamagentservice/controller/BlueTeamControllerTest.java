package com.cybersim.blueteamagentservice.controller;

import com.cybersim.blueteamagentservice.client.RemediationClient;
import com.cybersim.shared.dto.RemediationResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BlueTeamControllerTest {
    private static final UUID SIMULATION_ID = UUID.randomUUID();
    private static final UUID ROUND_ONE = UUID.randomUUID();
    private static final UUID ROUND_TWO = UUID.randomUUID();

    @Test
    void planForSimulationReturnsRealRemediationsFromRemediationService() {
        RemediationResponse roundOneRemediation = remediation(ROUND_ONE, "APPLIED");
        RemediationResponse roundTwoRemediation = remediation(ROUND_TWO, "PROPOSED");
        BlueTeamController controller = new BlueTeamController(simulationId ->
                List.of(roundOneRemediation, roundTwoRemediation));

        List<RemediationResponse> plan = controller.plan(SIMULATION_ID);

        assertThat(plan).containsExactly(roundOneRemediation, roundTwoRemediation);
    }

    @Test
    void planForRoundFiltersToOnlyThatRound() {
        RemediationResponse roundOneRemediation = remediation(ROUND_ONE, "APPLIED");
        RemediationResponse roundTwoRemediation = remediation(ROUND_TWO, "PROPOSED");
        BlueTeamController controller = new BlueTeamController(simulationId ->
                List.of(roundOneRemediation, roundTwoRemediation));

        List<RemediationResponse> plan = controller.plan(SIMULATION_ID, ROUND_TWO);

        assertThat(plan).containsExactly(roundTwoRemediation);
    }

    @Test
    void planForSimulationIsEmptyWhenRemediationServiceHasNothing() {
        BlueTeamController controller = new BlueTeamController(simulationId -> List.of());

        assertThat(controller.plan(SIMULATION_ID)).isEmpty();
    }

    @Test
    void staticCapabilityPlanIsUnchanged() {
        BlueTeamController controller = new BlueTeamController(simulationId -> List.of());

        assertThat(controller.plan().get("actions")).isEqualTo(List.of(
                "TRIAGE_FINDING", "RECOMMEND_REMEDIATION", "APPLY_REMEDIATION", "VERIFY_REMEDIATION"));
    }

    private static RemediationResponse remediation(UUID roundId, String status) {
        return new RemediationResponse(UUID.randomUUID(), SIMULATION_ID, roundId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "REQUIRE_AUTH", "Patched",
                status, null, Instant.now(), Instant.now(), null, null, null, null);
    }
}
