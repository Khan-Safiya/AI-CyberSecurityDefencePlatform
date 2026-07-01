package com.cybersim.detectionengineservice.persistence;

import com.cybersim.detectionengineservice.model.DetectionRuleRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "detection_rules")
class DetectionRuleEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(name = "event_pattern", nullable = false, length = 2000)
    private String eventPattern;
    @Column(nullable = false, length = 20)
    private String severity;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DetectionRuleEntity() {
    }

    private DetectionRuleEntity(DetectionRuleRecord rule) {
        id = rule.id();
        name = rule.name();
        description = rule.description();
        eventPattern = rule.eventPattern();
        severity = rule.severity();
        enabled = rule.enabled();
        createdAt = rule.createdAt();
        updatedAt = rule.updatedAt();
    }

    static DetectionRuleEntity from(DetectionRuleRecord rule) {
        return new DetectionRuleEntity(rule);
    }

    DetectionRuleRecord toRecord() {
        return new DetectionRuleRecord(id, name, description, eventPattern, severity, enabled, createdAt, updatedAt);
    }
}
