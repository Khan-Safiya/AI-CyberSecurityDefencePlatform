package com.cybersim.shared.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundHostValidatorTest {

    @Test
    void acceptsWellFormedHttpsUri() {
        assertThat(OutboundHostValidator.findUriViolation(URI.create("https://example.com/path"))).isEmpty();
    }

    @Test
    void acceptsHttpUri() {
        assertThat(OutboundHostValidator.findUriViolation(URI.create("http://example.com/path"))).isEmpty();
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThat(OutboundHostValidator.findUriViolation(URI.create("ftp://example.com/path"))).isPresent();
    }

    @Test
    void rejectsEmbeddedCredentials() {
        assertThat(OutboundHostValidator.findUriViolation(URI.create("https://user:pass@example.com/path"))).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "app.local", "service.internal", "metadata.google.internal", "intranet"})
    void rejectsNonPublicHostnameSuffixes(String hostname) {
        assertThat(OutboundHostValidator.findHostnameViolation(hostname)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.com", "staging.example.com", "api.example.co.uk"})
    void acceptsPublicHostnames(String hostname) {
        assertThat(OutboundHostValidator.findHostnameViolation(hostname)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.0.0.1", "192.168.1.1", "172.16.0.1", "169.254.169.254", "0.0.0.0", "224.0.0.1"})
    void rejectsPrivateAndReservedIpv4Literals(String ip) {
        assertThat(OutboundHostValidator.findHostnameViolation(ip)).isPresent();
    }

    @Test
    void rejectsCarrierGradeNatRange() {
        assertThat(OutboundHostValidator.findHostnameViolation("100.64.0.1")).isPresent();
    }

    @Test
    void acceptsPublicIpv4Literal() {
        assertThat(OutboundHostValidator.findHostnameViolation("93.184.216.34")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "192.168.0.5", "169.254.1.1", "0.0.0.0"})
    void resolvedAddressCheckRejectsPrivateAndReservedRanges(String ip) throws UnknownHostException {
        assertThat(OutboundHostValidator.isUnsafeAddress(InetAddress.getByName(ip))).isTrue();
    }

    @Test
    void resolvedAddressCheckRejectsIpv6Loopback() throws UnknownHostException {
        assertThat(OutboundHostValidator.isUnsafeAddress(InetAddress.getByName("::1"))).isTrue();
    }

    @Test
    void resolvedAddressCheckRejectsIpv6UniqueLocal() throws UnknownHostException {
        assertThat(OutboundHostValidator.isUnsafeAddress(InetAddress.getByName("fd00::1"))).isTrue();
    }

    @Test
    void resolvedAddressCheckAcceptsPublicIpv4() throws UnknownHostException {
        assertThat(OutboundHostValidator.isUnsafeAddress(InetAddress.getByName("93.184.216.34"))).isFalse();
    }
}
