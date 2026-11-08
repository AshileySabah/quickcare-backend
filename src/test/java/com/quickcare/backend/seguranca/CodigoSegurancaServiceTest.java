package com.quickcare.backend.seguranca;

import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.seguranca.exception.CodigoInvalidoException;
import com.quickcare.backend.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodigoSegurancaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");

    @Mock private CodigoSegurancaRepository repository;
    @Mock private EmailService emailService;

    private CodigoSegurancaService service;
    private final Usuario usuario = Usuario.builder().id(1L).nome("Ana").email("ana@quickcare.dev").build();

    @BeforeEach
    void setUp() {
        service = new CodigoSegurancaService(repository, emailService, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private CodigoSeguranca salvo(String codigo, Instant criadoEm, int tentativas) {
        return CodigoSeguranca.builder().id(5L).usuario(usuario).finalidade(FinalidadeCodigo.TROCAR_EMAIL)
                .codigoHash(CodigoSegurancaService.hash(codigo)).dado("novo@quickcare.dev").tentativas(tentativas)
                .expiraEm(criadoEm.plus(CodigoSegurancaService.VALIDADE_CODIGO)).criadoEm(criadoEm).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void enviaUmCodigoDeSeisNumerosEGuardaSoOHash() {
        when(repository.findByUsuarioIdAndFinalidade(1L, FinalidadeCodigo.TROCAR_EMAIL)).thenReturn(Optional.empty());
        ArgumentCaptor<CodigoSeguranca> salvo = ArgumentCaptor.forClass(CodigoSeguranca.class);
        ArgumentCaptor<Map<String, Object>> variaveis = ArgumentCaptor.forClass(Map.class);

        service.enviarCodigo(usuario, FinalidadeCodigo.TROCAR_EMAIL, "novo@quickcare.dev", "novo@quickcare.dev", "confirmar");

        verify(repository).save(salvo.capture());
        verify(emailService).enviarEmailTemplate(eq("novo@quickcare.dev"), anyString(), eq("seguranca"), variaveis.capture());
        String codigo = (String) variaveis.getValue().get("codigo");
        assertThat(codigo).matches("\\d{6}");
        assertThat(salvo.getValue().getCodigoHash()).isEqualTo(CodigoSegurancaService.hash(codigo)).isNotEqualTo(codigo);
        assertThat(salvo.getValue().getDado()).isEqualTo("novo@quickcare.dev");
        assertThat(salvo.getValue().getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(10)));
    }

    @Test
    void naoEnviaOutroCodigoAntesDeUmMinuto() {
        when(repository.findByUsuarioIdAndFinalidade(1L, FinalidadeCodigo.TROCAR_EMAIL))
                .thenReturn(Optional.of(salvo("123456", AGORA.minusSeconds(30), 0)));

        assertThatThrownBy(() -> service.enviarCodigo(usuario, FinalidadeCodigo.TROCAR_EMAIL, "x", "x", "confirmar"))
                .isInstanceOf(CodigoInvalidoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void codigoCertoDevolveODadoEApagaOCodigo() {
        CodigoSeguranca codigo = salvo("123456", AGORA.minusSeconds(60), 0);
        when(repository.findByUsuarioIdAndFinalidade(1L, FinalidadeCodigo.TROCAR_EMAIL)).thenReturn(Optional.of(codigo));

        String dado = service.confirmar(usuario, FinalidadeCodigo.TROCAR_EMAIL, "123456");

        assertThat(dado).isEqualTo("novo@quickcare.dev");
        verify(repository).delete(codigo);
    }

    @Test
    void codigoErradoContaATentativaEApagaNaQuinta() {
        CodigoSeguranca codigo = salvo("123456", AGORA.minusSeconds(60), 3);
        when(repository.findByUsuarioIdAndFinalidade(1L, FinalidadeCodigo.TROCAR_EMAIL)).thenReturn(Optional.of(codigo));

        assertThatThrownBy(() -> service.confirmar(usuario, FinalidadeCodigo.TROCAR_EMAIL, "000000"))
                .isInstanceOf(CodigoInvalidoException.class)
                .hasMessageContaining("Código incorreto.");
        assertThat(codigo.getTentativas()).isEqualTo(4);
        verify(repository, never()).delete(any());

        assertThatThrownBy(() -> service.confirmar(usuario, FinalidadeCodigo.TROCAR_EMAIL, "000000"))
                .isInstanceOf(CodigoInvalidoException.class)
                .hasMessageContaining("Peça um novo código");
        verify(repository).delete(codigo);
    }

    @Test
    void codigoExpiradoNaoVale() {
        CodigoSeguranca codigo = salvo("123456", AGORA.minus(Duration.ofMinutes(11)), 0);
        when(repository.findByUsuarioIdAndFinalidade(1L, FinalidadeCodigo.TROCAR_EMAIL)).thenReturn(Optional.of(codigo));

        assertThatThrownBy(() -> service.confirmar(usuario, FinalidadeCodigo.TROCAR_EMAIL, "123456"))
                .isInstanceOf(CodigoInvalidoException.class)
                .hasMessageContaining("expirou");
        verify(repository).delete(codigo);
    }

    @Test
    void naoTrocaOLinkDeDesfazerQueAindaVale() {
        CodigoSeguranca vigente = CodigoSeguranca.builder().id(8L).usuario(usuario).finalidade(FinalidadeCodigo.DESFAZER_EMAIL)
                .codigoHash("x").dado("primeiro@quickcare.dev").expiraEm(AGORA.plus(Duration.ofDays(3))).build();
        when(repository.findByUsuarioIdAndFinalidade(1L, FinalidadeCodigo.DESFAZER_EMAIL)).thenReturn(Optional.of(vigente));

        assertThat(service.gerarTokenDeDesfazerEmail(usuario, "segundo@quickcare.dev", "hash")).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void desfazerComSenhaErradaContaATentativaSemApagarOLink() {
        CodigoSeguranca desfazer = CodigoSeguranca.builder().id(8L).usuario(usuario).finalidade(FinalidadeCodigo.DESFAZER_EMAIL)
                .codigoHash(CodigoSegurancaService.hash("tok")).senhaHashAnterior("hash")
                .expiraEm(AGORA.plus(Duration.ofDays(3))).build();
        when(repository.findByCodigoHashAndFinalidade(CodigoSegurancaService.hash("tok"), FinalidadeCodigo.DESFAZER_EMAIL))
                .thenReturn(Optional.of(desfazer));

        assertThatThrownBy(() -> service.usarTokenDeDesfazerEmail("tok", senhaHash -> false))
                .isInstanceOf(CodigoInvalidoException.class)
                .hasMessageContaining("Senha incorreta");
        assertThat(desfazer.getTentativas()).isEqualTo(1);
        verify(repository, never()).delete(any());

        assertThat(service.usarTokenDeDesfazerEmail("tok", "hash"::equals)).isEqualTo(desfazer);
        verify(repository).delete(desfazer);
    }

    @Test
    void linkDeRecuperacaoVencidoNaoVale() {
        CodigoSeguranca token = CodigoSeguranca.builder().id(6L).usuario(usuario).finalidade(FinalidadeCodigo.RECUPERAR_CONTA)
                .codigoHash(CodigoSegurancaService.hash("tok")).expiraEm(AGORA.minusSeconds(1)).criadoEm(AGORA.minus(Duration.ofDays(30)))
                .build();
        when(repository.findByCodigoHashAndFinalidade(CodigoSegurancaService.hash("tok"), FinalidadeCodigo.RECUPERAR_CONTA))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.usarTokenDeRecuperacao("tok")).isInstanceOf(CodigoInvalidoException.class);
    }

}
