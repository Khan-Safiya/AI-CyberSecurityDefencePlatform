package com.cybersim.scoringservice.persistence;

import com.cybersim.scoringservice.model.ScoreEventRecord;
import com.cybersim.scoringservice.store.ScoreAppendResult;
import com.cybersim.scoringservice.store.ScoreEventStore;
import com.cybersim.shared.dto.ScoreEventCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScoringPersistenceIntegrationTest {
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Autowired
    private ScoreEventStore store;
    @Autowired private ConsumedMessageStore inbox;

    @Test
    void flywayCreatesEmptyScoreEventStore() {
        assertThat(store.findBySimulationId(SIMULATION_ID)).isEmpty();
    }

    @Test
    void persistsAwardAndPreventsDuplicateSourceAward() {
        UUID sourceEventId = UUID.randomUUID();
        ScoreEventRecord event = ScoreEventRecord.from(new ScoreEventCreateRequest(
                SIMULATION_ID, UUID.randomUUID(), sourceEventId, "BLUE_FIX_VERIFIED", null));

        ScoreAppendResult first = store.append(event);
        ScoreAppendResult duplicate = store.append(ScoreEventRecord.from(new ScoreEventCreateRequest(
                SIMULATION_ID, event.roundId(), sourceEventId, "BLUE_FIX_VERIFIED", null)));

        assertThat(first.created()).isTrue();
        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.event().id()).isEqualTo(first.event().id());
        assertThat(store.findBySimulationId(SIMULATION_ID)).singleElement().satisfies(stored -> {
            assertThat(stored.points()).isEqualTo(30);
            assertThat(stored.reason()).isEqualTo("Fix verified");
        });
    }

    @Test
    void productionMigrationMakesScoreEventsAppendOnly() throws IOException {
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("db/postgresql/V2__enforce_append_only_scores.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("BEFORE UPDATE OR DELETE", "score events are append-only");
        }
    }

    @Test void persistsConsumedMessageId(){UUID id=UUID.randomUUID();inbox.record(id);assertThat(inbox.contains(id)).isTrue();}
}
