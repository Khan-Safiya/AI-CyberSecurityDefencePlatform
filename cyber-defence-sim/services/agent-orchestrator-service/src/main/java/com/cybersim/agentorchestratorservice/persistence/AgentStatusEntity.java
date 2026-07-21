package com.cybersim.agentorchestratorservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_status")
class AgentStatusEntity {
    @Id
    private UUID id;

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Column(name = "team", nullable = false, length = 20)
    private String team;

    @Column(name = "agent_name", nullable = false, length = 100)
    private String agentName;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentStatusEntity() {
    }

    AgentStatusEntity(UUID id, UUID simulationId, String team, String agentName, String status, Instant updatedAt) {
        this.id = id;
        this.simulationId = simulationId;
        this.team = team;
        this.agentName = agentName;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    UUID id() {
        return id;
    }

    UUID simulationId() {
        return simulationId;
    }

    String team() {
        return team;
    }

    String agentName() {
        return agentName;
    }

    String status() {
        return status;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    void update(String agentName, String status, Instant updatedAt) {
        this.agentName = agentName;
        this.status = status;
        this.updatedAt = updatedAt;
    }
}
