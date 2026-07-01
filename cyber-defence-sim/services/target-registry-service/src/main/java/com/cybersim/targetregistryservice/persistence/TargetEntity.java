package com.cybersim.targetregistryservice.persistence;

import com.cybersim.shared.dto.TargetMode;
import com.cybersim.targetregistryservice.model.TargetRecord;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "targets")
class TargetEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TargetMode mode;

    @Column(name = "base_url", nullable = false, length = 2048)
    private String baseUrl;

    @Column(name = "environment_type", nullable = false, length = 50)
    private String environmentType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "target_allowed_hosts", joinColumns = @JoinColumn(name = "target_id"))
    @OrderColumn(name = "position")
    @Column(name = "host_value", nullable = false, length = 253)
    private List<String> allowedHosts = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "target_allowed_paths", joinColumns = @JoinColumn(name = "target_id"))
    @OrderColumn(name = "position")
    @Column(name = "path_value", nullable = false, length = 2048)
    private List<String> allowedPaths = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "target_excluded_paths", joinColumns = @JoinColumn(name = "target_id"))
    @OrderColumn(name = "position")
    @Column(name = "path_value", nullable = false, length = 2048)
    private List<String> excludedPaths = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "target_allowed_http_methods", joinColumns = @JoinColumn(name = "target_id"))
    @OrderColumn(name = "position")
    @Column(name = "method_value", nullable = false, length = 16)
    private List<String> allowedHttpMethods = new ArrayList<>();

    @Column(name = "max_requests_per_minute", nullable = false)
    private int maxRequestsPerMinute;

    @Column(name = "written_authorization_confirmed", nullable = false)
    private boolean writtenAuthorizationConfirmed;

    @Column(name = "ownership_verification_status", nullable = false, length = 50)
    private String ownershipVerificationStatus;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "verification_token", nullable = false, length = 128)
    private String verificationToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TargetEntity() {
    }

    private TargetEntity(TargetRecord record) {
        id = record.id();
        name = record.name();
        description = record.description();
        mode = record.mode();
        baseUrl = record.baseUrl();
        environmentType = record.environmentType();
        allowedHosts = new ArrayList<>(record.allowedHosts());
        allowedPaths = new ArrayList<>(record.allowedPaths());
        excludedPaths = new ArrayList<>(record.excludedPaths());
        allowedHttpMethods = new ArrayList<>(record.allowedHttpMethods());
        maxRequestsPerMinute = record.maxRequestsPerMinute();
        writtenAuthorizationConfirmed = record.writtenAuthorizationConfirmed();
        ownershipVerificationStatus = record.ownershipVerificationStatus();
        status = record.status();
        verificationToken = record.verificationToken();
        createdAt = record.createdAt();
        updatedAt = record.updatedAt();
    }

    static TargetEntity from(TargetRecord record) {
        return new TargetEntity(record);
    }

    TargetRecord toRecord() {
        return new TargetRecord(id, name, description, mode, baseUrl, environmentType, List.copyOf(allowedHosts),
                List.copyOf(allowedPaths), List.copyOf(excludedPaths), List.copyOf(allowedHttpMethods),
                maxRequestsPerMinute, writtenAuthorizationConfirmed, ownershipVerificationStatus, status,
                verificationToken, createdAt, updatedAt);
    }
}
