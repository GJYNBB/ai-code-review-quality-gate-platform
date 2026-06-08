package com.acrqg.platform.infra.net;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Guard for user/admin supplied outbound URLs.
 *
 * <p>The platform sends repository tokens and AI prompts/API keys to remote endpoints. Every such
 * endpoint must be HTTPS and must not resolve to local/private/link-local/metadata addresses.
 */
public final class OutboundUrlGuard {

    private static final String METADATA_IPV4 = "169.254.169.254";

    private OutboundUrlGuard() {
    }

    public static URI requireHttpsPublicUrl(String rawUrl, String purpose) {
        return requireHttpsPublicUri(parse(rawUrl, purpose), purpose);
    }

    public static URI requireHttpsPublicUri(URI uri, String purpose) {
        requireUsableUri(uri, purpose);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(purpose + " must use https");
        }
        requirePublicHost(uri.getHost(), purpose);
        return uri;
    }

    public static URI requireSameHttpsHost(URI uri, String expectedHost, String purpose) {
        URI checked = requireHttpsPublicUri(uri, purpose);
        String actual = normalizeHost(checked.getHost(), purpose);
        String expected = normalizeHost(expectedHost, purpose + " expected host");
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(purpose + " must stay on host " + expected);
        }
        return checked;
    }

    public static String requirePublicHost(String host, String purpose) {
        String asciiHost = normalizeHost(host, purpose);
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(asciiHost);
        } catch (Exception ex) {
            throw new IllegalArgumentException(purpose + " host cannot be resolved safely: " + asciiHost, ex);
        }
        if (resolved.length == 0) {
            throw new IllegalArgumentException(purpose + " host has no addresses: " + asciiHost);
        }
        for (InetAddress address : resolved) {
            requirePublicAddress(address, purpose);
        }
        return asciiHost;
    }

    public static void requirePublicAddress(InetAddress address, String purpose) {
        if (!isPublicRoutable(address)) {
            throw new IllegalArgumentException(purpose + " resolves to a disallowed address: "
                    + address.getHostAddress());
        }
    }

    private static URI parse(String rawUrl, String purpose) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException(purpose + " URL is required");
        }
        try {
            URI uri = new URI(rawUrl.trim());
            requireUsableUri(uri, purpose);
            return uri;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(purpose + " URL is invalid", ex);
        }
    }

    private static void requireUsableUri(URI uri, String purpose) {
        if (uri == null) {
            throw new IllegalArgumentException(purpose + " URL is required");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(purpose + " URL host is required");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(purpose + " URL must not contain userinfo");
        }
    }

    private static String normalizeHost(String host, String purpose) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(purpose + " host is required");
        }
        String asciiHost = IDN.toASCII(host.trim()).toLowerCase(Locale.ROOT);
        if (asciiHost.equals("localhost") || asciiHost.endsWith(".localhost")) {
            throw new IllegalArgumentException(purpose + " must not target localhost");
        }
        return asciiHost;
    }

    public static boolean isPublicRoutable(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        String textual = address.getHostAddress();
        if (METADATA_IPV4.equals(textual)) {
            return false;
        }
        if (address instanceof Inet4Address) {
            byte[] b = address.getAddress();
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            int third = b[2] & 0xff;
            // 0.0.0.0/8, 100.64.0.0/10, 127/8, 169.254/16, 192.0.0/24, 192.0.2/24,
            // 198.18/15, 198.51.100/24, 203.0.113/24, 224/4+ are not valid public API targets.
            return first != 0
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 127)
                    && !(first == 169 && second == 254)
                    && !(first == 192 && second == 0 && third == 0)
                    && !(first == 192 && second == 0 && third == 2)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 198 && second == 51 && third == 100)
                    && !(first == 203 && second == 0 && third == 113)
                    && first < 224;
        }
        if (address instanceof Inet6Address) {
            byte[] b = address.getAddress();
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            boolean uniqueLocal = (first & 0xfe) == 0xfc; // fc00::/7
            boolean documentation = first == 0x20 && second == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8;
            return !uniqueLocal && !documentation;
        }
        return false;
    }
}
