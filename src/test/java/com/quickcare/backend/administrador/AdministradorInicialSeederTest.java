package com.quickcare.backend.administrador;

import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdministradorInicialSeederTest {

    private static final String EMAIL = "admin@quickcare.com";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AdministradorRepository administradorRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdministradorInicialSeeder seeder(String senha, String cpf) {
        return new AdministradorInicialSeeder(
                new AdministradorInicialProperties(EMAIL, senha, "Admin QuickCare", cpf),
                usuarioRepository, administradorRepository, passwordEncoder);
    }

    @Test
    void semEmailESenhaNaoFazNada() {
        new AdministradorInicialSeeder(new AdministradorInicialProperties("", "", null, null),
                usuarioRepository, administradorRepository, passwordEncoder).run(null);

        verifyNoInteractions(usuarioRepository, administradorRepository);
    }

    @Test
    void criaUsuarioEPerfilDeAdmin() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        when(usuarioRepository.findByCpf("52998224725")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Senha@Forte1")).thenReturn("hash");
        when(usuarioRepository.save(any())).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(10L);
            return usuario;
        });

        seeder("Senha@Forte1", "529.982.247-25").run(null);

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuario.capture());
        assertThat(usuario.getValue().getCpf()).isEqualTo("52998224725");
        assertThat(usuario.getValue().getSenhaHash()).isEqualTo("hash");
        verify(administradorRepository).save(any());
    }

    @Test
    void usuarioExistenteGanhaPerfilDeAdminSemTrocarASenha() {
        Usuario existente = Usuario.builder().id(5L).email(EMAIL).senhaHash("hash-antigo").build();
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existente));
        when(administradorRepository.existsByUsuarioId(5L)).thenReturn(false);

        seeder("Senha@Forte1", null).run(null);

        assertThat(existente.getSenhaHash()).isEqualTo("hash-antigo");
        verify(usuarioRepository, never()).save(any());
        verify(administradorRepository).save(any());
    }

    @Test
    void naoDuplicaAdminJaExistente() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(Usuario.builder().id(5L).email(EMAIL).build()));
        when(administradorRepository.existsByUsuarioId(5L)).thenReturn(true);

        seeder("Senha@Forte1", "52998224725").run(null);

        verify(administradorRepository, never()).save(any());
    }

    @Test
    void recusaSenhaFracaECpfInvalido() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seeder("fraca", "52998224725").run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_SENHA");
        assertThatThrownBy(() -> seeder("Senha@Forte1", "12345678900").run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_CPF");
    }

}
