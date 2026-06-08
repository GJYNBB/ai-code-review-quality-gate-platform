package com.acrqg.platform.infra.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class OutboundUrlGuardTest {

    @Test
    void rejectsNonHttpsUserinfoAndPrivateTargets() {
        assertThatThrownBy(() -> OutboundUrlGuard.requireHttpsPublicUrl("http://example.com", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
        assertThatThrownBy(() -> OutboundUrlGuard.requireHttpsPublicUrl("https://user@example.com", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userinfo");
        assertThatThrownBy(() -> OutboundUrlGuard.requireHttpsPublicUrl("https://localhost", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("localhost");
        assertThatThrownBy(() -> OutboundUrlGuard.requireHttpsPublicUrl("https://127.0.0.1", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed address");
        assertThatThrownBy(() -> OutboundUrlGuard.requireHttpsPublicUrl("https://10.0.0.1", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed address");
        assertThatThrownBy(() -> OutboundUrlGuard.requireHttpsPublicUrl("https://169.254.169.254", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed address");
    }

    @Test
    void rejectsPrivateAddressesAtConnectionResolutionTime() throws Exception {
        assertThatThrownBy(() -> OutboundUrlGuard.requirePublicAddress(
                InetAddress.getByName("127.0.0.1"), "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed address");
        assertThatThrownBy(() -> OutboundUrlGuard.requirePublicAddress(
                InetAddress.getByName("::1"), "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed address");
    }

    @Test
    void validatesSameHttpsHostRedirectsAndPaginationLinks() {
        URI uri = OutboundUrlGuard.requireSameHttpsHost(
                URI.create("https://api.github.com/repos/acme/demo?page=2"),
                "api.github.com",
                "GitHub pagination link");

        assertThat(uri.getHost()).isEqualTo("api.github.com");
        assertThatThrownBy(() -> OutboundUrlGuard.requireSameHttpsHost(
                URI.create("https://example.com/repos/acme/demo?page=2"),
                "api.github.com",
                "GitHub pagination link"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must stay on host");
        assertThatThrownBy(() -> OutboundUrlGuard.requireSameHttpsHost(
                URI.create("http://api.github.com/repos/acme/demo?page=2"),
                "api.github.com",
                "GitHub pagination link"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }
}
