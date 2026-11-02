package com.quickcare.backend.administrador;

import com.quickcare.backend.common.validation.DocumentosBrasileiros;
import com.quickcare.backend.common.validation.SenhaForteValidator;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdministradorInicialSeeder implements ApplicationRunner {

    private final AdministradorInicialProperties properties;
    private final UsuarioRepository usuarioRepository;
    private final AdministradorRepository administradorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.configurado()) {
            log.debug("ADMIN_EMAIL/ADMIN_SENHA não definidos: nenhum admin inicial a criar.");
            return;
        }

        String email = properties.email().trim();
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email).orElseGet(() -> criarUsuario(email));

        if (administradorRepository.existsByUsuarioId(usuario.getId())) {
            log.info("Admin inicial {} já existe.", email);
            return;
        }

        administradorRepository.save(Administrador.builder().usuario(usuario).build());
        log.info("Perfil de admin concedido a {}.", email);
    }

    private Usuario criarUsuario(String email) {
        if (!new SenhaForteValidator().isValid(properties.senha(), null)) {
            throw new IllegalStateException("ADMIN_SENHA fraca: use no mínimo 8 caracteres, com maiúscula, minúscula e caractere especial.");
        }

        String cpf = properties.cpf() == null ? "" : properties.cpf().replaceAll("\\D", "");
        if (!DocumentosBrasileiros.cpfValido(cpf)) {
            throw new IllegalStateException("ADMIN_CPF ausente ou inválido: informe um CPF válido (só números) para o admin inicial.");
        }
        if (usuarioRepository.findByCpf(cpf).isPresent()) {
            throw new IllegalStateException("ADMIN_CPF já pertence a outro usuário, com e-mail diferente de ADMIN_EMAIL.");
        }

        String nome = properties.nome() == null || properties.nome().isBlank() ? "Administrador" : properties.nome().trim();
        log.info("Criando admin inicial {}.", email);

        return usuarioRepository.save(
                Usuario.builder()
                        .nome(nome)
                        .email(email)
                        .senhaHash(passwordEncoder.encode(properties.senha()))
                        .cpf(cpf)
                        .build()
        );
    }

}
