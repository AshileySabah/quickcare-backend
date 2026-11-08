package com.quickcare.backend.avaliacao;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.avaliacao.dto.AvaliacaoRequest;
import com.quickcare.backend.avaliacao.dto.AvaliacoesPendentesResponse;
import com.quickcare.backend.avaliacao.dto.DestaqueResponse;
import com.quickcare.backend.avaliacao.dto.ResumoAvaliacoesResponse;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuarioRepository;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvaliacaoServiceTest {

    private static final Long USUARIO_PACIENTE_ID = 1L;
    private static final Long USUARIO_PROFISSIONAL_ID = 2L;
    private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");

    @Mock private AvaliacaoRepository avaliacaoRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private BloqueioUsuarioRepository bloqueioUsuarioRepository;
    @Mock private Notificador notificador;
    @Mock private PlatformTransactionManager transactionManager;

    private AvaliacaoService service;
    private Paciente paciente;
    private Profissional profissional;
    private Atendimento atendimento;

    @BeforeEach
    void setUp() {
        service = new AvaliacaoService(avaliacaoRepository, atendimentoRepository, pacienteRepository,
                profissionalRepository, bloqueioUsuarioRepository, notificador,
                Clock.fixed(AGORA, ZoneOffset.UTC), transactionManager);

        paciente = Paciente.builder().id(10L)
                .usuario(Usuario.builder().id(USUARIO_PACIENTE_ID).nome("Ana Paciente").build()).build();
        profissional = Profissional.builder().id(20L)
                .usuario(Usuario.builder().id(USUARIO_PROFISSIONAL_ID).nome("Joana Enfermeira").build()).build();

        Solicitacao solicitacao = Solicitacao.builder().id(50L).paciente(paciente).profissionais(new LinkedHashSet<>()).build();
        solicitacao.getProfissionais().add(SolicitacaoProfissional.builder()
                .id(1L)
                .solicitacao(solicitacao)
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .especialidades(new LinkedHashSet<>(List.of(
                        Especialidade.builder().id(5L).nome("Curativos").categoria(CategoriaProfissional.ENFERMEIRO).build())))
                .quantidade(1)
                .build());
        Proposta proposta = Proposta.builder().id(100L).solicitacao(solicitacao).profissional(profissional).build();

        atendimento = Atendimento.builder()
                .id(900L)
                .proposta(proposta)
                .solicitacao(solicitacao)
                .status(StatusAtendimento.FINALIZADO)
                .inicio(AGORA.minus(Duration.ofDays(1)).minus(Duration.ofHours(8)))
                .termino(AGORA.minus(Duration.ofDays(1)))
                .build();
    }

    private void atendimentoEncontrado() {
        when(atendimentoRepository.findWithPartesById(900L)).thenReturn(Optional.of(atendimento));
    }

    @Test
    void pacienteAvaliaOProfissionalDepoisDoTermino() {
        atendimentoEncontrado();
        when(avaliacaoRepository.existsByAtendimentoIdAndAvaliador(900L, LadoAtendimento.PACIENTE)).thenReturn(false);
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(paciente));
        when(avaliacaoRepository.pendentesDoPaciente(eq(10L), eq(LadoAtendimento.PACIENTE), any(), any())).thenReturn(List.of());

        AvaliacoesPendentesResponse resposta = service.avaliar(USUARIO_PACIENTE_ID, 900L,
                new AvaliacaoRequest(5, Set.of(DestaqueAvaliacao.PONTUALIDADE, DestaqueAvaliacao.CUIDADO)));

        ArgumentCaptor<Avaliacao> avaliacao = ArgumentCaptor.forClass(Avaliacao.class);
        verify(avaliacaoRepository).save(avaliacao.capture());
        assertThat(avaliacao.getValue().getAvaliador()).isEqualTo(LadoAtendimento.PACIENTE);
        assertThat(avaliacao.getValue().getNota()).isEqualTo(5);
        assertThat(avaliacao.getValue().getProfissional()).isSameAs(profissional);
        assertThat(avaliacao.getValue().getPaciente()).isSameAs(paciente);
        assertThat(avaliacao.getValue().getDestaques())
                .containsExactlyInAnyOrder(DestaqueAvaliacao.PONTUALIDADE, DestaqueAvaliacao.CUIDADO);
        assertThat(resposta.pendentes()).isEmpty();
        assertThat(resposta.destaques()).extracting(DestaqueResponse::codigo)
                .contains(DestaqueAvaliacao.CUIDADO)
                .doesNotContain(DestaqueAvaliacao.COLABORACAO);
    }

    @Test
    void naoAvaliaAntesDoTermino() {
        atendimento.setStatus(StatusAtendimento.EM_ANDAMENTO);
        atendimentoEncontrado();

        assertThatThrownBy(() -> service.avaliar(USUARIO_PACIENTE_ID, 900L, new AvaliacaoRequest(4, Set.of())))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("A avaliação abre quando o atendimento terminar.");
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void avaliacaoFicaSuspensaEnquantoHaProblemaRelatado() {
        atendimento.setStatus(StatusAtendimento.PROBLEMA_RELATADO);
        atendimentoEncontrado();

        assertThatThrownBy(() -> service.avaliar(USUARIO_PROFISSIONAL_ID, 900L, new AvaliacaoRequest(4, Set.of())))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("problema relatado");
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void atendimentoNaoRealizadoNaoPodeSerAvaliado() {
        atendimento.setStatus(StatusAtendimento.NAO_REALIZADO);
        atendimentoEncontrado();

        assertThatThrownBy(() -> service.avaliar(USUARIO_PACIENTE_ID, 900L, new AvaliacaoRequest(1, Set.of())))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Este atendimento não pode ser avaliado.");
    }

    @Test
    void janelaDeAvaliacaoFechaEm30DiasDepoisDoTermino() {
        atendimento.setStatus(StatusAtendimento.CONCLUIDO);
        atendimento.setTermino(AGORA.minus(Duration.ofDays(30)));
        atendimentoEncontrado();

        assertThatThrownBy(() -> service.avaliar(USUARIO_PACIENTE_ID, 900L, new AvaliacaoRequest(3, Set.of())))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("O prazo para avaliar este atendimento já acabou.");
    }

    @Test
    void cadaLadoAvaliaUmaVezSo() {
        atendimentoEncontrado();
        when(avaliacaoRepository.existsByAtendimentoIdAndAvaliador(900L, LadoAtendimento.PROFISSIONAL)).thenReturn(true);

        assertThatThrownBy(() -> service.avaliar(USUARIO_PROFISSIONAL_ID, 900L, new AvaliacaoRequest(5, Set.of())))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Você já avaliou este atendimento.");
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void destaquesPrecisamSerDaListaDoLadoAvaliado() {
        atendimentoEncontrado();
        when(avaliacaoRepository.existsByAtendimentoIdAndAvaliador(900L, LadoAtendimento.PACIENTE)).thenReturn(false);

        assertThatThrownBy(() -> service.avaliar(USUARIO_PACIENTE_ID, 900L,
                new AvaliacaoRequest(4, Set.of(DestaqueAvaliacao.COLABORACAO))))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Escolha os destaques da lista.");
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void perfilBanidoNaoAvalia() {
        profissional.setBanido(true);
        atendimentoEncontrado();

        assertThatThrownBy(() -> service.avaliar(USUARIO_PROFISSIONAL_ID, 900L, new AvaliacaoRequest(5, Set.of())))
                .hasMessage("Seu perfil não pode avaliar atendimentos.");
    }

    @Test
    void quemNaoParticipouDoAtendimentoNaoOEncontra() {
        atendimentoEncontrado();

        assertThatThrownBy(() -> service.avaliar(99L, 900L, new AvaliacaoRequest(5, Set.of())))
                .isInstanceOf(com.quickcare.backend.atendimento.exception.AtendimentoNaoEncontradoException.class);
    }

    @Test
    void pendentesDoProfissionalEscondemONomeQuandoHaBloqueio() {
        when(profissionalRepository.findByUsuarioId(USUARIO_PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));
        when(avaliacaoRepository.pendentesDoProfissional(eq(20L), eq(LadoAtendimento.PROFISSIONAL), any(), any()))
                .thenReturn(List.of(atendimento));
        when(bloqueioUsuarioRepository.existsByPacienteIdAndProfissionalId(10L, 20L)).thenReturn(true);

        AvaliacoesPendentesResponse resposta = service.pendentes(USUARIO_PROFISSIONAL_ID, TipoPerfil.PROFISSIONAL);

        assertThat(resposta.pendentes()).singleElement().satisfies(pendente -> {
            assertThat(pendente.atendimentoId()).isEqualTo(900L);
            assertThat(pendente.servico()).isEqualTo("Curativos");
            assertThat(pendente.nomeAvaliado()).isNull();
            assertThat(pendente.prazo()).isEqualTo(atendimento.getTermino().plus(Duration.ofDays(30)));
        });
        assertThat(resposta.destaques()).extracting(DestaqueResponse::codigo)
                .contains(DestaqueAvaliacao.COLABORACAO)
                .doesNotContain(DestaqueAvaliacao.CUIDADO);
    }

    @Test
    void pendentesDoPacienteMostramONomeDoProfissional() {
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(paciente));
        when(avaliacaoRepository.pendentesDoPaciente(eq(10L), eq(LadoAtendimento.PACIENTE), any(), eq(AGORA.minus(Duration.ofDays(30)))))
                .thenReturn(List.of(atendimento));

        AvaliacoesPendentesResponse resposta = service.pendentes(USUARIO_PACIENTE_ID, TipoPerfil.PACIENTE);

        assertThat(resposta.pendentes()).singleElement()
                .satisfies(pendente -> assertThat(pendente.nomeAvaliado()).isEqualTo("Joana Enfermeira"));
    }

    @Test
    void resumoArredondaAMediaEMostraOsTresDestaquesMaisRecebidos() {
        when(avaliacaoRepository.resumoDoProfissional(eq(20L), any(), eq(AGORA.minus(Duration.ofDays(30)))))
                .thenReturn(resumo(4.3333, 3));
        when(avaliacaoRepository.destaquesDoProfissional(eq(20L), any(), any())).thenReturn(List.of(
                contagem(DestaqueAvaliacao.PONTUALIDADE, 3),
                contagem(DestaqueAvaliacao.EDUCACAO, 2),
                contagem(DestaqueAvaliacao.ATRASO, 1),
                contagem(DestaqueAvaliacao.CUIDADO, 1)));

        ResumoAvaliacoesResponse resumo = service.resumoDoProfissional(20L);

        assertThat(resumo.media()).isEqualTo(4.3);
        assertThat(resumo.total()).isEqualTo(3);
        assertThat(resumo.destaques()).extracting(ResumoAvaliacoesResponse.DestaqueRecebido::rotulo)
                .containsExactly("Pontualidade", "Educação", "Atraso");
        assertThat(resumo.destaques().get(2).positivo()).isFalse();
    }

    @Test
    void semAvaliacoesVisiveisNaoTemMedia() {
        when(avaliacaoRepository.resumoDoPaciente(eq(10L), any(), any())).thenReturn(resumo(null, 0));
        when(avaliacaoRepository.destaquesDoPaciente(eq(10L), any(), any())).thenReturn(List.of());

        ResumoAvaliacoesResponse resumo = service.resumoDoPaciente(10L);

        assertThat(resumo.media()).isNull();
        assertThat(resumo.total()).isZero();
        assertThat(resumo.destaques()).isEmpty();
    }

    @Test
    void lembraEm3DiasSoQuemAindaNaoAvaliou() {
        atendimento.setTermino(AGORA.minus(Duration.ofDays(3)).minus(Duration.ofHours(1)));
        when(avaliacaoRepository.findIdsComLembreteDeAvaliacaoDevido(any(), any(), any(), any())).thenReturn(List.of(900L));
        atendimentoEncontrado();
        when(avaliacaoRepository.findAvaliadoresDoAtendimento(900L)).thenReturn(List.of(LadoAtendimento.PACIENTE));

        service.processarLembretes();

        verify(notificador).lembreteDeAvaliacao(atendimento, LadoAtendimento.PROFISSIONAL,
                atendimento.getTermino().plus(Duration.ofDays(30)));
        verify(notificador, never()).lembreteDeAvaliacao(eq(atendimento), eq(LadoAtendimento.PACIENTE), any());
        assertThat(atendimento.getLembreteAvaliacao3dEm()).isEqualTo(AGORA);
        assertThat(atendimento.getLembreteAvaliacao7dEm()).isNull();
    }

    @Test
    void depoisDe7DiasMandaUmLembreteSoEMarcaOsDois() {
        atendimento.setStatus(StatusAtendimento.CONCLUIDO);
        atendimento.setTermino(AGORA.minus(Duration.ofDays(8)));
        when(avaliacaoRepository.findIdsComLembreteDeAvaliacaoDevido(any(), any(), any(), any())).thenReturn(List.of(900L));
        atendimentoEncontrado();
        when(avaliacaoRepository.findAvaliadoresDoAtendimento(900L)).thenReturn(List.of());

        service.processarLembretes();

        verify(notificador).lembreteDeAvaliacao(eq(atendimento), eq(LadoAtendimento.PACIENTE), any());
        verify(notificador).lembreteDeAvaliacao(eq(atendimento), eq(LadoAtendimento.PROFISSIONAL), any());
        assertThat(atendimento.getLembreteAvaliacao3dEm()).isEqualTo(AGORA);
        assertThat(atendimento.getLembreteAvaliacao7dEm()).isEqualTo(AGORA);
    }

    @Test
    void naoLembraQuandoOAtendimentoEntrouEmProblema() {
        atendimento.setStatus(StatusAtendimento.EM_DISPUTA);
        atendimento.setTermino(AGORA.minus(Duration.ofDays(4)));
        when(avaliacaoRepository.findIdsComLembreteDeAvaliacaoDevido(any(), any(), any(), any())).thenReturn(List.of(900L));
        atendimentoEncontrado();

        service.processarLembretes();

        verify(notificador, never()).lembreteDeAvaliacao(any(), any(), any());
        assertThat(atendimento.getLembreteAvaliacao3dEm()).isNull();
    }

    private static AvaliacaoRepository.Resumo resumo(Double media, long total) {
        return new AvaliacaoRepository.Resumo() {
            @Override
            public Double getMedia() {
                return media;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private static AvaliacaoRepository.ContagemDestaque contagem(DestaqueAvaliacao destaque, long total) {
        return new AvaliacaoRepository.ContagemDestaque() {
            @Override
            public DestaqueAvaliacao getDestaque() {
                return destaque;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

}
