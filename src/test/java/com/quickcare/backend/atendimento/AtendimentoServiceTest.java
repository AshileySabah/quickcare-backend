package com.quickcare.backend.atendimento;

import com.quickcare.backend.atendimento.dto.AtendimentoResponse;
import com.quickcare.backend.atendimento.dto.RemarcacaoRequest;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.ocorrencia.MotivoOcorrencia;
import com.quickcare.backend.ocorrencia.OcorrenciaService;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * As regras combinadas para o atendimento (branch 4a): aceite ocupa vaga,
 * edição do paciente pede reaceite (vaga reservada, aberta a propostas), troca
 * do aceito, remarcação, cancelamento com a regra das 48h e as mudanças no horário.
 */
@ExtendWith(MockitoExtension.class)
class AtendimentoServiceTest {

    private static final Long USUARIO_PACIENTE_ID = 1L;
    private static final Long USUARIO_PROFISSIONAL_ID = 2L;
    private static final Instant AGORA = Instant.parse("2026-11-02T12:00:00Z");

    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private AtendimentoEventoRepository eventoRepository;
    @Mock private PropostaRepository propostaRepository;
    @Mock private OcorrenciaService ocorrenciaService;
    @Mock private Notificador notificador;
    @Mock private PlatformTransactionManager transactionManager;

    private RelogioDeTeste relogio;
    private AtendimentoService service;

    private Especialidade curativos;
    private Usuario usuarioPaciente;
    private Solicitacao solicitacao;
    private SolicitacaoProfissional linha;
    private final List<Proposta> pendentes = new ArrayList<>();
    private final List<Atendimento> atendimentos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        relogio = new RelogioDeTeste(AGORA);
        service = new AtendimentoService(atendimentoRepository, eventoRepository, propostaRepository,
                ocorrenciaService, notificador, relogio, transactionManager);

        curativos = Especialidade.builder().id(5L).nome("Curativos").categoria(CategoriaProfissional.ENFERMEIRO).build();
        usuarioPaciente = Usuario.builder().id(USUARIO_PACIENTE_ID).nome("Paciente").build();
        solicitacao = Solicitacao.builder()
                .id(50L)
                .paciente(Paciente.builder().id(10L).usuario(usuarioPaciente).build())
                .profissionais(new LinkedHashSet<>())
                .modalidade(ModalidadeSolicitacao.ONLINE)
                .prazoDesejado(LocalDate.of(2026, 11, 30))
                .status(StatusSolicitacao.RECEBENDO_PROPOSTAS)
                .build();
        linha = SolicitacaoProfissional.builder()
                .id(1L)
                .solicitacao(solicitacao)
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .especialidades(new LinkedHashSet<>(List.of(curativos)))
                .quantidade(1)
                .build();
        solicitacao.getProfissionais().add(linha);

