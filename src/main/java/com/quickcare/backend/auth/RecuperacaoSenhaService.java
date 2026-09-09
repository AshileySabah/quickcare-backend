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

            String corpoHtml = "<b>Recuperação de Senha - QuickCare</b><br><br>"
                    + "Olá, " + usuario.getNome() + "!<br><br>"
                    + "Você solicitou a recuperação de senha. Use o código abaixo:<br><br>"
                    + "<h2>" + token + "</h2><br><br>"
                    + "Este código expira em 1 hora.<br><br>"
                    + "Se você não solicitou, ignore este e-mail.";

            emailService.enviarEmailHtml(usuario.getEmail(), "Recuperação de Senha - QuickCare", corpoHtml);
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