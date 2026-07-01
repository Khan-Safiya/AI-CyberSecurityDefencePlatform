package com.cybersim.remediationservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface RemediationJpaRepository extends JpaRepository<RemediationEntity, UUID> {
    List<RemediationEntity> findBySimulationIdOrderByCreatedAtAscIdAsc(UUID simulationId);
}