        // Os repositórios refletem as listas do cenário.
        lenient().when(propostaRepository.findBySolicitacaoIdAndStatus(eq(50L), eq(StatusProposta.PENDENTE)))
                .thenAnswer(invocation -> pendentes.stream().filter(p -> p.getStatus() == StatusProposta.PENDENTE).toList());
        lenient().when(propostaRepository.existsBySolicitacaoIdAndStatus(eq(50L), eq(StatusProposta.PENDENTE)))
                .thenAnswer(invocation -> pendentes.stream().anyMatch(p -> p.getStatus() == StatusProposta.PENDENTE));
        lenient().when(atendimentoRepository.save(any())).thenAnswer(invocation -> {
            Atendimento atendimento = invocation.getArgument(0);
            if (atendimento.getId() == null) {
                atendimento.setId(900L + atendimentos.size());
            }
            atendimentos.add(atendimento);
            return atendimento;
        });
        lenient().when(atendimentoRepository.findBySolicitacaoIdAndStatusIn(eq(50L), any())).thenAnswer(invocation -> {
            java.util.Collection<?> status = invocation.getArgument(1);
            return atendimentos.stream().filter(a -> status.contains(a.getStatus())).toList();
        });
        lenient().when(atendimentoRepository.existsBySolicitacaoIdAndStatusIn(eq(50L), any())).thenAnswer(invocation -> {
            java.util.Collection<?> status = invocation.getArgument(1);
            return atendimentos.stream().anyMatch(a -> status.contains(a.getStatus()));
        });
        lenient().when(atendimentoRepository.findWithPartesById(anyLong())).thenAnswer(invocation ->
                atendimentos.stream().filter(a -> a.getId().equals(invocation.getArgument(0))).findFirst());
    }

    // ------------------------------------------------------------- aceite

    @Test
    void aceitarOcupaAVagaAgendaERecusaQuemNaoCabeMais() {
        Proposta escolhida = pendente(100L, profissional(20L, USUARIO_PROFISSIONAL_ID), dias(5));
        Proposta concorrente = pendente(101L, profissional(21L, 3L), dias(6));

        Atendimento atendimento = service.aceitarProposta(escolhida);

        assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.AGENDADO);
        assertThat(atendimento.getInicio()).isEqualTo(escolhida.getInicio());
        assertThat(atendimento.getAceitoEm()).isEqualTo(AGORA);
        assertThat(escolhida.getStatus()).isEqualTo(StatusProposta.ACEITA);
        assertThat(linha.getVagasPreenchidas()).isEqualTo(1);
        assertThat(concorrente.getStatus()).isEqualTo(StatusProposta.RECUSADA);
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.EM_ANDAMENTO);
        verify(notificador).propostaRecusada(concorrente, true);
    }

    @Test
    void naoAceitaPropostaCujoInicioJaPassou() {
        Proposta vencida = pendente(100L, profissional(20L, USUARIO_PROFISSIONAL_ID), Duration.ofHours(-1));

        assertThatThrownBy(() -> service.aceitarProposta(vencida))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("já passou");
    }

    // ------------------------------------------- paciente edita a solicitação

    @Test
    void edicaoReservaAVagaPedeReaceiteEVoltaAReceberPropostas() {
        Atendimento atendimento = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), dias(5));
        solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);

        editarSolicitacao();

        assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.AGUARDANDO_REACEITE);
        assertThat(atendimento.getReaceiteSolicitadoEm()).isEqualTo(AGORA);
        assertThat(linha.getVagasPreenchidas()).isEqualTo(1);
        assertThat(linha.getVagasReservadas()).isEqualTo(1);
        // A vaga reservada aceita propostas: o paciente pode escolher outro enquanto o aceito não responde.
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.RECEBENDO_PROPOSTAS);
        assertThat(solicitacao.atendidaPor(profissional(30L, 7L))).isTrue();
    }

    @Test
    void reaceitarFechaAVagaERecusaPendentesQueNaoCabem() {
        Atendimento atendimento = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), dias(5));
        editarSolicitacao();
        Proposta nova = pendente(101L, profissional(21L, 3L), dias(6));
        relogio.avancar(Duration.ofHours(1));

        AtendimentoResponse response = service.reaceitar(USUARIO_PROFISSIONAL_ID, atendimento.getId());

        assertThat(response.status()).isEqualTo(StatusAtendimento.AGENDADO);
        assertThat(atendimento.getAceitoEm()).isEqualTo(relogio.instant());
        assertThat(linha.getVagasReservadas()).isZero();
        assertThat(nova.getStatus()).isEqualTo(StatusProposta.RECUSADA);
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.EM_ANDAMENTO);
        verify(notificador).reaceiteRespondido(atendimento.getProposta(), true);
    }

    @Test
    void profissionalRecusaANovaVersaoSemPenalidade() {
        Atendimento atendimento = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), dias(10));
        editarSolicitacao();

        service.recusarReaceite(USUARIO_PROFISSIONAL_ID, atendimento.getId());

        assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.CANCELADO);
        assertThat(atendimento.getMotivoCancelamento()).isEqualTo(MotivoCancelamento.REACEITE_RECUSADO);
        assertThat(linha.getVagasPreenchidas()).isZero();
        assertThat(linha.getVagasReservadas()).isZero();
        // Edição com mais de 48h de antecedência: ninguém leva ocorrência.
        verify(ocorrenciaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void edicaoAMenosDe48hQueOProfissionalRecusaPenalizaOPaciente() {
        Profissional profissional = profissional(20L, USUARIO_PROFISSIONAL_ID);
        Atendimento atendimento = agendado(profissional, Duration.ofHours(30));
        editarSolicitacao();

        service.recusarReaceite(USUARIO_PROFISSIONAL_ID, atendimento.getId());

        verify(ocorrenciaService).registrar(usuarioPaciente, TipoPerfil.PACIENTE,
                MotivoOcorrencia.ALTERACAO_TARDIA_SOLICITACAO, atendimento);
        verify(ocorrenciaService, never()).registrar(eq(profissional.getUsuario()), any(), any(), any());
    }

    @Test
    void pacienteAceitaOutroNaVagaReservadaEOAntigoSaiSemPenalidade() {
        Atendimento antigo = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), dias(10));
        editarSolicitacao();
        Proposta outra = pendente(101L, profissional(21L, 3L), dias(10));

        Atendimento novo = service.aceitarProposta(outra);

        assertThat(antigo.getStatus()).isEqualTo(StatusAtendimento.CANCELADO);
        assertThat(antigo.getMotivoCancelamento()).isEqualTo(MotivoCancelamento.SUBSTITUIDO);
        assertThat(antigo.getProposta().getStatus()).isEqualTo(StatusProposta.RECUSADA);
        assertThat(novo.getStatus()).isEqualTo(StatusAtendimento.AGENDADO);
        assertThat(outra.getVaga()).isSameAs(linha);
        assertThat(linha.getVagasPreenchidas()).isEqualTo(1);
        assertThat(linha.getVagasReservadas()).isZero();
        verify(notificador).substituido(antigo.getProposta());
        // Mais de 48h antes: o paciente não é penalizado pela troca.
        verify(ocorrenciaService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void trocarOAceitoAMenosDe48hPenalizaOPaciente() {
        Atendimento antigo = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), Duration.ofHours(40));
        editarSolicitacao();
        Proposta outra = pendente(101L, profissional(21L, 3L), Duration.ofHours(40));

        service.aceitarProposta(outra);

        verify(ocorrenciaService).registrar(usuarioPaciente, TipoPerfil.PACIENTE,
                MotivoOcorrencia.ALTERACAO_TARDIA_SOLICITACAO, antigo);
    }

    @Test
    void semReaceiteAteOInicioOAtendimentoCaiEAVagaReabre() {
        Atendimento atendimento = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), dias(5));
        editarSolicitacao();
        relogio.avancar(dias(5));
        mockVencidos(atendimento);

        service.processarVencidos();

        assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.CANCELADO);
        assertThat(atendimento.getMotivoCancelamento()).isEqualTo(MotivoCancelamento.SEM_REACEITE);
        assertThat(linha.getVagasPreenchidas()).isZero();
        verify(notificador).semReaceite(atendimento);
    }

    // ---------------------------------------------------------- remarcação

    @Test
    void pacienteRemarcaEOProfissionalAceita() {
        Atendimento atendimento = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), dias(5));
        Instant novoInicio = AGORA.plus(dias(7));

        service.solicitarRemarcacao(USUARIO_PACIENTE_ID, atendimento.getId(),
                new RemarcacaoRequest(novoInicio, novoInicio.plus(Duration.ofHours(4))));
        assertThat(atendimento.getRemarcacaoPor()).isEqualTo(LadoAtendimento.PACIENTE);
        assertThat(atendimento.getInicio()).isEqualTo(AGORA.plus(dias(5)));
        verify(notificador).remarcacaoSolicitada(atendimento);

        // Quem pediu não responde a própria remarcação.
        assertThatThrownBy(() -> service.responderRemarcacao(USUARIO_PACIENTE_ID, atendimento.getId(), true))
                .isInstanceOf(TransicaoStatusInvalidaException.class);

        relogio.avancar(Duration.ofHours(2));
        service.responderRemarcacao(USUARIO_PROFISSIONAL_ID, atendimento.getId(), true);

        assertThat(atendimento.getInicio()).isEqualTo(novoInicio);
        assertThat(atendimento.getAceitoEm()).isEqualTo(relogio.instant());
        assertThat(atendimento.temRemarcacaoPendente()).isFalse();
        verify(notificador).remarcacaoRespondida(atendimento, LadoAtendimento.PACIENTE, true);
    }

    @Test
    void recusarARemarcacaoMantemOPeriodo() {
        Atendimento atendimento = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), dias(5));
        Instant original = atendimento.getInicio();
        service.solicitarRemarcacao(USUARIO_PROFISSIONAL_ID, atendimento.getId(),
                new RemarcacaoRequest(AGORA.plus(dias(8)), AGORA.plus(dias(8)).plus(Duration.ofHours(2))));

        service.responderRemarcacao(USUARIO_PACIENTE_ID, atendimento.getId(), false);

        assertThat(atendimento.getInicio()).isEqualTo(original);
        assertThat(atendimento.temRemarcacaoPendente()).isFalse();
    }

    @Test
    void emAndamentoSoOTerminoPodeMudar() {
        Atendimento atendimento = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), Duration.ofHours(-1));
        atendimento.iniciar();

        assertThatThrownBy(() -> service.solicitarRemarcacao(USUARIO_PACIENTE_ID, atendimento.getId(),
                new RemarcacaoRequest(AGORA.plus(dias(1)), AGORA.plus(dias(2)))))
                .hasMessage("O atendimento já começou: só o término pode mudar.");

        service.solicitarRemarcacao(USUARIO_PACIENTE_ID, atendimento.getId(),
                new RemarcacaoRequest(atendimento.getInicio(), AGORA.plus(Duration.ofHours(3))));
        assertThat(atendimento.getRemarcacaoTermino()).isEqualTo(AGORA.plus(Duration.ofHours(3)));
    }

    // ------------------------------------------------------- cancelamento

    @Test
    void cancelarNas48hDepoisDoAceiteNaoGeraOcorrencia() {
        Atendimento atendimento = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), dias(10));

        service.cancelar(USUARIO_PACIENTE_ID, atendimento.getId());

        assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.CANCELADO);
        assertThat(atendimento.getCanceladoPor()).isEqualTo(LadoAtendimento.PACIENTE);
        assertThat(linha.getVagasPreenchidas()).isZero();
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.ABERTA);
        verify(ocorrenciaService, never()).registrar(any(), any(), any(), any());
        verify(notificador).atendimentoCancelado(atendimento, LadoAtendimento.PACIENTE);
    }

    @Test
    void profissionalQueCancelaDepoisDaToleranciaLevaOcorrencia() {
        Profissional profissional = profissional(20L, USUARIO_PROFISSIONAL_ID);
        Atendimento atendimento = agendado(profissional, dias(10));
        relogio.avancar(dias(3));

        service.cancelar(USUARIO_PROFISSIONAL_ID, atendimento.getId());

        assertThat(atendimento.getProposta().getStatus()).isEqualTo(StatusProposta.CANCELADA);
        verify(ocorrenciaService).registrar(profissional.getUsuario(), TipoPerfil.PROFISSIONAL,
                MotivoOcorrencia.CANCELAMENTO_ATENDIMENTO, atendimento);
    }

    // ------------------------------------------------ mudanças no horário

    @Test
    void comecaETerminaSozinhoEConcluiASolicitacao() {
        Atendimento atendimento = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), Duration.ofMinutes(10));
        solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);

        relogio.avancar(Duration.ofMinutes(10));
        mockVencidos(atendimento);
        service.processarVencidos();
        assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.EM_ANDAMENTO);
        verify(notificador).lembrete(atendimento, Notificador.Lembrete.INICIO);

        relogio.avancar(Duration.ofHours(8));
        service.processarVencidos();
        assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.FINALIZADO);
        verify(notificador).atendimentoFinalizado(atendimento);
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.CONCLUIDA);

        relogio.avancar(Duration.ofHours(72));
        service.processarVencidos();
        assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.CONCLUIDO);
    }

    @Test
    void lembretesNaVesperaE30MinutosAntes() {
        Atendimento atendimento = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), dias(3));
        when(atendimentoRepository.findIdsComLembreteDevido(any(), any())).thenReturn(List.of(atendimento.getId()));

        relogio.avancar(dias(2));
        service.processarVencidos();
        verify(notificador).lembrete(atendimento, Notificador.Lembrete.VESPERA);

        relogio.avancar(Duration.ofHours(23).plusMinutes(30));
        service.processarVencidos();
        verify(notificador).lembrete(atendimento, Notificador.Lembrete.TRINTA_MINUTOS);
    }

    @Test
    void quemAceitaDentroDaJanelaNaoRecebeOLembreteDela() {
        Atendimento atendimento = agendado(profissional(20L, USUARIO_PROFISSIONAL_ID), Duration.ofHours(10));
        when(atendimentoRepository.findIdsComLembreteDevido(any(), any())).thenReturn(List.of(atendimento.getId()));

        service.processarVencidos();

        verify(notificador, never()).lembrete(any(), eq(Notificador.Lembrete.VESPERA));
        assertThat(atendimento.getLembrete24hEm()).isNotNull();
    }

    // ---------------------------------------------------------------- apoio

    private Duration dias(int quantidade) {
        return Duration.ofDays(quantidade);
    }

    private Profissional profissional(Long id, Long usuarioId) {
        return Profissional.builder()
                .id(id)
                .usuario(Usuario.builder().id(usuarioId).nome("Profissional " + id).build())
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .modalidadeAtendimento(ModalidadeAtendimento.AMBOS)
                .especialidades(Set.of(curativos))
                .raioAtendimentoKm(new BigDecimal("10"))
                .build();
    }

    /** Proposta pendente com início daqui a {@code antecedencia} e 8h de duração. */
    private Proposta pendente(Long id, Profissional profissional, Duration antecedencia) {
        Proposta proposta = Proposta.builder()
                .id(id)
                .solicitacao(solicitacao)
                .profissional(profissional)
                .status(StatusProposta.PENDENTE)
                .inicio(relogio.instant().plus(antecedencia))
                .termino(relogio.instant().plus(antecedencia).plus(Duration.ofHours(8)))
                .build();
        pendentes.add(proposta);
        return proposta;
    }

    /** Aceita agora (o relógio do teste), com início daqui a {@code antecedencia}. */
    private Atendimento agendado(Profissional profissional, Duration antecedencia) {
        Proposta proposta = pendente(100L + pendentes.size() * 10L, profissional, antecedencia);
        if (antecedencia.isNegative()) {
            // Já começou: aceita no passado, sem passar pela validação de início futuro.
            linha.preencherVaga();
            proposta.setVaga(linha);
            proposta.setStatus(StatusProposta.ACEITA);
            Atendimento atendimento = Atendimento.agendar(proposta, relogio.instant().minus(dias(3)));
            atendimento.setId(800L);
            atendimentos.add(atendimento);
            return atendimento;
        }
        return service.aceitarProposta(proposta);
    }

    /** O que o SolicitacaoService faz ao salvar a edição (mesma linha pedida). */
    private void editarSolicitacao() {
        when(propostaRepository.findBySolicitacaoIdAndStatus(50L, StatusProposta.ACEITA))
                .thenReturn(atendimentos.stream().map(Atendimento::getProposta).toList());
        service.prepararEdicao(solicitacao);

        SolicitacaoProfissional nova = SolicitacaoProfissional.builder()
                .id(2L)
                .solicitacao(solicitacao)
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .especialidades(new LinkedHashSet<>(List.of(curativos)))
                .quantidade(1)
                .build();
        solicitacao.getProfissionais().clear();
        solicitacao.getProfissionais().add(nova);
        linha = nova;
        service.solicitacaoEditada(solicitacao);
    }

    private void mockVencidos(Atendimento atendimento) {
        when(atendimentoRepository.findIdsComTransicaoVencida(any(), any(), any())).thenReturn(List.of(atendimento.getId()));
    }

    /** Relógio que o teste avança. */
    private static final class RelogioDeTeste extends Clock {
        private Instant agora;

        private RelogioDeTeste(Instant agora) {
            this.agora = agora;
        }

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(agora, zone);
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

}
