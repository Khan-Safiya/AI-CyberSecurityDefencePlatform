package com.cybersim.targetregistryservice.controller;

import com.cybersim.shared.dto.ApiErrorResponse;
import com.cybersim.shared.dto.OwnershipVerificationRequest;
import com.cybersim.shared.dto.TargetMode;
import com.cybersim.shared.dto.TargetRegistrationRequest;
import com.cybersim.shared.dto.TargetResponse;
import com.cybersim.shared.exceptions.GlobalApiExceptionHandler;
import com.cybersim.shared.observability.CorrelationIdFilter;
import com.cybersim.targetregistryservice.model.TargetRecord;
import com.cybersim.targetregistryservice.store.TargetStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TargetRegistryControllerTest {
    private TargetRegistryController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new TargetRegistryController(new InMemoryTargetStore());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void unauthorizedExternalTargetCannotBeActivatedByVerification() {
        TargetRegistrationRequest request = new TargetRegistrationRequest(
                "Unauthorized production-like target",
                "Should stay disabled",
                TargetMode.EXTERNAL_STAGING_TARGET,
                "https://prod.example.com",
                "PRODUCTION",
                List.of("prod.example.com"),
                List.of("/"),
                List.of(),
                List.of("GET"),
                10,
                false
        );

        ResponseEntity<Object> created = controller.create(request);
        TargetResponse target = (TargetResponse) created.getBody();

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(target).isNotNull();
        assertThat(target.status()).isEqualTo("DISABLED");
        assertThat(target.ownershipVerificationStatus()).isEqualTo("FAILED");

        ResponseEntity<Object> verified = controller.verify(target.id(),
                new OwnershipVerificationRequest(target.verificationToken()));

        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertStandardError(verified, 400, "Bad Request", "/targets/" + target.id() + "/verify-ownership");
        assertThat(((TargetResponse) controller.get(target.id()).getBody()).status()).isEqualTo("DISABLED");
    }

    @Test
    void authorizedStagingTargetCanBeVerified() {
        TargetRegistrationRequest request = new TargetRegistrationRequest(
                "Authorized staging target",
                "Owned staging app",
                TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com",
                "STAGING",
                List.of("staging.example.com"),
                List.of("/demo/**"),
                List.of(),
                List.of("GET"),
                10,
                true
        );

        TargetResponse target = (TargetResponse) controller.create(request).getBody();

        ResponseEntity<Object> verified = controller.verify(target.id(),
                new OwnershipVerificationRequest(target.verificationToken()));

        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        TargetResponse verifiedTarget = (TargetResponse) verified.getBody();
        assertThat(verifiedTarget.status()).isEqualTo("ACTIVE");
        assertThat(verifiedTarget.ownershipVerificationStatus()).isEqualTo("VERIFIED");
    }

    @Test
    void missingTargetReturnsStandardError() {
        java.util.UUID id = java.util.UUID.fromString("00000000-0000-0000-0000-000000009999");

        ResponseEntity<Object> response = controller.get(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertStandardError(response, 404, "Not Found", "/targets/" + id);
    }

    @Test
    void disablingMissingTargetReturnsStandardError() {
        java.util.UUID id = java.util.UUID.fromString("00000000-0000-0000-0000-000000009998");

        ResponseEntity<Object> response = controller.disable(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertStandardError(response, 404, "Not Found", "/targets/" + id + "/disable");
    }

    @Test
    void invalidTargetRegistrationIsRejectedBeforeStorage() throws Exception {
        mockMvc.perform(post("/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "mode": "EXTERNAL_STAGING_TARGET",
                                  "baseUrl": "file:///etc/passwd",
                                  "environmentType": "STAGING",
                                  "allowedHosts": [],
                                  "allowedPaths": [],
                                  "allowedHttpMethods": [],
                                  "maxRequestsPerMinute": 0,
                                  "writtenAuthorizationConfirmed": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/targets"));
    }

    @Test
    void productionBlockedTargetCannotBeActivated() {
        TargetRegistrationRequest request = new TargetRegistrationRequest(
                "Blocked production target",
                "Must remain disabled",
                TargetMode.EXTERNAL_STAGING_TARGET,
                "https://blocked.example.com",
                "PRODUCTION_BLOCKED",
                List.of("blocked.example.com"),
                List.of("/"),
                List.of(),
                List.of("GET"),
                10,
                true
        );

        TargetResponse target = (TargetResponse) controller.create(request).getBody();

        assertThat(target.status()).isEqualTo("DISABLED");
        assertThat(controller.verify(target.id(), new OwnershipVerificationRequest(target.verificationToken())).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void externalTargetCannotBeVerifiedWithWrongToken() {
        TargetRegistrationRequest request = new TargetRegistrationRequest(
                "Authorized staging target",
                "Owned staging app",
                TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com",
                "STAGING",
                List.of("staging.example.com"),
                List.of("/demo/**"),
                List.of(),
                List.of("GET"),
                10,
                true
        );

        TargetResponse target = (TargetResponse) controller.create(request).getBody();

        ResponseEntity<Object> verified = controller.verify(target.id(),
                new OwnershipVerificationRequest("wrong-token"));

        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertStandardError(verified, 400, "Bad Request", "/targets/" + target.id() + "/verify-ownership");
        TargetResponse unchanged = (TargetResponse) controller.get(target.id()).getBody();
        assertThat(unchanged.status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(unchanged.ownershipVerificationStatus()).isEqualTo("PENDING");
    }

    @Test
    void privateExternalTargetIsRejectedBeforeStorage() {
        TargetRegistrationRequest request = new TargetRegistrationRequest(
                "Private target",
                "Must be blocked as an SSRF destination",
                TargetMode.EXTERNAL_STAGING_TARGET,
                "http://127.0.0.1",
                "STAGING",
                List.of("127.0.0.1"),
                List.of("/"),
                List.of(),
                List.of("GET"),
                10,
                true
        );

        ResponseEntity<Object> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertStandardError(response, 400, "Bad Request", "/targets");
        assertThat(((ApiErrorResponse) response.getBody()).message()).contains("Unsafe target scope");
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

    private static final class InMemoryTargetStore implements TargetStore {
        private final Map<UUID, TargetRecord> targets = new ConcurrentHashMap<>();

        @Override
        public TargetRecord save(TargetRecord target) {
            targets.put(target.id(), target);
            return target;
        }

        @Override
        public Optional<TargetRecord> findById(UUID id) {
            return Optional.ofNullable(targets.get(id));
        }

        @Override
        public List<TargetRecord> findAll() {
            return List.copyOf(targets.values());
        }
    }
}
