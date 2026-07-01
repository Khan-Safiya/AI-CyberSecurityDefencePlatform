package com.cybersim.scoringservice.persistence;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="consumed_messages")
class ConsumedMessageEntity {
    @Id @Column(name="message_id") private UUID messageId;
    @Column(name="consumed_at",nullable=false) private Instant consumedAt;
    protected ConsumedMessageEntity() { }
    ConsumedMessageEntity(UUID id, Instant at) { messageId=id; consumedAt=at; }
}
