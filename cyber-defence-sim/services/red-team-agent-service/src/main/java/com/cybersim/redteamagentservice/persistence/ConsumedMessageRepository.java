package com.cybersim.redteamagentservice.persistence;

import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

interface ConsumedMessageRepository extends CrudRepository<ConsumedMessageEntity, UUID> {
}
