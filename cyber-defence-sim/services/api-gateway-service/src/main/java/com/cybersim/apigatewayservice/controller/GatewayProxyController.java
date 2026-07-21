package com.cybersim.apigatewayservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class GatewayProxyController {
    private static final List<String> FORWARDED_REQUEST_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION, HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE,
            "Idempotency-Key", "X-Correlation-Id"
    );
    private static final List<String> FORWARDED_RESPONSE_HEADERS = List.of(
            HttpHeaders.CONTENT_TYPE, HttpHeaders.CACHE_CONTROL, "X-Correlation-Id"
    );

    private final RestClient client;
    private final Map<String, String> destinations;

    public GatewayProxyController(
            RestClient.Builder builder,
            @Value("${gateway.routes.identity}") String identityUrl,
            @Value("${gateway.routes.targets}") String targetUrl,
            @Value("${gateway.routes.simulations}") String simulationUrl,
            @Value("${gateway.routes.dashboard}") String dashboardUrl
    ) {
        client = builder.build();
        destinations = Map.of(
                "auth", withoutTrailingSlash(identityUrl),
                "targets", withoutTrailingSlash(targetUrl),
                "simulations", withoutTrailingSlash(simulationUrl),
                "dashboard", withoutTrailingSlash(dashboardUrl)
        );
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        @RequestBody(required = false) byte[] body) {
        URI destination = destination(request);
        try {
            RestClient.RequestBodySpec outbound = client.method(HttpMethod.valueOf(request.getMethod()))
                    .uri(destination)
                    .headers(headers -> copyRequestHeaders(request, headers));
            if (body != null && body.length > 0) {
                outbound.body(body);
            }
            return outbound.exchange((ignored, response) -> {
                HttpHeaders headers = new HttpHeaders();
                FORWARDED_RESPONSE_HEADERS.forEach(name -> {
                    List<String> values = response.getHeaders().get(name);
                    if (values != null) {
                        headers.put(name, values);
                    }
                });
                return new ResponseEntity<>(response.getBody().readAllBytes(), headers, response.getStatusCode());
            });
        } catch (RestClientException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Downstream service is unavailable", exception);
        }
    }

    private URI destination(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String remainder = requestPath.substring("/api/".length());
        int slash = remainder.indexOf('/');
        String route = slash < 0 ? remainder : remainder.substring(0, slash);
        String baseUrl = destinations.get(route);
        if (baseUrl == null) {
            throw new ResponseStatusException(NOT_FOUND, "Gateway route not found");
        }
        String downstreamPath = requestPath.substring("/api".length());
        String query = request.getQueryString();
        return URI.create(baseUrl + downstreamPath + (query == null ? "" : "?" + query));
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        FORWARDED_REQUEST_HEADERS.forEach(name -> {
            var values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        });
    }

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
