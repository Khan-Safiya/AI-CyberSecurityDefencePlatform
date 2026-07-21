package com.cybersim.apigatewayservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GatewayProxyControllerTest {
    private MockRestServiceServer server;
    private GatewayProxyController controller;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        controller = new GatewayProxyController(builder, "http://identity", "http://targets/",
                "http://simulations", "http://dashboard");
    }

    @Test
    void forwardsIdentityPathQueryAndSafeHeadersOnly() {
        server.expect(requestTo("http://identity/auth/me?view=full"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer signed-token"))
                .andExpect(header("X-Correlation-Id", "correlation-1"))
                .andExpect(headerDoesNotExist("X-Service-Token"))
                .andRespond(withSuccess("{\"username\":\"demo-auditor\"}", MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "correlation-1"));

        MockHttpServletRequest request = request("GET", "/api/auth/me");
        request.setQueryString("view=full");
        request.addHeader("Authorization", "Bearer signed-token");
        request.addHeader("X-Correlation-Id", "correlation-1");
        request.addHeader("X-Service-Token", "must-not-cross-gateway");
        var response = controller.proxy(request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).contains("demo-auditor");
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("correlation-1");
        server.verify();
    }

    @Test
    void forwardsWriteBodyAndPreservesBackendError() {
        server.expect(requestTo("http://targets/targets"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "request-1"))
                .andExpect(content().json("{\"name\":\"sandbox\"}"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"duplicate\"}"));

        MockHttpServletRequest request = request("POST", "/api/targets");
        request.addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        request.addHeader("Idempotency-Key", "request-1");
        var response = controller.proxy(request, "{\"name\":\"sandbox\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).contains("duplicate");
        server.verify();
    }

    @Test
    void rejectsRoutesOutsideFixedAllowlist() {
        MockHttpServletRequest request = request("GET", "/api/arbitrary-host/path");
        assertThatThrownBy(() -> controller.proxy(request, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }
}
