package com.quickcare.backend.banimento;

import com.quickcare.backend.administrador.AcaoAdmin;
import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.administrador.TipoAcaoAdmin;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoRepository;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
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
import java.util.List;
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
class BanimentoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");

    @Mock private BanimentoRepository banimentoRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private SolicitacaoRepository solicitacaoRepository;
    @Mock private PropostaRepository propostaRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private AtendimentoService atendimentoService;
    @Mock private AcessoAdmin acessoAdmin;
    @Mock private Notificador notificador;
    @Mock private PlatformTransactionManager transactionManager;

    private BanimentoService service;
    private final Usuario admin = Usuario.builder().id(99L).nome("Admin").build();
    private final Usuario usuario = Usuario.builder().id(1L).nome("Helena").build();

    @BeforeEach
    void setUp() {
        service = new BanimentoService(banimentoRepository, pacienteRepository, profissionalRepository,
                solicitacaoRepository, propostaRepository, atendimentoRepository, atendimentoService, acessoAdmin,
                notificador, Clock.fixed(AGORA, ZoneOffset.UTC), transactionManager);
    }

    private void salvaBanimento() {
        when(banimentoRepository.save(any(Banimento.class))).thenAnswer(invocacao -> {
            Banimento banimento = invocacao.getArgument(0);
            return Banimento.builder()
                    .id(300L)
                    .usuario(banimento.getUsuario())
                    .perfil(banimento.getPerfil())
                    .tipo(banimento.getTipo())
                    .inicio(banimento.getInicio())
                    .fim(banimento.getFim())
                    .justificativa(banimento.getJustificativa())
                    .aplicadoPor(banimento.getAplicadoPor())
                    .criadoEm(banimento.getCriadoEm())
                    .build();
        });
    }

    private Banimento ativo(TipoPerfil perfil) {
        return Banimento.builder()
                .id(300L)
                .usuario(usuario)
                .perfil(perfil)
                .tipo(TipoBanimento.SUSPENSAO)
                .inicio(AGORA.minus(Duration.ofDays(2)))
                .fim(AGORA.plus(Duration.ofDays(5)))
                .justificativa("Golpe confirmado.")
                .aplicadoPor(admin)
                .build();
    }

    @Test
    void banirOPacienteEncerraAsSolicitacoesEmCursoESoConcluiAsQueJaTiveramAtendimento() {
        Paciente paciente = Paciente.builder().id(10L).usuario(usuario).build();
        Solicitacao recebendo = Solicitacao.builder().id(50L).status(StatusSolicitacao.RECEBENDO_PROPOSTAS).build();
        Solicitacao emAndamento = Solicitacao.builder().id(51L).status(StatusSolicitacao.EM_ANDAMENTO).build();
        Solicitacao concluida = Solicitacao.builder().id(52L).status(StatusSolicitacao.CONCLUIDA).build();
        salvaBanimento();
        when(pacienteRepository.findByUsuarioId(1L)).thenReturn(Optional.of(paciente));
        when(solicitacaoRepository.findByPacienteIdOrderByCriadoEmDesc(10L)).thenReturn(List.of(recebendo, emAndamento, concluida));
        when(atendimentoRepository.existsBySolicitacaoIdAndStatusIn(eq(50L), any())).thenReturn(false);
        when(atendimentoRepository.existsBySolicitacaoIdAndStatusIn(eq(51L), any())).thenReturn(true);
        Proposta afetada = Proposta.builder().id(100L).build();
        when(atendimentoService.encerrarPeloAdmin(recebendo, StatusSolicitacao.CANCELADA)).thenReturn(List.of(afetada));
        when(atendimentoService.encerrarPeloAdmin(emAndamento, StatusSolicitacao.CONCLUIDA)).thenReturn(List.of());

        Banimento banimento = service.banir(admin, usuario, TipoPerfil.PACIENTE, TipoBanimento.PERMANENTE, null,
                " Golpe confirmado. ", null);

        assertThat(paciente.isBanido()).isTrue();
        assertThat(banimento.getFim()).isNull();
        verify(atendimentoService, never()).encerrarPeloAdmin(eq(concluida), any());
        verify(notificador).encerradoPorBanimentoDoPaciente(List.of(afetada));
        verify(notificador).banimentoAplicado(banimento);
        verify(acessoAdmin).registrar(eq(admin), eq(TipoAcaoAdmin.BANIMENTO_APLICADO), eq(AcaoAdmin.Alvo.BANIMENTO),
                eq(300L), anyString(), eq(" Golpe confirmado. "));
    }

    @Test
    void banirOProfissionalCancelaPendentesEAtendimentosEmCursoMasNaoMexeNoQueJaTerminou() {
        Profissional profissional = Profissional.builder().id(20L).usuario(usuario).build();
        Solicitacao solicitacao = Solicitacao.builder().id(50L).build();
        Proposta pendente = Proposta.builder().id(100L).solicitacao(solicitacao).status(StatusProposta.PENDENTE).build();
        Proposta emCurso = Proposta.builder().id(101L).solicitacao(solicitacao).status(StatusProposta.ACEITA).build();
        Proposta terminada = Proposta.builder().id(102L).solicitacao(solicitacao).status(StatusProposta.ACEITA).build();
        salvaBanimento();
        when(profissionalRepository.findByUsuarioId(1L)).thenReturn(Optional.of(profissional));
        when(propostaRepository.findByProfissionalIdOrderByCriadoEmDesc(20L)).thenReturn(List.of(pendente, emCurso, terminada));
        when(atendimentoService.ativoDa(emCurso)).thenReturn(Optional.of(Atendimento.builder().id(900L).build()));
        when(atendimentoService.ativoDa(terminada)).thenReturn(Optional.empty());

        service.banir(admin, usuario, TipoPerfil.PROFISSIONAL, TipoBanimento.SUSPENSAO, 15, "Faltou várias vezes.", null);

        assertThat(profissional.isBanido()).isTrue();
        assertThat(pendente.getStatus()).isEqualTo(StatusProposta.CANCELADA);
        verify(notificador).encerradoPorBanimentoDoProfissional(pendente, false);
        verify(atendimentoService).cancelarPeloAdmin(emCurso);
        verify(notificador).encerradoPorBanimentoDoProfissional(emCurso, true);
        verify(atendimentoService, never()).cancelarPeloAdmin(terminada);

        ArgumentCaptor<Banimento> salvo = ArgumentCaptor.forClass(Banimento.class);
        verify(banimentoRepository).save(salvo.capture());
        assertThat(salvo.getValue().getFim()).isEqualTo(AGORA.plus(Duration.ofDays(15)));
    }

    @Test
    void suspensaoPrecisaDosDias() {
        assertThatThrownBy(() -> service.banir(admin, usuario, TipoPerfil.PACIENTE, TipoBanimento.SUSPENSAO, null, "x", null))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Informe de 1 a 365 dias de suspensão.");
        verify(banimentoRepository, never()).save(any());
    }

    @Test
    void naoBaneDuasVezesOMesmoPerfil() {
        when(banimentoRepository.findAtivo(1L, TipoPerfil.PACIENTE, AGORA)).thenReturn(Optional.of(ativo(TipoPerfil.PACIENTE)));

        assertThatThrownBy(() -> service.banir(admin, usuario, TipoPerfil.PACIENTE, TipoBanimento.PERMANENTE, null, "x", null))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("O perfil de paciente já está banido.");
    }

    @Test
    void removerLiberaOPerfilEAvisa() {
        Banimento banimento = ativo(TipoPerfil.PROFISSIONAL);
        Profissional profissional = Profissional.builder().id(20L).usuario(usuario).banido(true).build();
        when(banimentoRepository.findById(300L)).thenReturn(Optional.of(banimento));
        when(profissionalRepository.findByUsuarioId(1L)).thenReturn(Optional.of(profissional));

        service.remover(admin, 300L, "Engano na análise.");

        assertThat(banimento.getEncerramento()).isEqualTo(Banimento.Encerramento.REMOVIDO);
        assertThat(banimento.ativo(AGORA)).isFalse();
        assertThat(profissional.isBanido()).isFalse();
        verify(notificador).banimentoEncerrado(banimento);
    }

    @Test
    void banidoRecorreUmaVezSo() {
        Banimento banimento = ativo(TipoPerfil.PACIENTE);
        when(banimentoRepository.findById(300L)).thenReturn(Optional.of(banimento));

        service.recorrer(1L, TipoPerfil.PACIENTE, 300L, "  Não fui eu, minha conta foi usada por outra pessoa.  ");

        assertThat(banimento.getRecursoStatus()).isEqualTo(Banimento.StatusRecurso.EM_ANALISE);
        assertThat(banimento.getRecursoTexto()).isEqualTo("Não fui eu, minha conta foi usada por outra pessoa.");
        assertThatThrownBy(() -> service.recorrer(1L, TipoPerfil.PACIENTE, 300L, "De novo."))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Você já recorreu deste banimento.");
    }

    @Test
    void recursoAceitoDesbane() {
        Banimento banimento = ativo(TipoPerfil.PACIENTE);
        banimento.recorrer("Não fui eu.", AGORA);
        Paciente paciente = Paciente.builder().id(10L).usuario(usuario).banido(true).build();
        when(banimentoRepository.findById(300L)).thenReturn(Optional.of(banimento));
        when(pacienteRepository.findByUsuarioId(1L)).thenReturn(Optional.of(paciente));

        service.decidirRecurso(admin, 300L, true, "Comprovado o acesso indevido.");

        assertThat(banimento.getRecursoStatus()).isEqualTo(Banimento.StatusRecurso.ACEITO);
        assertThat(banimento.getEncerramento()).isEqualTo(Banimento.Encerramento.RECURSO_ACEITO);
        assertThat(paciente.isBanido()).isFalse();
        verify(notificador).recursoDeBanimentoDecidido(banimento);
    }

    @Test
    void recursoNegadoMantemOBanimento() {
        Banimento banimento = ativo(TipoPerfil.PACIENTE);
        banimento.recorrer("Não fui eu.", AGORA);
        when(banimentoRepository.findById(300L)).thenReturn(Optional.of(banimento));

        service.decidirRecurso(admin, 300L, false, "As provas mostram o contrário.");

        assertThat(banimento.getRecursoStatus()).isEqualTo(Banimento.StatusRecurso.NEGADO);
        assertThat(banimento.ativo(AGORA)).isTrue();
        verify(pacienteRepository, never()).findByUsuarioId(any());
    }

    @Test
    void suspensaoVencidaTerminaSozinhaELiberaOPerfil() {
        Banimento banimento = ativo(TipoPerfil.PACIENTE);
        Banimento vencido = Banimento.builder()
                .id(300L).usuario(usuario).perfil(TipoPerfil.PACIENTE).tipo(TipoBanimento.SUSPENSAO)
                .inicio(AGORA.minus(Duration.ofDays(8))).fim(AGORA.minus(Duration.ofDays(1)))
                .justificativa(banimento.getJustificativa()).build();
        Paciente paciente = Paciente.builder().id(10L).usuario(usuario).banido(true).build();
        when(banimentoRepository.findIdsVencidos(AGORA)).thenReturn(List.of(300L));
        when(banimentoRepository.findById(300L)).thenReturn(Optional.of(vencido));
        when(pacienteRepository.findByUsuarioId(1L)).thenReturn(Optional.of(paciente));

        service.processarVencidos();

        assertThat(vencido.getEncerramento()).isEqualTo(Banimento.Encerramento.EXPIROU);
        assertThat(paciente.isBanido()).isFalse();
        verify(notificador).banimentoEncerrado(vencido);
    }

}
