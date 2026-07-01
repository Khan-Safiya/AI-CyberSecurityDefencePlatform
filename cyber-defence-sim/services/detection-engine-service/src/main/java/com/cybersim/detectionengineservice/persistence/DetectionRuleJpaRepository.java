package com.cybersim.detectionengineservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface DetectionRuleJpaRepository extends JpaRepository<DetectionRuleEntity, UUID> {
    List<DetectionRuleEntity> findAllByOrderByCreatedAtAscIdAsc();
}
