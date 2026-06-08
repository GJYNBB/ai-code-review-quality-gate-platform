package com.acrqg.platform.infra.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds outbound HTTP clients that enforce URL guard rules at connection time.
 *
 * <p>Pre-validating a configured URL is not enough for token-bearing Git/AI calls: DNS can change between validation
 * and connection, and redirects can move a request to an internal endpoint. This factory disables automatic redirects
 * and installs a DNS resolver that rejects local/private/link-local/metadata addresses on the actual connection path.
 */
public final class GuardedRestClientFactory {

    private GuardedRestClientFactory() {
    }

    public static RestClient build(Duration connectTimeout, Duration readTimeout) {
        PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new GuardedDnsResolver())
                .build();
        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(cm)
                .disableRedirectHandling()
                .build();
        HttpComponentsClientHttpRequestFactory rf = new HttpComponentsClientHttpRequestFactory(client);
        rf.setConnectTimeout(connectTimeout);
        rf.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(rf).build();
    }

    private static final class GuardedDnsResolver implements DnsResolver {

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                try {
                    OutboundUrlGuard.requirePublicAddress(address, "outbound HTTP target");
                } catch (IllegalArgumentException ex) {
                    UnknownHostException wrapped = new UnknownHostException(ex.getMessage());
                    wrapped.initCause(ex);
                    throw wrapped;
                }
            }
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            InetAddress address = InetAddress.getByName(host);
            try {
                OutboundUrlGuard.requirePublicAddress(address, "outbound HTTP target");
            } catch (IllegalArgumentException ex) {
                UnknownHostException wrapped = new UnknownHostException(ex.getMessage());
                wrapped.initCause(ex);
                throw wrapped;
            }
            return address.getCanonicalHostName();
        }
    }
}
