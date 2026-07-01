package com.cybersim.targetregistryservice.persistence;

import com.cybersim.shared.dto.TargetMode;
import com.cybersim.targetregistryservice.model.TargetRecord;
import com.cybersim.targetregistryservice.store.TargetStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TargetPersistenceIntegrationTest {
    private static final UUID SANDBOX_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Autowired
    private TargetStore targetStore;

    @Test
    void flywaySeedsBuiltInSandboxTarget() {
        TargetRecord sandbox = targetStore.findById(SANDBOX_ID).orElseThrow();

        assertThat(sandbox.status()).isEqualTo("ACTIVE");
        assertThat(sandbox.ownershipVerificationStatus()).isEqualTo("VERIFIED");
        assertThat(sandbox.allowedHosts()).containsExactly("target-system-service:8080", "localhost:8104");
        assertThat(sandbox.allowedPaths()).containsExactly("/demo/**");
    }

    @Test
    void persistsAndReloadsCompleteTargetScope() {
        Instant now = Instant.now();
        TargetRecord target = new TargetRecord(
                UUID.randomUUID(),
                "Persistent staging target",
                "Integration test target",
                TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com",
                "STAGING",
                List.of("staging.example.com"),
                List.of("/api/**"),
                List.of("/api/admin/**"),
                List.of("GET", "POST"),
                120,
                true,
                "PENDING",
                "PENDING_VERIFICATION",
                UUID.randomUUID().toString(),
                now,
                now
        );

        targetStore.save(target);
        TargetRecord reloaded = targetStore.findById(target.id()).orElseThrow();

        assertThat(reloaded).usingRecursiveComparison().isEqualTo(target);
    }
}
