package com.cybersim.identityservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformUserRepository extends JpaRepository<PlatformUserEntity, UUID> {
    Optional<PlatformUserEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
