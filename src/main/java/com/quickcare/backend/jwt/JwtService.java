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
    private static final String TIPO_SESSAO = "sessao";

    private final SecretKey secretKey;
    private final Duration validade;

    public JwtService(JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.validade = Duration.ofDays(properties.tokenExpirationDays());
    }

    public Duration validade() {
        return validade;
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getId().toString())
                .claim("email", usuario.getEmail())
                .claim(CLAIM_TIPO_TOKEN, TIPO_SESSAO)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)))
                .signWith(secretKey)
                .compact();
    }

    public Claims validarToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new TokenInvalidoException();
        }
        if (!TIPO_SESSAO.equals(claims.get(CLAIM_TIPO_TOKEN, String.class))) {
            throw new TokenInvalidoException();
        }
        return claims;
    }

}
