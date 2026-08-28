package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.CadastroRequest;
import com.quickcare.backend.auth.dto.CadastroResponse;
import com.quickcare.backend.auth.exception.CpfJaCadastradoException;
import com.quickcare.backend.auth.exception.EmailJaCadastradoException;
import com.quickcare.backend.user.Usuario;
import com.quickcare.backend.user.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public CadastroResponse cadastrar(CadastroRequest request) {
        String cpfNormalizado = normalizarCpf(request.getCpf());

        if (usuarioRepository.existsByCpf(cpfNormalizado)) {
            throw new CpfJaCadastradoException();
        }

        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new EmailJaCadastradoException();
        }

        Usuario usuario = Usuario.builder()
                .nome(request.getNome().trim())
                .email(request.getEmail().trim().toLowerCase())
                .cpf(cpfNormalizado)
                .senhaHash(passwordEncoder.encode(request.getSenha()))
                .tipoUsuario(request.getTipoUsuario())
                .build();

        Usuario salvo = usuarioRepository.save(usuario);

        return CadastroResponse.builder()
                .id(salvo.getId())
                .nome(salvo.getNome())
                .email(salvo.getEmail())
                .cpf(salvo.getCpf())
                .tipoUsuario(salvo.getTipoUsuario())
                .build();
    }

    private String normalizarCpf(String cpf) {
        return cpf.replaceAll("\\D", "");
    }
}
