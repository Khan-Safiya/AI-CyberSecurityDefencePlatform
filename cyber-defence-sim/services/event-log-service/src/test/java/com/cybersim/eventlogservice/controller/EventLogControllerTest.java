package com.cybersim.eventlogservice.controller;

import com.cybersim.eventlogservice.store.EventStore;
import com.cybersim.shared.events.PlatformEvent;
import com.cybersim.shared.exceptions.ConflictException;
import com.cybersim.shared.exceptions.GlobalApiExceptionHandler;
import com.cybersim.shared.observability.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventLogControllerTest {
    private MockMvc mockMvc;
    private InMemoryEventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        mockMvc = MockMvcBuilders.standaloneSetup(new EventLogController(eventStore))
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void rejectsEventWithoutRequiredAuditFields() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void acceptsValidEvent() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "00000000-0000-0000-0000-000000000201",
                                  "eventType": "simulation.started",
                                  "simulationId": "00000000-0000-0000-0000-000000000202",
                                  "targetId": "00000000-0000-0000-0000-000000000101",
                                  "producerService": "simulation-orchestrator-service",
                                  "correlationId": "event-test-1",
                                  "timestamp": "2026-06-28T10:00:00Z",
                                  "payload": {}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("00000000-0000-0000-0000-000000000201"))
                .andExpect(jsonPath("$.status").value("STORED"));
    }

    @Test
    void listsStoredEventsForSimulation() throws Exception {
        acceptsValidEvent();

        mockMvc.perform(get("/simulations/00000000-0000-0000-0000-000000000202/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("simulation.started"))
                .andExpect(jsonPath("$[0].correlationId").value("event-test-1"));
    }

    @Test
    void rejectsDuplicateEventIdAsConflict() throws Exception {
        acceptsValidEvent();

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "00000000-0000-0000-0000-000000000201",
                                  "eventType": "simulation.completed",
                                  "producerService": "simulation-orchestrator-service",
                                  "correlationId": "event-test-2",
                                  "timestamp": "2026-06-28T10:01:00Z",
                                  "payload": {}
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Event already exists: 00000000-0000-0000-0000-000000000201"));
    }

    private static final class InMemoryEventStore implements EventStore {
        private final Map<UUID, PlatformEvent> events = new ConcurrentHashMap<>();

        @Override
        public PlatformEvent append(PlatformEvent event) {
            if (events.putIfAbsent(event.eventId(), event) != null) {
                throw new ConflictException("Event already exists: " + event.eventId());
            }
            return event;
        }

        @Override
        public java.util.List<PlatformEvent> findBySimulationId(UUID simulationId) {
            return events.values().stream()
                    .filter(event -> simulationId.equals(event.simulationId()))
                    .sorted(Comparator.comparing(PlatformEvent::timestamp).thenComparing(PlatformEvent::eventId))
                    .toList();
        }

        @Override
        public java.util.List<PlatformEvent> findAll() {
            return events.values().stream()
                    .sorted(Comparator.comparing(PlatformEvent::timestamp).thenComparing(PlatformEvent::eventId))
                    .toList();
        }
    }
}
