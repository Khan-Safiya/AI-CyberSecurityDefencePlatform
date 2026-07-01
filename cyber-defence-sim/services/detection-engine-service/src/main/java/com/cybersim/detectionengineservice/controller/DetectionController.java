package com.cybersim.detectionengineservice.controller;

import com.cybersim.detectionengineservice.model.DetectionEventRecord;
import com.cybersim.detectionengineservice.model.DetectionRuleRecord;
import com.cybersim.detectionengineservice.store.DetectionEventStore;
import com.cybersim.detectionengineservice.store.DetectionRuleStore;
import com.cybersim.shared.dto.DetectionEventCreateRequest;
import com.cybersim.shared.dto.DetectionEventResponse;
import com.cybersim.shared.dto.DetectionRuleCreateRequest;
import com.cybersim.shared.dto.DetectionRuleResponse;
import com.cybersim.shared.dto.DetectionRuleUpdateRequest;
import com.cybersim.shared.observability.ApiErrors;
import com.cybersim.shared.exceptions.ConflictException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

@RestController
public class DetectionController {
    private final DetectionRuleStore ruleStore;
    private final DetectionEventStore eventStore;

    public DetectionController(DetectionRuleStore ruleStore, DetectionEventStore eventStore) {
        this.ruleStore = ruleStore;
        this.eventStore = eventStore;
    }

    @PostMapping("/detection-rules")
    public ResponseEntity<DetectionRuleResponse> createRule(@Valid @RequestBody DetectionRuleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleStore.save(DetectionRuleRecord.from(request)).toResponse());
    }

    @GetMapping("/detection-rules")
    public List<DetectionRuleResponse> rules() {
        return ruleStore.findAll().stream().map(DetectionRuleRecord::toResponse).toList();
    }

    @GetMapping("/detection-rules/{id}")
    public ResponseEntity<Object> rule(@PathVariable UUID id) {
        return ruleStore.findById(id)
                .<ResponseEntity<Object>>map(rule -> ResponseEntity.ok(rule.toResponse()))
                .orElseGet(() -> ApiErrors.response(HttpStatus.NOT_FOUND, "Detection rule not found", "/detection-rules/" + id));
    }

    @PatchMapping("/detection-rules/{id}")
    public ResponseEntity<Object> updateRule(@PathVariable UUID id, @Valid @RequestBody DetectionRuleUpdateRequest request) {
        return ruleStore.findById(id)
                .<ResponseEntity<Object>>map(rule -> ResponseEntity.ok(ruleStore.save(rule.update(request)).toResponse()))
                .orElseGet(() -> ApiErrors.response(HttpStatus.NOT_FOUND, "Detection rule not found", "/detection-rules/" + id));
    }

    @DeleteMapping("/detection-rules/{id}")
    public ResponseEntity<Object> deleteRule(@PathVariable UUID id) {
        if (ruleStore.findById(id).isEmpty()) {
            return ApiErrors.response(HttpStatus.NOT_FOUND, "Detection rule not found", "/detection-rules/" + id);
        }
        ruleStore.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/detections")
    public ResponseEntity<DetectionEventResponse> createDetection(
            @Valid @RequestBody DetectionEventCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey
    ) {
        if (idempotencyKey != null) {
            DetectionEventRecord existing = eventStore.findById(idempotencyKey).orElse(null);
            if (existing != null) {
                if (!existing.sameEvent(request)) {
                    throw new ConflictException("Idempotency key already belongs to a different detection event");
                }
                return ResponseEntity.ok(existing.toResponse());
            }
        }
        UUID eventId = idempotencyKey == null ? UUID.randomUUID() : idempotencyKey;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventStore.save(DetectionEventRecord.from(request, eventId)).toResponse());
    }

    public ResponseEntity<DetectionEventResponse> createDetection(DetectionEventCreateRequest request) {
        return createDetection(request, null);
    }

    @GetMapping("/detections/{id}")
    public ResponseEntity<Object> detection(@PathVariable UUID id) {
        return eventStore.findById(id)
                .<ResponseEntity<Object>>map(event -> ResponseEntity.ok(event.toResponse()))
                .orElseGet(() -> ApiErrors.response(HttpStatus.NOT_FOUND, "Detection event not found", "/detections/" + id));
    }

    @GetMapping("/simulations/{id}/detections")
    public List<DetectionEventResponse> detections(@PathVariable UUID id) {
        return eventStore.findBySimulationId(id).stream().map(DetectionEventRecord::toResponse).toList();
    }
}
