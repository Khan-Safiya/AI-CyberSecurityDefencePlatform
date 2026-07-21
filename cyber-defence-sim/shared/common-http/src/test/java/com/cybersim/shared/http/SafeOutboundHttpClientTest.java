package com.cybersim.shared.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeOutboundHttpClientTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void refusesLoopbackDestination() {
        SafeOutboundHttpClient client = SafeOutboundHttpClient.create();

        assertThatThrownBy(() -> client.send(SafeHttpRequest.get(URI.create("http://127.0.0.1:9/path"))))
                .isInstanceOf(SafeOutboundHttpException.class)
                .hasMessageContaining("Refusing outbound request");
    }

    @Test
    void refusesPrivateNetworkDestination() {
        SafeOutboundHttpClient client = SafeOutboundHttpClient.create();

        assertThatThrownBy(() -> client.send(SafeHttpRequest.get(URI.create("http://10.0.0.5/path"))))
                .isInstanceOf(SafeOutboundHttpException.class)
                .hasMessageContaining("Refusing outbound request");
    }

    @Test
    void refusesInternalHostnameSuffix() {
        SafeOutboundHttpClient client = SafeOutboundHttpClient.create();

        assertThatThrownBy(() -> client.send(SafeHttpRequest.get(URI.create("http://app.internal/path"))))
                .isInstanceOf(SafeOutboundHttpException.class)
                .hasMessageContaining("public hostname");
    }

    @Test
    void refusesNonHttpScheme() {
        SafeOutboundHttpClient client = SafeOutboundHttpClient.create();

        assertThatThrownBy(() -> client.send(SafeHttpRequest.get(URI.create("ftp://example.com/path"))))
                .isInstanceOf(SafeOutboundHttpException.class);
    }

    @Test
    void capturesStatusHeadersAndBodyFromServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] body = "hello".getBytes();
            exchange.getResponseHeaders().add("X-Test-Header", "present");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        SafeOutboundHttpClient client = SafeOutboundHttpClient.createForTesting(Duration.ofSeconds(5), 1_048_576L);
        SafeHttpResponse response = client.send(SafeHttpRequest.get(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ok")));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.bodyAsString()).isEqualTo("hello");
        assertThat(response.hasHeader("x-test-header")).isTrue();
    }

    @Test
    void doesNotFollowRedirectsAutomatically() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://evil.example.com/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        SafeOutboundHttpClient client = SafeOutboundHttpClient.createForTesting(Duration.ofSeconds(5), 1_048_576L);
        SafeHttpResponse response = client.send(SafeHttpRequest.get(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect")));

        assertThat(response.statusCode()).isEqualTo(302);
    }

    @Test
    void rejectsResponseLargerThanConfiguredLimit() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/big", exchange -> {
            byte[] body = new byte[2048];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        SafeOutboundHttpClient client = SafeOutboundHttpClient.createForTesting(Duration.ofSeconds(5), 1024L);

        assertThatThrownBy(() -> client.send(SafeHttpRequest.get(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/big"))))
                .isInstanceOf(SafeOutboundHttpException.class)
                .hasMessageContaining("exceeded max allowed size");
    }
}
