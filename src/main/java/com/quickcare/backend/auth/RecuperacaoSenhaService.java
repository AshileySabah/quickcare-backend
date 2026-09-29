package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.RedefinirSenhaRequest;
import com.quickcare.backend.auth.dto.SolicitarRecuperacaoRequest;
import com.quickcare.backend.auth.exception.TokenRecuperacaoInvalidoException;
import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecuperacaoSenhaService {

    static final int MAX_TENTATIVAS = 5;
    static final int VALIDADE_HORAS = 1; // provisório até confirmar as RN

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Value("${quickcare.frontend-url:http://localhost:4200}")
    private String frontendUrl;


    public void solicitarRecuperacao(SolicitarRecuperacaoRequest request) {
        String email = request.getEmail().trim();
        executor.submit(() -> {
            try {
                processarSolicitacao(email);
            } catch (Exception e) {
                log.error("Erro ao processar solicitação de recuperação de senha", e);
            }
        });
    }

    void processarSolicitacao(String email) {
        usuarioRepository.findByEmailIgnoreCase(email).ifPresent(usuario -> {
            String token = UUID.randomUUID().toString();

            usuario.setTokenRecuperacao(token);
            usuario.setTokenRecuperacaoExpiracao(LocalDateTime.now().plusHours(VALIDADE_HORAS));
            usuario.setTentativasRecuperacao(0);
            usuarioRepository.save(usuario);

            String link = frontendUrl + "/redefinir-senha?email="
                    + URLEncoder.encode(usuario.getEmail(), StandardCharsets.UTF_8)
                    + "&token=" + token;

            Map<String, Object> variaveis = Map.of(
                    "nome", usuario.getNome(),
                    "token", token,
                    "link", link,
                    "validadeHoras", VALIDADE_HORAS
            );

            emailService.enviarEmailTemplate(usuario.getEmail(), "Recuperação de Senha - QuickCare",
                    "recuperacao-senha", variaveis);
        });
    }

    public void redefinirSenha(RedefinirSenhaRequest request) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .orElseThrow(TokenRecuperacaoInvalidoException::new);

        if (usuario.getTokenRecuperacao() == null
                || usuario.getTokenRecuperacaoExpiracao() == null
                || usuario.getTokenRecuperacaoExpiracao().isBefore(LocalDateTime.now())) {
            throw new TokenRecuperacaoInvalidoException();
        }

        if (!tokenConfere(usuario.getTokenRecuperacao(), request.getToken())) {
            registrarTentativaFalha(usuario);
            throw new TokenRecuperacaoInvalidoException();
        }

        usuario.setSenhaHash(passwordEncoder.encode(request.getNovaSenha()));
        usuario.setTokenRecuperacao(null);
        usuario.setTokenRecuperacaoExpiracao(null);
        usuario.setTentativasRecuperacao(0);
        usuarioRepository.save(usuario);
    }

    private void registrarTentativaFalha(Usuario usuario) {
        int tentativas = usuario.getTentativasRecuperacao() + 1;
        usuario.setTentativasRecuperacao(tentativas);

        if (tentativas >= MAX_TENTATIVAS) {
            usuario.setTokenRecuperacao(null);
            usuario.setTokenRecuperacaoExpiracao(null);
            usuario.setTentativasRecuperacao(0);
        }

        usuarioRepository.save(usuario);
    }

    private boolean tokenConfere(String esperado, String informado) {
        if (informado == null) {
            return false;
        }
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                informado.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    @PreDestroy
    void encerrar() {
        executor.shutdown();
    }
}