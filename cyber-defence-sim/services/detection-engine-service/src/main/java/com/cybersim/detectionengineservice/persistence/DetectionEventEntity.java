package com.cybersim.detectionengineservice.persistence;

import com.cybersim.detectionengineservice.model.DetectionEventRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "detection_events")
class DetectionEventEntity {
    @Id
    private UUID id;
    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;
    @Column(name = "round_id")
    private UUID roundId;
    @Column(name = "target_id", nullable = false)
    private UUID targetId;
    @Column(nullable = false, length = 100)
    private String source;
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    @Column(nullable = false, length = 20)
    private String severity;
    @Column(nullable = false, length = 2000)
    private String message;
    @Column(name = "related_action_id")
    private UUID relatedActionId;
    @Column(name = "related_vulnerability_id")
    private UUID relatedVulnerabilityId;
    @Convert(converter = StringMapJsonConverter.class)
    @Column(nullable = false, length = 8000)
    private Map<String, String> metadata;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DetectionEventEntity() {
    }

    private DetectionEventEntity(DetectionEventRecord event) {
        id = event.id();
        simulationId = event.simulationId();
        roundId = event.roundId();
        targetId = event.targetId();
        source = event.source();
        eventType = event.eventType();
        severity = event.severity();
        message = event.message();
        relatedActionId = event.relatedActionId();
        relatedVulnerabilityId = event.relatedVulnerabilityId();
        metadata = event.metadata();
        createdAt = event.createdAt();
    }

    static DetectionEventEntity from(DetectionEventRecord event) {
        return new DetectionEventEntity(event);
    }

    DetectionEventRecord toRecord() {
        return new DetectionEventRecord(id, simulationId, roundId, targetId, source, eventType, severity, message,
                relatedActionId, relatedVulnerabilityId, metadata, createdAt);
    }
}
