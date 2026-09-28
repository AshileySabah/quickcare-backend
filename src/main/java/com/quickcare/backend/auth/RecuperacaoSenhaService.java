package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.RedefinirSenhaRequest;
import com.quickcare.backend.auth.dto.SolicitarRecuperacaoRequest;
import com.quickcare.backend.auth.exception.TokenRecuperacaoInvalidoException;
import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Map;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RecuperacaoSenhaService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public void solicitarRecuperacao(SolicitarRecuperacaoRequest request) {
    String emailNormalizado = request.getEmail().trim().toLowerCase();

    usuarioRepository.findByEmail(emailNormalizado).ifPresent(usuario -> {
        String token = gerarTokenNumerico();

        usuario.setTokenRecuperacao(token);
        usuario.setTokenRecuperacaoExpiracao(LocalDateTime.now().plusHours(1));
        usuarioRepository.save(usuario);

        Map<String, Object> variaveis = Map.of(
                "nome", usuario.getNome(),
                "token", token,
                "validadeHoras", 1
        );

        emailService.enviarEmailTemplate(usuario.getEmail(), "Recuperação de Senha - QuickCare",
                "recuperacao-senha", variaveis);
    });
}

    public void redefinirSenha(RedefinirSenhaRequest request) {
        Usuario usuario = usuarioRepository.findByTokenRecuperacao(request.getToken())
                .filter(u -> u.getTokenRecuperacaoExpiracao() != null
                        && u.getTokenRecuperacaoExpiracao().isAfter(LocalDateTime.now()))
                .orElseThrow(TokenRecuperacaoInvalidoException::new);

        usuario.setSenhaHash(passwordEncoder.encode(request.getNovaSenha()));
        usuario.setTokenRecuperacao(null);
        usuario.setTokenRecuperacaoExpiracao(null);
        usuarioRepository.save(usuario);
    }

    private String gerarTokenNumerico() {
        int numero = 100000 + random.nextInt(900000); 
        return String.valueOf(numero);
    }
}