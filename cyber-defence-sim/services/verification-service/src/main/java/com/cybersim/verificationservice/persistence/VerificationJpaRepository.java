package com.cybersim.verificationservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface VerificationJpaRepository extends JpaRepository<VerificationEntity, UUID> {
    List<VerificationEntity> findBySimulationIdOrderByVerifiedAtAscIdAsc(UUID simulationId);
}
