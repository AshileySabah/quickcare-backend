package com.quickcare.backend.jwt;

import com.quickcare.backend.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final String CLAIM_TIPO_TOKEN = "typ";
    private static final String TIPO_ACCESS = "access";
    private static final String TIPO_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String gerarAccessToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getId().toString())
                .claim("email", usuario.getEmail())
                .claim(CLAIM_TIPO_TOKEN, TIPO_ACCESS)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(Duration.ofMinutes(properties.accessTokenExpirationMinutes()))))
                .signWith(secretKey)
                .compact();
    }

    public String gerarRefreshToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getId().toString())
                .claim(CLAIM_TIPO_TOKEN, TIPO_REFRESH)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(Duration.ofDays(properties.refreshTokenExpirationDays()))))
                .signWith(secretKey)
                .compact();
    }

    public Claims validarAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!TIPO_ACCESS.equals(claims.get(CLAIM_TIPO_TOKEN, String.class))) {
            throw new TokenInvalidoException();
        }
        return claims;
    }

    public Claims validarRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (!TIPO_REFRESH.equals(claims.get(CLAIM_TIPO_TOKEN, String.class))) {
            throw new TokenInvalidoException();
        }
        return claims;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new TokenInvalidoException();
        }
    }

}
