package com.quickcare.backend.proposta;

import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuarioRepository;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPropostaServiceTest {

    @Mock private PropostaRepository propostaRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private AtendimentoService atendimentoService;
    @Mock private BloqueioUsuarioRepository bloqueioUsuarioRepository;
    @Mock private AcessoAdmin acessoAdmin;
    @Mock private Notificador notificador;

    @InjectMocks
    private AdminPropostaService service;

    private final Usuario admin = Usuario.builder().id(1L).nome("Admin").build();
    private Solicitacao solicitacao;

    @BeforeEach
    void setUp() {
        lenient().when(acessoAdmin.exigir(1L)).thenReturn(admin);
        solicitacao = Solicitacao.builder()
                .id(50L)
                .paciente(Paciente.builder().id(10L).usuario(Usuario.builder().id(3L).nome("Paciente").email("p@x").build()).build())
                .profissionais(new LinkedHashSet<>())
                .status(StatusSolicitacao.RECEBENDO_PROPOSTAS)
                .build();
    }

    @Test
    void recusaAPendenteEAvisaOsDois() {
        Proposta proposta = proposta(StatusProposta.PENDENTE);

        service.alterarStatus(1L, 100L, AcaoAdminProposta.RECUSAR, "Proposta com valor incompatível.");

        assertThat(proposta.getStatus()).isEqualTo(StatusProposta.RECUSADA);
        verify(atendimentoService).recalcularDepoisDoAdmin(solicitacao);
        verify(notificador).propostaAlteradaPeloAdmin(proposta, AcaoAdminProposta.RECUSAR, "Proposta com valor incompatível.");
        verify(acessoAdmin).registrar(eq(admin), any(), any(), eq(100L), anyString(), anyString());
    }

    @Test
    void reativaARecusadaQuandoAindaRecebePropostas() {
        Proposta proposta = proposta(StatusProposta.RECUSADA);

        service.alterarStatus(1L, 100L, AcaoAdminProposta.REATIVAR, "Recusada por engano.");

        assertThat(proposta.getStatus()).isEqualTo(StatusProposta.PENDENTE);
    }

    @Test
    void naoReativaSeOProfissionalJaTemOutraValendoOuEstaoBloqueados() {
        Proposta proposta = proposta(StatusProposta.CANCELADA);
        when(propostaRepository.existsBySolicitacaoIdAndProfissionalIdAndStatusIn(50L, 20L, StatusProposta.ATIVAS))
                .thenReturn(true);

        assertThatThrownBy(() -> service.alterarStatus(1L, 100L, AcaoAdminProposta.REATIVAR, "Teste"))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("outra proposta valendo");

        when(propostaRepository.existsBySolicitacaoIdAndProfissionalIdAndStatusIn(50L, 20L, StatusProposta.ATIVAS))
                .thenReturn(false);
        when(bloqueioUsuarioRepository.existsByPacienteIdAndProfissionalId(10L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> service.alterarStatus(1L, 100L, AcaoAdminProposta.REATIVAR, "Teste"))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("bloqueados");
        assertThat(proposta.getStatus()).isEqualTo(StatusProposta.CANCELADA);
    }

    @Test
    void naoCancelaAceitaSemAtendimentoEmCurso() {
        proposta(StatusProposta.ACEITA);
        when(atendimentoService.ativoDa(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.alterarStatus(1L, 100L, AcaoAdminProposta.CANCELAR, "Teste"))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
        verify(atendimentoService, never()).cancelarPeloAdmin(any());
    }

    private Proposta proposta(StatusProposta status) {
        Proposta proposta = Proposta.builder()
                .id(100L)
                .solicitacao(solicitacao)
                .profissional(Profissional.builder().id(20L).usuario(Usuario.builder().id(2L).nome("Prof").email("x@x").build()).build())
                .status(status)
                .valor(new BigDecimal("150"))
                .inicio(Instant.parse("2026-12-01T11:00:00Z"))
                .termino(Instant.parse("2026-12-01T19:00:00Z"))
                .build();
        when(propostaRepository.findById(100L)).thenReturn(Optional.of(proposta));
        lenient().when(atendimentoRepository.findByPropostaIdInOrderByIdAsc(List.of(100L))).thenReturn(List.of());
        return proposta;
    }

}
