package com.cybersim.redteamagentservice.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RedTeamPersistenceIntegrationTest {
    @Autowired private ConsumedMessageStore inbox;

    @Test
    void storesConsumedMessageId() {
        UUID messageId = UUID.fromString("00000000-0000-0000-0000-000000000601");

        inbox.record(messageId);

        assertThat(inbox.contains(messageId)).isTrue();
    }
}
