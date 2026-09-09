package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.LoginRequest;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.jwt.JwtProperties;
import com.quickcare.backend.jwt.TokenInvalidoException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String REFRESH_TOKEN_PATH = "/auth/refresh";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult resultado = authService.login(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildAccessTokenCookie(resultado.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(resultado.refreshToken()).toString())
                .body(resultado.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new TokenInvalidoException();
        }

        String novoAccessToken = authService.renovarAccessToken(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildAccessTokenCookie(novoAccessToken).toString())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, buildExpiredCookie(ACCESS_TOKEN_COOKIE, "/").toString())
                .header(HttpHeaders.SET_COOKIE, buildExpiredCookie(REFRESH_TOKEN_COOKIE, REFRESH_TOKEN_PATH).toString())
                .build();
    }

    private ResponseCookie buildAccessTokenCookie(String token) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(Duration.ofMinutes(jwtProperties.accessTokenExpirationMinutes()))
                .build();
    }

    private ResponseCookie buildRefreshTokenCookie(String token) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(REFRESH_TOKEN_PATH)
                .maxAge(Duration.ofDays(jwtProperties.refreshTokenExpirationDays()))
                .build();
    }

    private ResponseCookie buildExpiredCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(path)
                .maxAge(0)
                .build();
    }

}
