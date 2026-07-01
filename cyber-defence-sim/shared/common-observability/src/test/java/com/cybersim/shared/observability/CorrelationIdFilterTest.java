package com.cybersim.shared.observability;

import com.cybersim.shared.dto.ApiErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesCorrelationIdAndMakesItAvailableDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                idDuringRequest.set(MDC.get(CorrelationIds.MDC_KEY)));

        assertThat(idDuringRequest.get()).isNotBlank();
        assertThat(response.getHeader(CorrelationIds.HEADER_NAME)).isEqualTo(idDuringRequest.get());
        assertThat(request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE)).isEqualTo(idDuringRequest.get());
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }

    @Test
    void preservesSafeCallerCorrelationIdInResponseAndErrorBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER_NAME, "simulation-42.request_7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ApiErrorResponse> error = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                error.set((ApiErrorResponse) ApiErrors.response(
                        HttpStatus.BAD_REQUEST,
                        "Invalid request",
                        "/simulations"
                ).getBody()));

        assertThat(response.getHeader(CorrelationIds.HEADER_NAME)).isEqualTo("simulation-42.request_7");
        assertThat(error.get().correlationId()).isEqualTo("simulation-42.request_7");
    }

    @Test
    void replacesUnsafeCallerCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER_NAME, "unsafe value with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        assertThat(response.getHeader(CorrelationIds.HEADER_NAME))
                .isNotBlank()
                .isNotEqualTo("unsafe value with spaces");
    }

    @Test
    void registersObservabilityAutoConfigurationForSpringBootServices() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(ObservabilityAutoConfiguration.class.getName());
    }
}
