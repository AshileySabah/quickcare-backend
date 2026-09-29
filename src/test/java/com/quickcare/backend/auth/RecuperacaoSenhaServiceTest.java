package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.RedefinirSenhaRequest;
import com.quickcare.backend.auth.exception.TokenRecuperacaoInvalidoException;
import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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

    private static final String TOKEN = "3f2a9c1e-7b4d-4e8a-9c3f-1a2b3c4d5e6f";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> variaveisCaptor;

    @InjectMocks
    private RecuperacaoSenhaService service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:4200");
        usuario = Usuario.builder()
                .id(1L)
                .nome("Maria Silva")
                .email("Maria@Email.com")
                .senhaHash("hash-antigo")
                .build();
    }

    private RedefinirSenhaRequest requisicao(String email, String token, String senha) {
        RedefinirSenhaRequest request = new RedefinirSenhaRequest();
        request.setEmail(email);
        request.setToken(token);
        request.setNovaSenha(senha);
        return request;
    }

    private void comTokenValido() {
        usuario.setTokenRecuperacao(TOKEN);
        usuario.setTokenRecuperacaoExpiracao(LocalDateTime.now().plusHours(1));
        when(usuarioRepository.findByEmailIgnoreCase("maria@email.com")).thenReturn(Optional.of(usuario));
    }

    @Test
    void deveGerarUuidSalvarEEnviarLinkQuandoEmailExiste() {
        when(usuarioRepository.findByEmailIgnoreCase("maria@email.com")).thenReturn(Optional.of(usuario));

        service.processarSolicitacao("maria@email.com");

        assertThat(usuario.getTokenRecuperacao())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(usuario.getTokenRecuperacaoExpiracao()).isAfter(LocalDateTime.now());
        assertThat(usuario.getTentativasRecuperacao()).isZero();
        verify(usuarioRepository).save(usuario);
        verify(emailService).enviarEmailTemplate(eq("Maria@Email.com"), anyString(),
                eq("recuperacao-senha"), variaveisCaptor.capture());
        assertThat((String) variaveisCaptor.getValue().get("link"))
                .contains("token=" + usuario.getTokenRecuperacao());
    }

    @Test
    void naoDeveFazerNadaQuandoEmailNaoExiste() {
        when(usuarioRepository.findByEmailIgnoreCase("naoexiste@email.com")).thenReturn(Optional.empty());

        service.processarSolicitacao("naoexiste@email.com");

        verify(usuarioRepository, never()).save(any());
        verify(emailService, never()).enviarEmailTemplate(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void deveRedefinirSenhaComEmailETokenCorretos() {
        comTokenValido();
        when(passwordEncoder.encode("NovaSenha@123")).thenReturn("hash-novo");

        service.redefinirSenha(requisicao("maria@email.com", TOKEN, "NovaSenha@123"));

        assertThat(usuario.getSenhaHash()).isEqualTo("hash-novo");
        assertThat(usuario.getTokenRecuperacao()).isNull();
        assertThat(usuario.getTokenRecuperacaoExpiracao()).isNull();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void deveRejeitarEmailInexistente() {
        when(usuarioRepository.findByEmailIgnoreCase("naoexiste@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redefinirSenha(requisicao("naoexiste@email.com", TOKEN, "NovaSenha@123")))
                .isInstanceOf(TokenRecuperacaoInvalidoException.class);
    }

    @Test
    void deveRejeitarTokenExpirado() {
        usuario.setTokenRecuperacao(TOKEN);
        usuario.setTokenRecuperacaoExpiracao(LocalDateTime.now().minusMinutes(1));
        when(usuarioRepository.findByEmailIgnoreCase("maria@email.com")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.redefinirSenha(requisicao("maria@email.com", TOKEN, "NovaSenha@123")))
                .isInstanceOf(TokenRecuperacaoInvalidoException.class);

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void deveContarTentativaQuandoTokenErrado() {
        comTokenValido();

        assertThatThrownBy(() -> service.redefinirSenha(requisicao("maria@email.com", "token-errado", "NovaSenha@123")))
                .isInstanceOf(TokenRecuperacaoInvalidoException.class);

        assertThat(usuario.getTentativasRecuperacao()).isEqualTo(1);
        assertThat(usuario.getTokenRecuperacao()).isEqualTo(TOKEN);
        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void deveInvalidarTokenNaUltimaTentativaPermitida() {
        comTokenValido();
        usuario.setTentativasRecuperacao(RecuperacaoSenhaService.MAX_TENTATIVAS - 1);

        assertThatThrownBy(() -> service.redefinirSenha(requisicao("maria@email.com", "token-errado", "NovaSenha@123")))
                .isInstanceOf(TokenRecuperacaoInvalidoException.class);

        assertThat(usuario.getTokenRecuperacao()).isNull();
        assertThat(usuario.getTokenRecuperacaoExpiracao()).isNull();
    }
}