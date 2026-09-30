package com.quickcare.backend.auth;

import com.quickcare.backend.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
class AuthCookies {

    static final String ACCESS_TOKEN_COOKIE = "access_token";
    static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String REFRESH_TOKEN_PATH = "/auth/refresh";

    private final JwtProperties jwtProperties;

    ResponseCookie accessToken(String token) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(Duration.ofMinutes(jwtProperties.accessTokenExpirationMinutes()))
                .build();
    }

    ResponseCookie refreshToken(String token) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(REFRESH_TOKEN_PATH)
                .maxAge(Duration.ofDays(jwtProperties.refreshTokenExpirationDays()))
                .build();
    }

    ResponseCookie expiredAccessToken() {
        return expired(ACCESS_TOKEN_COOKIE, "/");
    }

    ResponseCookie expiredRefreshToken() {
        return expired(REFRESH_TOKEN_COOKIE, REFRESH_TOKEN_PATH);
    }

    private ResponseCookie expired(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(path)
                .maxAge(0)
                .build();
    }

}
