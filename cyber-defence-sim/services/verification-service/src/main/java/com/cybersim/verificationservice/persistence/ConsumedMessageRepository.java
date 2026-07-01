package com.cybersim.verificationservice.persistence;

import org.springframework.data.repository.CrudRepository;
import java.util.UUID;

interface ConsumedMessageRepository extends CrudRepository<ConsumedMessageEntity, UUID> { }
