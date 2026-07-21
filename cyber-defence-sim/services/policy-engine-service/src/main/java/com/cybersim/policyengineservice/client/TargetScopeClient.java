package com.cybersim.policyengineservice.client;

import com.cybersim.shared.dto.TargetResponse;

import java.util.Optional;
import java.util.UUID;

public interface TargetScopeClient {
    Optional<TargetResponse> targetScope(UUID targetId);
}
