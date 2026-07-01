package com.cybersim.detectionengineservice.workflow;

import com.cybersim.detectionengineservice.model.DetectionEventRecord;
import com.cybersim.detectionengineservice.store.DetectionEventStore;
import com.cybersim.shared.dto.DetectionEventCreateRequest;
import com.cybersim.shared.dto.VulnerabilityResponse;
import com.cybersim.shared.exceptions.ConflictException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DetectionStageProcessor {
    private static final Map<String, UUID> RULE_IDS = Map.of(
            "AUTHENTICATION", UUID.fromString("00000000-0000-0000-0000-000000000601"),
            "RATE_LIMIT", UUID.fromString("00000000-0000-0000-0000-000000000601"),
            "ACCESS_CONTROL", UUID.fromString("00000000-0000-0000-0000-000000000602"),
            "INPUT_VALIDATION", UUID.fromString("00000000-0000-0000-0000-000000000603"),
            "CONFIG_EXPOSURE", UUID.fromString("00000000-0000-0000-0000-000000000604"),
            "DEPENDENCY_RISK", UUID.fromString("00000000-0000-0000-0000-000000000605"));

    private final DetectionEventStore eventStore;

    public DetectionStageProcessor(DetectionEventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int persist(UUID messageId, UUID simulationId, UUID roundId, List<VulnerabilityResponse> findings) {
        int created = 0;
        for (VulnerabilityResponse finding : findings) {
            if (!simulationId.equals(finding.simulationId()) || !roundId.equals(finding.roundId())) {
                continue;
            }
            UUID ruleId = RULE_IDS.get(finding.type());
            if (ruleId == null) {
                throw new IllegalStateException("No safe detection rule maps vulnerability type: " + finding.type());
            }
            UUID eventId = UUID.nameUUIDFromBytes((messageId + ":" + finding.id())
                    .getBytes(StandardCharsets.UTF_8));
            DetectionEventCreateRequest request = new DetectionEventCreateRequest(
                    simulationId, roundId, finding.targetId(), "RED_TEAM_FINDING", "detection.created",
                    finding.severity(), "Authorized red-team finding observed: " + finding.title(), null, finding.id(),
                    Map.of("ruleId", ruleId.toString(), "findingType", finding.type()));
            DetectionEventRecord existing = eventStore.findById(eventId).orElse(null);
            if (existing != null) {
                if (!existing.sameEvent(request)) {
                    throw new ConflictException("Deterministic detection ID belongs to different event data");
                }
                continue;
            }
            eventStore.save(DetectionEventRecord.from(request, eventId));
            created++;
        }
        return created;
    }
}
