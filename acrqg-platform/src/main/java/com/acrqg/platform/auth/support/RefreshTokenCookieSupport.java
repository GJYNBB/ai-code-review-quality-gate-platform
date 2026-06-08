package com.acrqg.platform.auth.support;

import com.acrqg.platform.infra.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * HttpOnly refresh-token cookie helper.
 *
 * <p>The refresh JWT is an authentication credential and must not be exposed to JavaScript. Controllers use this
 * helper to set, read and clear the cookie consistently while {@code AuthService} continues to own token issuance,
 * rotation and revocation.
 */
@Component
public class RefreshTokenCookieSupport {

    public static final String DEFAULT_COOKIE_NAME = "ACRQG_REFRESH";
    public static final String DEFAULT_COOKIE_PATH = "/api/v1/auth";

    private final JwtTokenProvider tokenProvider;
    private final String cookieName;
    private final String cookiePath;
    private final boolean secure;
    private final String sameSite;

    public RefreshTokenCookieSupport(
            JwtTokenProvider tokenProvider,
            @Value("${app.security.refresh-cookie.name:" + DEFAULT_COOKIE_NAME + "}") String cookieName,
            @Value("${app.security.refresh-cookie.path:" + DEFAULT_COOKIE_PATH + "}") String cookiePath,
            @Value("${app.security.refresh-cookie.secure:true}") boolean secure,
            @Value("${app.security.refresh-cookie.same-site:Lax}") String sameSite) {
        this.tokenProvider = tokenProvider;
        this.cookieName = blankToDefault(cookieName, DEFAULT_COOKIE_NAME);
        this.cookiePath = blankToDefault(cookiePath, DEFAULT_COOKIE_PATH);
        this.secure = secure;
        this.sameSite = blankToDefault(sameSite, "Lax");
    }

    public String cookieName() {
        return cookieName;
    }

    public String extract(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

    public String setHeader(String refreshToken) {
        return baseCookie(refreshToken)
                .maxAge(Duration.ofSeconds(tokenProvider.getRefreshTtlSeconds()))
                .build()
                .toString();
    }

    public String clearHeader() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build()
                .toString();
    }

    public void addSetCookieHeader(HttpHeaders headers, String refreshToken) {
        if (headers != null) {
            headers.add(HttpHeaders.SET_COOKIE, setHeader(refreshToken));
        }
    }

    public void addClearCookieHeader(HttpHeaders headers) {
        if (headers != null) {
            headers.add(HttpHeaders.SET_COOKIE, clearHeader());
        }
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieName, value == null ? "" : value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(cookiePath);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
