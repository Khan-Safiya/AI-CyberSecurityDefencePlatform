package com.cybersim.verificationservice.workflow;

import com.cybersim.shared.dto.RemediationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpVerificationWorkflowClientTest {
    private static final UUID REMEDIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID VULNERABILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");

    @Test
    void loadsRemediationChecksPatchAndSynchronizesOutcome() {
        RestClient.Builder remediationBuilder = RestClient.builder().baseUrl("http://remediation");
        RestClient.Builder vulnerabilityBuilder = RestClient.builder().baseUrl("http://vulnerability");
        RestClient.Builder targetBuilder = RestClient.builder().baseUrl("http://target");
        MockRestServiceServer remediationServer = MockRestServiceServer.bindTo(remediationBuilder).build();
        MockRestServiceServer vulnerabilityServer = MockRestServiceServer.bindTo(vulnerabilityBuilder).build();
        MockRestServiceServer targetServer = MockRestServiceServer.bindTo(targetBuilder).build();
        HttpVerificationWorkflowClient client = new HttpVerificationWorkflowClient(
                remediationBuilder.build(), vulnerabilityBuilder.build(), targetBuilder.build(),
                RestClient.create("http://simulation"));

        remediationServer.expect(requestTo("http://remediation/remediations/" + REMEDIATION_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(remediationJson(), MediaType.APPLICATION_JSON));
        targetServer.expect(requestTo("http://target/internal/patches/status"))
                .andRespond(withSuccess("{\"auth-required\":true}", MediaType.APPLICATION_JSON));
        remediationServer.expect(requestTo("http://remediation/internal/remediations/" + REMEDIATION_ID + "/verification-result"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        vulnerabilityServer.expect(requestTo("http://vulnerability/internal/vulnerabilities/" + VULNERABILITY_ID + "/verification-result"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        RemediationResponse remediation = client.findRemediation(REMEDIATION_ID).orElseThrow();
        VerificationCheckResult result = client.verifyPatch(remediation);

        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(client.synchronizeOutcome(remediation, result)).isTrue();
        remediationServer.verify();
        vulnerabilityServer.verify();
        targetServer.verify();
    }

    private String remediationJson() {
        return """
                {
                  "id":"00000000-0000-0000-0000-000000000801",
                  "simulationId":"00000000-0000-0000-0000-000000000201",
                  "roundId":null,
                  "vulnerabilityId":"00000000-0000-0000-0000-000000000501",
                  "detectionId":"00000000-0000-0000-0000-000000000701",
                  "agentId":"00000000-0000-0000-0000-000000000302",
                  "targetId":"00000000-0000-0000-0000-000000000101",
                  "remediationType":"AUTH_REQUIRED",
                  "patchSummary":"Require authentication",
                  "status":"APPLIED",
                  "outcomeSummary":"Applied",
                  "createdAt":"2026-06-29T10:00:00Z",
                  "updatedAt":"2026-06-29T10:01:00Z",
                  "approvedAt":"2026-06-29T10:00:30Z",
                  "appliedAt":"2026-06-29T10:01:00Z",
                  "verifiedAt":null,
                  "rolledBackAt":null
                }
                """;
    }
}
