package com.quickcare.backend.jwt;

import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AutenticacaoPorToken {

    public static final String HEADER_TOKEN = "X-Auth-Token";
    public static final String PREFIXO_BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public record Sessao(Long usuarioId, String tokenRenovado) {
    }

    public Optional<Sessao> autenticarCabecalho(String authorization) {
        if (authorization == null || !authorization.startsWith(PREFIXO_BEARER)) {
            return Optional.empty();
        }
        return autenticar(authorization.substring(PREFIXO_BEARER.length()).trim());
    }

    public Optional<Sessao> autenticar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Claims claims;
        Long usuarioId;
        try {
            claims = jwtService.validarToken(token);
            usuarioId = Long.valueOf(claims.getSubject());
        } catch (TokenInvalidoException | NumberFormatException ex) {
            return Optional.empty();
        }

        Optional<Usuario> usuario = usuarioRepository.findById(usuarioId);
        if (usuario.isEmpty() || revogado(claims, usuario.get())) {
            return Optional.empty();
        }

        Instant emitidoEm = claims.getIssuedAt().toInstant();
        boolean passouDaMetade = emitidoEm.plus(jwtService.validade().dividedBy(2)).isBefore(clock.instant());
        return Optional.of(new Sessao(usuarioId, passouDaMetade ? jwtService.gerarToken(usuario.get()) : null));
    }

    private boolean revogado(Claims claims, Usuario usuario) {
        if (usuario.contaEncerrada() || usuario.isConfirmacaoEmailPendente()) {
            return true;
        }
        Instant senhaAlteradaEm = usuario.getSenhaAlteradaEm();
        return senhaAlteradaEm != null
                && claims.getIssuedAt().toInstant().isBefore(senhaAlteradaEm.truncatedTo(ChronoUnit.SECONDS));
    }

}
