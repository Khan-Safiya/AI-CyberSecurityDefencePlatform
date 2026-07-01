package com.cybersim.eventlogservice.persistence;

import com.cybersim.eventlogservice.store.EventStore;
import com.cybersim.shared.events.PlatformEvent;
import com.cybersim.shared.exceptions.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventPersistenceIntegrationTest {
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Autowired
    private EventStore eventStore;

    @Test
    void persistsPayloadAndReturnsTimelineInTimestampOrder() {
        PlatformEvent later = event("00000000-0000-0000-0000-000000000402", "simulation.completed",
                "2026-06-28T10:02:00Z", Map.of("score", 490));
        PlatformEvent earlier = event("00000000-0000-0000-0000-000000000401", "simulation.started",
                "2026-06-28T10:00:00Z", Map.of("mode", "INTERNAL_SANDBOX"));

        eventStore.append(later);
        eventStore.append(earlier);

        assertThat(eventStore.findBySimulationId(SIMULATION_ID))
                .extracting(PlatformEvent::eventType)
                .containsExactly("simulation.started", "simulation.completed");
        assertThat(eventStore.findBySimulationId(SIMULATION_ID).getFirst().payload())
                .containsEntry("mode", "INTERNAL_SANDBOX");
    }

    @Test
    void duplicateEventIdCannotReplaceExistingAuditEvent() {
        PlatformEvent original = event("00000000-0000-0000-0000-000000000403", "simulation.started",
                "2026-06-28T10:00:00Z", Map.of());
        eventStore.append(original);

        assertThatThrownBy(() -> eventStore.append(new PlatformEvent(
                original.eventId(), "simulation.tampered", SIMULATION_ID, null, null,
                "unknown-service", "duplicate", Instant.parse("2026-06-28T10:03:00Z"), Map.of()
        ))).isInstanceOf(ConflictException.class);

        assertThat(eventStore.findBySimulationId(SIMULATION_ID).getFirst().eventType())
                .isEqualTo("simulation.started");
    }

    @Test
    void packagedPostgresqlMigrationRejectsUpdatesAndDeletes() throws IOException {
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("db/postgresql/V2__enforce_append_only_events.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("BEFORE UPDATE OR DELETE ON events");
        }
    }

    private PlatformEvent event(String id, String type, String timestamp, Map<String, Object> payload) {
        return new PlatformEvent(UUID.fromString(id), type, SIMULATION_ID, null,
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "simulation-orchestrator-service", "event-persistence-test", Instant.parse(timestamp), payload);
    }
}
