package com.quickcare.backend.auth;

import com.quickcare.backend.auth.exception.TokenRedefinicaoInvalidoException;
import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedefinicaoSenhaService {

    static final Duration VALIDADE_TOKEN = Duration.ofMinutes(30);
    static final Duration INTERVALO_MINIMO_ENTRE_PEDIDOS = Duration.ofMinutes(1);
    private static final int BYTES_TOKEN = 32;

    private final UsuarioRepository usuarioRepository;
    private final TokenRedefinicaoSenhaRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthService authService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${quickcare.frontend-url}")
    private String frontendUrl;

    @Async
    @Transactional
    public void solicitarRedefinicao(String email) {
        usuarioRepository.findByEmailIgnoreCase(email.trim()).ifPresent(this::enviarLink);
    }

    @Transactional(readOnly = true)
    public void validarToken(String token) {
        buscarTokenValido(token);
    }

    @Transactional
    public LoginResult redefinirSenha(String token, String novaSenha) {
        TokenRedefinicaoSenha tokenRedefinicao = buscarTokenValido(token);

        Usuario usuario = tokenRedefinicao.getUsuario();
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.setSenhaAlteradaEm(Instant.now());

        tokenRepository.delete(tokenRedefinicao);

        return authService.iniciarSessao(usuario);
    }

    private void enviarLink(Usuario usuario) {
        Instant agora = Instant.now();
        Optional<TokenRedefinicaoSenha> anterior = tokenRepository.findByUsuarioId(usuario.getId());

        if (anterior.isPresent() && anterior.get().getCriadoEm().plus(INTERVALO_MINIMO_ENTRE_PEDIDOS).isAfter(agora)) {
            log.debug("Pedido de redefinição de senha ignorado: já houve um há menos de {}s (usuário {})",
                    INTERVALO_MINIMO_ENTRE_PEDIDOS.toSeconds(), usuario.getId());
            return;
        }

        anterior.ifPresent(token -> tokenRepository.excluirDoUsuario(usuario.getId()));

        String token = gerarToken();
        tokenRepository.save(TokenRedefinicaoSenha.builder()
                .usuario(usuario)
                .tokenHash(hash(token))
                .expiraEm(agora.plus(VALIDADE_TOKEN))
                .build());

        String link = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/redefinir-senha")
                .queryParam("token", token)
                .toUriString();

        log.debug("Link de redefinição de senha para {}: {}", usuario.getEmail(), link);

        emailService.enviarEmailTemplate(usuario.getEmail(), "Redefinição de senha - QuickCare", "redefinicao-senha",
                Map.of(
                        "nome", usuario.getNome(),
                        "link", link,
                        "validadeMinutos", VALIDADE_TOKEN.toMinutes()
                ));
    }

    private TokenRedefinicaoSenha buscarTokenValido(String token) {
        return tokenRepository.findByTokenHash(hash(token))
                .filter(tokenRedefinicao -> !tokenRedefinicao.expirado(Instant.now()))
                .orElseThrow(TokenRedefinicaoInvalidoException::new);
    }

    private String gerarToken() {
        byte[] bytes = new byte[BYTES_TOKEN];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", ex);
        }
    }

}
