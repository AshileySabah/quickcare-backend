package com.quickcare.backend.auth;

import com.quickcare.backend.auth.exception.TokenRedefinicaoInvalidoException;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedefinicaoSenhaServiceTest {

    private static final String TOKEN = "token-de-teste";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TokenRedefinicaoSenhaRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private AuthService authService;

    @Captor
    private ArgumentCaptor<TokenRedefinicaoSenha> tokenCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> variaveisCaptor;

    @InjectMocks
    private RedefinicaoSenhaService service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:4200");
        usuario = Usuario.builder()
                .id(1L)
                .nome("Maria Silva")
                .email("maria@email.com")
                .senhaHash("hash-antigo")
                .build();
    }

    private TokenRedefinicaoSenha tokenSalvo(Instant criadoEm, Instant expiraEm) {
        return TokenRedefinicaoSenha.builder()
                .id(10L)
                .usuario(usuario)
                .tokenHash(RedefinicaoSenhaService.hash(TOKEN))
                .criadoEm(criadoEm)
                .expiraEm(expiraEm)
                .build();
    }

    @Test
    void deveEnviarLinkComTokenCujoHashFoiSalvo() {
        when(usuarioRepository.findByEmailIgnoreCase("maria@email.com")).thenReturn(Optional.of(usuario));
        when(tokenRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());

        service.solicitarRedefinicao("  maria@email.com ");

        verify(tokenRepository).save(tokenCaptor.capture());
        verify(emailService).enviarEmailTemplate(eq("maria@email.com"), anyString(), eq("redefinicao-senha"),
                variaveisCaptor.capture());

        String link = (String) variaveisCaptor.getValue().get("link");
        assertThat(link).startsWith("http://localhost:4200/redefinir-senha?token=");
        String tokenDoLink = UriComponentsBuilder.fromUriString(link).build().getQueryParams().getFirst("token");

        TokenRedefinicaoSenha salvo = tokenCaptor.getValue();
        assertThat(salvo.getUsuario()).isSameAs(usuario);
        assertThat(salvo.getTokenHash()).isEqualTo(RedefinicaoSenhaService.hash(tokenDoLink));
        assertThat(salvo.getTokenHash()).isNotEqualTo(tokenDoLink);
        assertThat(salvo.getExpiraEm()).isBetween(
                Instant.now().plus(RedefinicaoSenhaService.VALIDADE_TOKEN).minusSeconds(5),
                Instant.now().plus(RedefinicaoSenhaService.VALIDADE_TOKEN));
    }

    @Test
    void naoDeveFazerNadaQuandoEmailNaoExiste() {
        when(usuarioRepository.findByEmailIgnoreCase("naoexiste@email.com")).thenReturn(Optional.empty());

        service.solicitarRedefinicao("naoexiste@email.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).enviarEmailTemplate(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void deveSubstituirTokenAnteriorForaDoIntervaloMinimo() {
        Instant criadoEm = Instant.now().minus(RedefinicaoSenhaService.INTERVALO_MINIMO_ENTRE_PEDIDOS).minusSeconds(1);
        when(usuarioRepository.findByEmailIgnoreCase("maria@email.com")).thenReturn(Optional.of(usuario));
        when(tokenRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tokenSalvo(criadoEm, criadoEm.plus(Duration.ofMinutes(30)))));

        service.solicitarRedefinicao("maria@email.com");

        verify(tokenRepository).excluirDoUsuario(1L);
        verify(tokenRepository).save(any());
        verify(emailService).enviarEmailTemplate(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void deveIgnorarPedidoRepetidoDentroDoIntervaloMinimo() {
        Instant criadoEm = Instant.now().minusSeconds(5);
        when(usuarioRepository.findByEmailIgnoreCase("maria@email.com")).thenReturn(Optional.of(usuario));
        when(tokenRepository.findByUsuarioId(1L)).thenReturn(Optional.of(tokenSalvo(criadoEm, criadoEm.plus(Duration.ofMinutes(30)))));

        service.solicitarRedefinicao("maria@email.com");

        verify(tokenRepository, never()).excluirDoUsuario(anyLong());
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).enviarEmailTemplate(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void deveRedefinirSenhaConsumirTokenEIniciarSessao() {
        TokenRedefinicaoSenha token = tokenSalvo(Instant.now(), Instant.now().plusSeconds(600));
        when(tokenRepository.findByTokenHash(RedefinicaoSenhaService.hash(TOKEN))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NovaSenha@123")).thenReturn("hash-novo");
        LoginResult sessao = new LoginResult(null, "access", "refresh");
        when(authService.iniciarSessao(usuario)).thenReturn(sessao);

        LoginResult resultado = service.redefinirSenha(TOKEN, "NovaSenha@123");

        assertThat(usuario.getSenhaHash()).isEqualTo("hash-novo");
        assertThat(usuario.getSenhaAlteradaEm()).isNotNull();
        assertThat(resultado).isSameAs(sessao);
        verify(tokenRepository).delete(token);
    }

    @Test
    void deveRejeitarTokenInexistente() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redefinirSenha("qualquer", "NovaSenha@123"))
                .isInstanceOf(TokenRedefinicaoInvalidoException.class);

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void deveRejeitarTokenExpirado() {
        TokenRedefinicaoSenha token = tokenSalvo(Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHash(RedefinicaoSenhaService.hash(TOKEN))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.redefinirSenha(TOKEN, "NovaSenha@123"))
                .isInstanceOf(TokenRedefinicaoInvalidoException.class);

        assertThat(usuario.getSenhaHash()).isEqualTo("hash-antigo");
        verify(tokenRepository, never()).delete(any());
    }

    @Test
    void deveValidarTokenSemConsumir() {
        TokenRedefinicaoSenha token = tokenSalvo(Instant.now(), Instant.now().plusSeconds(600));
        when(tokenRepository.findByTokenHash(RedefinicaoSenhaService.hash(TOKEN))).thenReturn(Optional.of(token));

        assertThatCode(() -> service.validarToken(TOKEN)).doesNotThrowAnyException();

        verify(tokenRepository, never()).delete(any());
    }

}
