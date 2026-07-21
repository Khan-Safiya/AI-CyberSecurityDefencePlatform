package com.cybersim.targetsystemservice.controller;

import com.cybersim.shared.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoTargetControllerTest {
    private final DemoTargetController controller = new DemoTargetController();

    @Test
    void adminReportStartsWithMissingAuthentication() {
        ResponseEntity<Object> beforePatch = controller.adminReport();

        assertThat(beforePatch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(beforePatch.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) beforePatch.getBody()).get("issue")).isEqualTo("missing authentication");

    }

    @Test
    void applyingAuthPatchMakesAdminReportRequireAuthentication() {
        ResponseEntity<Object> patch = controller.applyPatch("auth-required");
        ResponseEntity<Object> afterPatch = controller.adminReport();

        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterPatch.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertStandardError(afterPatch, 401, "Unauthorized", "/demo/admin/report");
    }

    @Test
    void applyingDebugPatchDisablesDebugEndpoint() {
        assertThat(controller.debugConfig().getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Object> patch = controller.applyPatch("disable-debug-endpoint");
        ResponseEntity<Object> afterPatch = controller.debugConfig();

        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterPatch.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertStandardError(afterPatch, 404, "Not Found", "/demo/debug/config");
        assertThat(((Map<?, ?>) controller.status().getBody()).get("disable-debug-endpoint")).isEqualTo(true);
    }

    @Test
    void rollbackRequiresTokenAndRestoresVulnerableSandboxState() {
        controller.applyPatch("auth-required");

        ResponseEntity<Object> rolledBack = controller.rollbackPatch("auth-required");

        assertThat(rolledBack.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.adminReport().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) controller.status().getBody()).get("auth-required")).isEqualTo(false);
    }

    @Test
    void unknownPatchNameReturnsNotFound() {
        ResponseEntity<Object> response = controller.applyPatch("unknown-patch");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertStandardError(response, 404, "Not Found", "/internal/patches/unknown-patch");
    }

    private void assertStandardError(ResponseEntity<Object> response, int status, String error, String path) {
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.status()).isEqualTo(status);
        assertThat(body.error()).isEqualTo(error);
        assertThat(body.message()).isNotBlank();
        assertThat(body.path()).isEqualTo(path);
    }
}
