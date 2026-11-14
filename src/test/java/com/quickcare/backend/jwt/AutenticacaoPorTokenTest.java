package com.quickcare.backend.jwt;

import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutenticacaoPorTokenTest {

    private JwtService jwtService;
    private UsuarioRepository usuarioRepository;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties("segredo-de-teste-com-pelo-menos-32-bytes-0123456789", 7));
        usuarioRepository = mock(UsuarioRepository.class);
        usuario = Usuario.builder().id(1L).email("ana@quickcare.dev").build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
    }

    private AutenticacaoPorToken em(Instant agora) {
        return new AutenticacaoPorToken(jwtService, usuarioRepository, Clock.fixed(agora, ZoneOffset.UTC));
    }

    @Test
    void tokenRecenteAutenticaSemRenovar() {
        String token = jwtService.gerarToken(usuario);

        Optional<AutenticacaoPorToken.Sessao> sessao = em(Instant.now()).autenticarCabecalho("Bearer " + token);

        assertThat(sessao).isPresent();
        assertThat(sessao.get().usuarioId()).isEqualTo(1L);
        assertThat(sessao.get().tokenRenovado()).isNull();
    }

    @Test
    void depoisDaMetadeDoPrazoDevolveUmTokenNovo() {
        String token = jwtService.gerarToken(usuario);

        Optional<AutenticacaoPorToken.Sessao> sessao = em(Instant.now().plus(Duration.ofDays(4))).autenticar(token);

        assertThat(sessao).isPresent();
        assertThat(sessao.get().tokenRenovado()).isNotBlank();
    }

    @Test
    void trocarASenhaDesconectaOsTokensAntigos() {
        String token = jwtService.gerarToken(usuario);
        usuario.setSenhaAlteradaEm(Instant.now().plusSeconds(5));

        assertThat(em(Instant.now()).autenticar(token)).isEmpty();
    }

    @Test
    void contaEncerradaNaoAutentica() {
        String token = jwtService.gerarToken(usuario);
        usuario.setEncerradoEm(Instant.now());

        assertThat(em(Instant.now()).autenticar(token)).isEmpty();
    }

    @Test
    void tokenInvalidoOuSemBearerNaoAutentica() {
        assertThat(em(Instant.now()).autenticar("qualquer-coisa")).isEmpty();
        assertThat(em(Instant.now()).autenticarCabecalho(jwtService.gerarToken(usuario))).isEmpty();
    }

}
