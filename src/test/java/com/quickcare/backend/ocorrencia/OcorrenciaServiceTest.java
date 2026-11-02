package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcorrenciaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-02T12:00:00Z");

    @Mock private OcorrenciaRepository ocorrenciaRepository;
    @Mock private BloqueioRepository bloqueioRepository;
    @Mock private Notificador notificador;
    @Mock private RecursoOcorrenciaRepository recursoRepository;
    @Mock private UsuarioRepository usuarioRepository;

    private OcorrenciaService service;
    private final Usuario usuario = Usuario.builder().id(7L).nome("Carla").build();
    private final Usuario admin = Usuario.builder().id(1L).nome("Admin").build();
    private final List<Instant> validas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new OcorrenciaService(ocorrenciaRepository, bloqueioRepository, recursoRepository, usuarioRepository, notificador, Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(ocorrenciaRepository.save(any())).thenAnswer(invocation -> {
            Ocorrencia ocorrencia = invocation.getArgument(0);
            validas.add(ocorrencia.getCriadoEm());
            return ocorrencia;
        });
        lenient().when(ocorrenciaRepository.findMomentosValidos(7L, TipoPerfil.PROFISSIONAL)).thenAnswer(invocation -> validas);
        lenient().when(bloqueioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aTerceiraOcorrenciaBloqueiaPor7DiasEAvisa() {
        validas.addAll(List.of(AGORA.minus(Duration.ofDays(30)), AGORA.minus(Duration.ofDays(3))));
        when(bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(7L, TipoPerfil.PROFISSIONAL))
                .thenReturn(Optional.empty());

        service.lancarManual(admin, usuario, TipoPerfil.PROFISSIONAL, "Faltou sem avisar, confirmado por telefone.");

        ArgumentCaptor<Bloqueio> bloqueio = ArgumentCaptor.forClass(Bloqueio.class);
        verify(bloqueioRepository).save(bloqueio.capture());
        assertThat(bloqueio.getValue().getNivel()).isEqualTo(1);
        assertThat(bloqueio.getValue().getFim()).isEqualTo(AGORA.plus(Duration.ofDays(7)));
        verify(notificador).ocorrenciaRegistrada(any(), eq(3));
        verify(notificador).bloqueioAplicado(bloqueio.getValue());
    }

    @Test
    void comMenosDe3NaoBloqueia() {
        when(bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(7L, TipoPerfil.PROFISSIONAL))
                .thenReturn(Optional.empty());

        service.lancarManual(admin, usuario, TipoPerfil.PROFISSIONAL, "Primeira ocorrência lançada manualmente.");

        verify(bloqueioRepository, never()).save(any());
        verify(notificador).ocorrenciaRegistrada(any(), eq(1));
    }

    @Test
    void bloqueadoNaoEnviaPropostaEOAvisoDizAteQuando() {
        validas.addAll(List.of(AGORA.minus(Duration.ofDays(20)), AGORA.minus(Duration.ofDays(5)), AGORA.minus(Duration.ofDays(1))));
        Bloqueio valendo = Bloqueio.builder().usuario(usuario).perfil(TipoPerfil.PROFISSIONAL).nivel(1)
                .inicio(AGORA.minus(Duration.ofDays(1))).fim(Instant.parse("2026-11-08T15:00:00Z")).build();
        when(bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(7L, TipoPerfil.PROFISSIONAL))
                .thenReturn(Optional.of(valendo));

        assertThatThrownBy(() -> service.exigirSemBloqueio(7L, TipoPerfil.PROFISSIONAL))
                .isInstanceOf(PerfilNaoAutorizadoException.class)
                .hasMessageContaining("enviar propostas até 08/11 às 12:00");
    }

    @Test
    void anularRegistraAJustificativaENaoAnulaDuasVezes() {
        Ocorrencia ocorrencia = Ocorrencia.builder().id(3L).usuario(usuario).perfil(TipoPerfil.PROFISSIONAL)
                .motivo(MotivoOcorrencia.CANCELAMENTO_ATENDIMENTO).criadoEm(AGORA).build();
        when(ocorrenciaRepository.findById(3L)).thenReturn(Optional.of(ocorrencia));

        service.anular(admin, 3L, "Cancelou por internação comprovada");

        assertThat(ocorrencia.anulada()).isTrue();
        assertThat(ocorrencia.getAnuladaPor()).isEqualTo(admin);
        verify(notificador).ocorrenciaAnulada(ocorrencia);
        assertThatThrownBy(() -> service.anular(admin, 3L, "De novo"))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    @Test
    void aTerceiraBloqueiaMesmoQuandoOBancoArredondaOHorarioParaCima() {
        validas.addAll(List.of(AGORA.minus(Duration.ofDays(30)), AGORA.minus(Duration.ofDays(3))));
        doAnswer(invocation -> {
            Ocorrencia ocorrencia = invocation.getArgument(0);
            validas.add(ocorrencia.getCriadoEm().plusNanos(500));
            return ocorrencia;
        }).when(ocorrenciaRepository).save(any());
        when(bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(7L, TipoPerfil.PROFISSIONAL))
                .thenReturn(Optional.empty());

        service.lancarManual(admin, usuario, TipoPerfil.PROFISSIONAL, "Terceira ocorrência.");

        verify(bloqueioRepository).save(any());
    }

    @Test
    void quemJaChegouAoLimiteSemBloqueioEhBloqueadoNaChecagem() {
        validas.addAll(List.of(AGORA.minus(Duration.ofDays(30)), AGORA.minus(Duration.ofDays(3)), AGORA.minus(Duration.ofHours(1))));
        when(bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(7L, TipoPerfil.PROFISSIONAL))
                .thenReturn(Optional.empty());
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.exigirSemBloqueio(7L, TipoPerfil.PROFISSIONAL))
                .isInstanceOf(PerfilNaoAutorizadoException.class)
                .hasMessageContaining("enviar propostas até 09/11");
        verify(notificador).bloqueioAplicado(any());
    }

    @Test
    void recorreUmaVezSo() {
        Ocorrencia ocorrencia = Ocorrencia.builder().id(3L).usuario(usuario).perfil(TipoPerfil.PROFISSIONAL)
                .motivo(MotivoOcorrencia.CANCELAMENTO_ATENDIMENTO).criadoEm(AGORA).build();
        when(ocorrenciaRepository.findById(3L)).thenReturn(Optional.of(ocorrencia));
        when(recursoRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.recorrer(7L, TipoPerfil.PROFISSIONAL, 3L, "O paciente pediu para cancelar por mensagem.");

        assertThat(ocorrencia.getRecurso().emAnalise()).isTrue();
        assertThat(ocorrencia.podeRecorrer()).isFalse();
        assertThatThrownBy(() -> service.recorrer(7L, TipoPerfil.PROFISSIONAL, 3L, "De novo"))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("já recorreu");
    }

    @Test
    void naoComparecimentoSeContestaPeloAtendimentoNaoPorRecurso() {
        Ocorrencia ocorrencia = Ocorrencia.builder().id(3L).usuario(usuario).perfil(TipoPerfil.PROFISSIONAL)
                .motivo(MotivoOcorrencia.NAO_COMPARECIMENTO).criadoEm(AGORA).build();
        when(ocorrenciaRepository.findById(3L)).thenReturn(Optional.of(ocorrencia));

        assertThatThrownBy(() -> service.recorrer(7L, TipoPerfil.PROFISSIONAL, 3L, "Eu fui"))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    @Test
    void recursoAceitoQueTiraDoLimiteDesfazOBloqueioEOContadorVolta() {
        Ocorrencia recorrida = Ocorrencia.builder().id(3L).usuario(usuario).perfil(TipoPerfil.PROFISSIONAL)
                .motivo(MotivoOcorrencia.MANUAL).criadoEm(AGORA.minus(Duration.ofHours(2))).build();
        RecursoOcorrencia recurso = RecursoOcorrencia.builder().id(10L).ocorrencia(recorrida)
                .texto("Foi combinado").status(RecursoOcorrencia.Status.EM_ANALISE).build();
        validas.addAll(List.of(AGORA.minus(Duration.ofDays(20)), AGORA.minus(Duration.ofDays(1))));
        Bloqueio bloqueio = Bloqueio.builder().id(5L).usuario(usuario).perfil(TipoPerfil.PROFISSIONAL).nivel(1)
                .inicio(AGORA.minus(Duration.ofHours(1))).fim(AGORA.plus(Duration.ofDays(7))).build();
        when(recursoRepository.findById(10L)).thenReturn(Optional.of(recurso));
        when(bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(7L, TipoPerfil.PROFISSIONAL))
                .thenAnswer(invocation -> bloqueio.isDesfeito() ? Optional.empty() : Optional.of(bloqueio));

        service.decidirRecurso(admin, 10L, true, "Mensagens confirmam o combinado.");

        assertThat(bloqueio.isDesfeito()).isTrue();
        assertThat(bloqueio.ativoEm(AGORA)).isFalse();
        verify(notificador).bloqueioDesfeito(bloqueio);
        assertThat(service.contando(7L, TipoPerfil.PROFISSIONAL)).isEqualTo(2);
        service.exigirSemBloqueio(7L, TipoPerfil.PROFISSIONAL);
    }

    @Test
    void adminAplicaBloqueioNoProximoNivelEAnularOcorrenciaNaoODesfaz() {
        Bloqueio anterior = Bloqueio.builder().id(4L).usuario(usuario).perfil(TipoPerfil.PROFISSIONAL).nivel(1)
                .inicio(AGORA.minus(Duration.ofDays(20))).fim(AGORA.minus(Duration.ofDays(13))).build();
        validas.add(AGORA.minus(Duration.ofDays(10)));
        when(bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(7L, TipoPerfil.PROFISSIONAL))
                .thenReturn(Optional.of(anterior));

        Bloqueio aplicado = service.aplicarBloqueio(admin, usuario, TipoPerfil.PROFISSIONAL, "Comportamento inadequado relatado.");

        assertThat(aplicado.getNivel()).isEqualTo(2);
        assertThat(aplicado.getFim()).isEqualTo(aplicado.getInicio().plus(Duration.ofDays(14)));
        assertThat(aplicado.aplicadoPeloAdmin()).isTrue();
        verify(notificador).bloqueioAplicado(aplicado);

        when(bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(7L, TipoPerfil.PROFISSIONAL))
                .thenReturn(Optional.of(aplicado));
        assertThatThrownBy(() -> service.exigirSemBloqueio(7L, TipoPerfil.PROFISSIONAL))
                .isInstanceOf(PerfilNaoAutorizadoException.class);
        assertThat(aplicado.isDesfeito()).isFalse();
        assertThatThrownBy(() -> service.aplicarBloqueio(admin, usuario, TipoPerfil.PROFISSIONAL, "De novo"))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    @Test
    void anularUmaQueNaoFazFaltaMantemOBloqueio() {
        Ocorrencia extra = Ocorrencia.builder().id(4L).usuario(usuario).perfil(TipoPerfil.PROFISSIONAL)
                .motivo(MotivoOcorrencia.MANUAL).criadoEm(AGORA.minus(Duration.ofDays(2))).build();
        validas.addAll(List.of(AGORA.minus(Duration.ofDays(30)), AGORA.minus(Duration.ofDays(20)),
                AGORA.minus(Duration.ofDays(3))));
        Bloqueio bloqueio = Bloqueio.builder().id(5L).usuario(usuario).perfil(TipoPerfil.PROFISSIONAL).nivel(1)
                .inicio(AGORA.minus(Duration.ofDays(1))).fim(AGORA.plus(Duration.ofDays(6))).build();
        when(ocorrenciaRepository.findById(4L)).thenReturn(Optional.of(extra));
        when(bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(7L, TipoPerfil.PROFISSIONAL))
                .thenReturn(Optional.of(bloqueio));

        service.anular(admin, 4L, "Lançada em duplicidade.");

        assertThat(bloqueio.isDesfeito()).isFalse();
        verify(notificador, never()).bloqueioDesfeito(any());
    }

    @Test
    void recursoAceitoAnulaAOcorrenciaENegadoMantem() {
        Ocorrencia aceita = Ocorrencia.builder().id(3L).usuario(usuario).perfil(TipoPerfil.PROFISSIONAL)
                .motivo(MotivoOcorrencia.CANCELAMENTO_ATENDIMENTO).criadoEm(AGORA).build();
        RecursoOcorrencia recursoAceito = RecursoOcorrencia.builder().id(10L).ocorrencia(aceita)
                .texto("Internação").status(RecursoOcorrencia.Status.EM_ANALISE).build();
        Ocorrencia negada = Ocorrencia.builder().id(4L).usuario(usuario).perfil(TipoPerfil.PROFISSIONAL)
                .motivo(MotivoOcorrencia.MANUAL).criadoEm(AGORA).build();
        RecursoOcorrencia recursoNegado = RecursoOcorrencia.builder().id(11L).ocorrencia(negada)
                .texto("Não fui eu").status(RecursoOcorrencia.Status.EM_ANALISE).build();
        when(recursoRepository.findById(10L)).thenReturn(Optional.of(recursoAceito));
        when(recursoRepository.findById(11L)).thenReturn(Optional.of(recursoNegado));

        service.decidirRecurso(admin, 10L, true, "Atestado anexado ao chamado.");
        service.decidirRecurso(admin, 11L, false, "Sem comprovação.");

        assertThat(recursoAceito.getStatus()).isEqualTo(RecursoOcorrencia.Status.ANULADA);
        assertThat(aceita.anulada()).isTrue();
        assertThat(recursoNegado.getStatus()).isEqualTo(RecursoOcorrencia.Status.MANTIDA);
        assertThat(negada.anulada()).isFalse();
        verify(notificador).recursoDecidido(recursoAceito);
        verify(notificador).recursoDecidido(recursoNegado);
        assertThatThrownBy(() -> service.decidirRecurso(admin, 11L, true, "Mudei de ideia"))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    @Test
    void removerBloqueioQueJaAcabouNaoVale() {
        Bloqueio vencido = Bloqueio.builder().id(9L).nivel(1)
                .inicio(AGORA.minus(Duration.ofDays(10))).fim(AGORA.minus(Duration.ofDays(3))).build();
        when(bloqueioRepository.findById(9L)).thenReturn(Optional.of(vencido));

        assertThatThrownBy(() -> service.removerBloqueio(admin, 9L, "Já acabou"))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

}
