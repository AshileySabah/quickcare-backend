package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.RedefinirSenhaRequest;
import com.quickcare.backend.auth.dto.SolicitarRecuperacaoRequest;
import com.quickcare.backend.auth.exception.TokenRecuperacaoInvalidoException;
import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private RecuperacaoSenhaService recuperacaoSenhaService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .nome("Maria Silva")
                .email("maria@email.com")
                .senhaHash("hash-antigo")
                .build();
    }

    @Test
    void deveGerarTokenESalvarQuandoEmailExiste() {
        when(usuarioRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(usuario));

        SolicitarRecuperacaoRequest request = new SolicitarRecuperacaoRequest();
        request.setEmail("maria@email.com");

        recuperacaoSenhaService.solicitarRecuperacao(request);

        verify(usuarioRepository).save(usuario);
        assertThat(usuario.getTokenRecuperacao()).isNotNull();
        assertThat(usuario.getTokenRecuperacao()).hasSize(6);
        assertThat(usuario.getTokenRecuperacaoExpiracao()).isAfter(LocalDateTime.now());
        verify(emailService).enviarEmailTemplate(eq("maria@email.com"), anyString(), anyString(), anyMap());
    }

    @Test
    void naoDeveFazerNadaQuandoEmailNaoExiste() {
        when(usuarioRepository.findByEmail("naoexiste@email.com")).thenReturn(Optional.empty());

        SolicitarRecuperacaoRequest request = new SolicitarRecuperacaoRequest();
        request.setEmail("naoexiste@email.com");

        recuperacaoSenhaService.solicitarRecuperacao(request);

        verify(usuarioRepository, never()).save(any());
        verify(emailService, never()).enviarEmailTemplate(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void deveRedefinirSenhaComTokenValido() {
        usuario.setTokenRecuperacao("123456");
        usuario.setTokenRecuperacaoExpiracao(LocalDateTime.now().plusHours(1));

        when(usuarioRepository.findByTokenRecuperacao("123456")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("novaSenha123")).thenReturn("hash-novo");

        RedefinirSenhaRequest request = new RedefinirSenhaRequest();
        request.setToken("123456");
        request.setNovaSenha("novaSenha123");

        recuperacaoSenhaService.redefinirSenha(request);

        assertThat(usuario.getSenhaHash()).isEqualTo("hash-novo");
        assertThat(usuario.getTokenRecuperacao()).isNull();
        assertThat(usuario.getTokenRecuperacaoExpiracao()).isNull();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void deveRejeitarTokenExpirado() {
        usuario.setTokenRecuperacao("123456");
        usuario.setTokenRecuperacaoExpiracao(LocalDateTime.now().minusMinutes(1));

        when(usuarioRepository.findByTokenRecuperacao("123456")).thenReturn(Optional.of(usuario));

        RedefinirSenhaRequest request = new RedefinirSenhaRequest();
        request.setToken("123456");
        request.setNovaSenha("novaSenha123");

        assertThatThrownBy(() -> recuperacaoSenhaService.redefinirSenha(request))
                .isInstanceOf(TokenRecuperacaoInvalidoException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveRejeitarTokenInexistente() {
        when(usuarioRepository.findByTokenRecuperacao("000000")).thenReturn(Optional.empty());

        RedefinirSenhaRequest request = new RedefinirSenhaRequest();
        request.setToken("000000");
        request.setNovaSenha("novaSenha123");

        assertThatThrownBy(() -> recuperacaoSenhaService.redefinirSenha(request))
                .isInstanceOf(TokenRecuperacaoInvalidoException.class);
    }
}