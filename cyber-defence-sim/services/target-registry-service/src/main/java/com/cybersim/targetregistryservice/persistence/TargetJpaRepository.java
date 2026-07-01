package com.cybersim.targetregistryservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface TargetJpaRepository extends JpaRepository<TargetEntity, UUID> {
}
