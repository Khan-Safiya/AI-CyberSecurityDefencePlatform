package com.cybersim.shared.exceptions;

import com.cybersim.shared.observability.CorrelationIdFilter;
import com.cybersim.shared.observability.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalApiExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void returnsStandardErrorForValidationFailure() throws Exception {
        mockMvc.perform(post("/validated")
                        .header(CorrelationIds.HEADER_NAME, "validation-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(CorrelationIds.HEADER_NAME, "validation-request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/validated"))
                .andExpect(jsonPath("$.correlationId").value("validation-request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returnsStandardErrorForMalformedJson() throws Exception {
        mockMvc.perform(post("/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void returnsStandardErrorForInvalidPathVariable() throws Exception {
        mockMvc.perform(get("/targets/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request parameter is missing or invalid"));
    }

    @Test
    void returnsStandardErrorForUnsupportedMethod() throws Exception {
        mockMvc.perform(get("/only-post"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("HTTP method is not supported for this endpoint"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database-password")
                )));
    }

    @Test
    void registersExceptionHandlingAutoConfigurationForSpringBootServices() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(ExceptionHandlingAutoConfiguration.class.getName());
    }

    @RestController
    static class TestController {
        @PostMapping("/validated")
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/targets/{id}")
        UUID target(@PathVariable UUID id) {
            return id;
        }

        @PostMapping("/only-post")
        void onlyPost() {
        }

        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException("database-password=must-not-leak");
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
