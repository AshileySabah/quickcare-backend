package com.quickcare.backend.problema;

import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.atendimento.TipoEventoAtendimento;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.ArquivoInvalidoException;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.ocorrencia.MotivoOcorrencia;
import com.quickcare.backend.ocorrencia.OcorrenciaService;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.problema.dto.ContestacaoRequest;
import com.quickcare.backend.problema.dto.DecisaoDisputaRequest;
import com.quickcare.backend.problema.dto.RelatoRequest;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.Solicitacao;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
class ProblemaServiceTest {

    private static final Long USUARIO_PACIENTE_ID = 1L;
    private static final Long USUARIO_PROFISSIONAL_ID = 2L;
    private static final Instant AGORA = Instant.parse("2026-11-02T12:00:00Z");

    @Mock private ProblemaRepository problemaRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private AtendimentoService atendimentoService;
    @Mock private OcorrenciaService ocorrenciaService;
    @Mock private ArmazenamentoDocumentoService armazenamento;
    @Mock private Notificador notificador;
    @Mock private AcessoAdmin acessoAdmin;
    @Mock private PlatformTransactionManager transactionManager;

    private ProblemaService service;
    private Usuario usuarioPaciente;
    private Usuario usuarioProfissional;
    private Atendimento atendimento;
    private ProblemaAtendimento salvo;

    @BeforeEach
    void setUp() {
        service = new ProblemaService(problemaRepository, atendimentoRepository, atendimentoService, ocorrenciaService,
                armazenamento, notificador, acessoAdmin, Clock.fixed(AGORA, ZoneOffset.UTC), transactionManager);

        usuarioPaciente = Usuario.builder().id(USUARIO_PACIENTE_ID).nome("Paciente").build();
        usuarioProfissional = Usuario.builder().id(USUARIO_PROFISSIONAL_ID).nome("Carla").build();
        Solicitacao solicitacao = Solicitacao.builder().id(50L)
                .paciente(Paciente.builder().id(10L).usuario(usuarioPaciente).build())
                .status(StatusSolicitacao.EM_ANDAMENTO).build();
        Proposta proposta = Proposta.builder().id(100L).solicitacao(solicitacao)
                .profissional(Profissional.builder().id(20L).usuario(usuarioProfissional).build())
                .status(StatusProposta.ACEITA).build();
        atendimento = Atendimento.builder().id(900L).proposta(proposta).solicitacao(solicitacao)
                .status(StatusAtendimento.FINALIZADO)
                .inicio(AGORA.minus(Duration.ofHours(30))).termino(AGORA.minus(Duration.ofHours(24)))
                .aceitoEm(AGORA.minus(Duration.ofDays(10))).build();

        lenient().when(atendimentoRepository.findWithPartesById(900L)).thenReturn(Optional.of(atendimento));
        lenient().when(problemaRepository.save(any())).thenAnswer(invocation -> {
            salvo = invocation.getArgument(0);
            return salvo;
        });
        lenient().when(problemaRepository.findByAtendimentoId(900L)).thenAnswer(invocation -> Optional.ofNullable(salvo));
        lenient().when(armazenamento.enviarAnexoProblema(any())).thenReturn("anexos-problema/x.pdf");
    }

    @Test
    void pacienteRelataQueOProfissionalNaoCompareceuComAnexo() {
        service.relatar(USUARIO_PACIENTE_ID, 900L, relato(MotivoProblema.OUTRO_NAO_COMPARECEU), List.of(pdf()));

        assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.PROBLEMA_RELATADO);
        assertThat(salvo.getStatus()).isEqualTo(StatusProblema.AGUARDANDO_RESPOSTA);
        assertThat(salvo.getPrazoResposta()).isEqualTo(AGORA.plus(Duration.ofDays(7)));
        assertThat(salvo.ausente()).isEqualTo(LadoAtendimento.PROFISSIONAL);
        assertThat(salvo.anexosDo(LadoAtendimento.PACIENTE)).hasSize(1);
        verify(notificador).problemaRelatado(salvo);
        verify(atendimentoService).registrarEvento(atendimento, TipoEventoAtendimento.PROBLEMA_RELATADO,
                LadoAtendimento.PACIENTE, "OUTRO_NAO_COMPARECEU");
    }

    @Test
    void naoRelataAntesDoInicioNemDepoisDas72h() {
        atendimento.setStatus(StatusAtendimento.AGENDADO);
        assertThatThrownBy(() -> service.relatar(USUARIO_PACIENTE_ID, 900L, relato(MotivoProblema.COMBINADO), List.of()))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("ainda não começou");

        atendimento.setStatus(StatusAtendimento.FINALIZADO);
        atendimento.setTermino(AGORA.minus(Duration.ofHours(73)));
        atendimento.setInicio(AGORA.minus(Duration.ofHours(80)));
        assertThatThrownBy(() -> service.relatar(USUARIO_PACIENTE_ID, 900L, relato(MotivoProblema.COMBINADO), List.of()))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("prazo");
    }

    @Test
    void aceitaNoMaximo3Anexos() {
        assertThatThrownBy(() -> service.relatar(USUARIO_PACIENTE_ID, 900L, relato(MotivoProblema.OUTRO_NAO_COMPARECEU),
                List.of(pdf(), pdf(), pdf(), pdf())))
                .isInstanceOf(ArquivoInvalidoException.class);
        verify(problemaRepository, never()).save(any());
    }

    @Test
    void confirmadoFicaNaoRealizadoEQuemFaltouLevaOcorrencia() {
        service.relatar(USUARIO_PACIENTE_ID, 900L, relato(MotivoProblema.OUTRO_NAO_COMPARECEU), List.of());

        service.confirmar(USUARIO_PROFISSIONAL_ID, 900L);

        assertThat(salvo.getStatus()).isEqualTo(StatusProblema.CONFIRMADO);
        verify(atendimentoService).encerrarComoNaoRealizado(atendimento, LadoAtendimento.PROFISSIONAL, AGORA);
        verify(ocorrenciaService).registrar(usuarioProfissional, TipoPerfil.PROFISSIONAL,
                MotivoOcorrencia.NAO_COMPARECIMENTO, atendimento, salvo);
        verify(notificador).problemaConfirmado(salvo);
    }

    @Test
    void quemRelatouNaoConfirmaOProprioRelato() {
        service.relatar(USUARIO_PACIENTE_ID, 900L, relato(MotivoProblema.OUTRO_NAO_COMPARECEU), List.of());

        assertThatThrownBy(() -> service.confirmar(USUARIO_PACIENTE_ID, 900L))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    @Test
    void combinadoNaoPenalizaNinguem() {
        service.relatar(USUARIO_PROFISSIONAL_ID, 900L, relato(MotivoProblema.COMBINADO), List.of());

        service.confirmar(USUARIO_PACIENTE_ID, 900L);

        verify(atendimentoService).encerrarComoNaoRealizado(atendimento, null, AGORA);
        verify(ocorrenciaService, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void semRespostaEm7DiasValeORelatoEAindaDaParaContestar() {
        service.relatar(USUARIO_PACIENTE_ID, 900L, relato(MotivoProblema.OUTRO_NAO_COMPARECEU), List.of());
        salvo = ProblemaAtendimento.builder().id(1L).atendimento(atendimento).relatadoPor(LadoAtendimento.PACIENTE)
                .motivo(MotivoProblema.OUTRO_NAO_COMPARECEU).descricao("Ninguém apareceu no horário.")
                .status(StatusProblema.AGUARDANDO_RESPOSTA).prazoResposta(AGORA.minusSeconds(1))
                .statusAnterior(StatusAtendimento.FINALIZADO).build();
        when(problemaRepository.findIdsComPrazoVencido(AGORA)).thenReturn(List.of(1L));
        when(problemaRepository.findWithPartesById(1L)).thenReturn(Optional.of(salvo));

        service.processarVencidos();

        assertThat(salvo.getStatus()).isEqualTo(StatusProblema.SEM_RESPOSTA);
        verify(ocorrenciaService).registrar(usuarioProfissional, TipoPerfil.PROFISSIONAL,
                MotivoOcorrencia.NAO_COMPARECIMENTO, atendimento, salvo);
        verify(notificador).problemaSemResposta(salvo);

        atendimento.setStatus(StatusAtendimento.NAO_REALIZADO);
        service.contestar(USUARIO_PROFISSIONAL_ID, 900L, new ContestacaoRequest("Eu fui e a família não abriu a porta."),
                List.of(pdf()));

        assertThat(salvo.getStatus()).isEqualTo(StatusProblema.EM_DISPUTA);
        assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.EM_DISPUTA);
        assertThat(salvo.anexosDo(LadoAtendimento.PROFISSIONAL)).hasSize(1);
        verify(notificador).problemaContestado(salvo);
    }

    @Test
    void adminDecideQueAconteceuTrocandoAsOcorrencias() {
        service.relatar(USUARIO_PACIENTE_ID, 900L, relato(MotivoProblema.OUTRO_NAO_COMPARECEU), List.of());
        service.contestar(USUARIO_PROFISSIONAL_ID, 900L, new ContestacaoRequest("Atendi normalmente, tenho as fotos."), List.of());
        Usuario admin = Usuario.builder().id(99L).nome("Admin").build();
        when(acessoAdmin.exigir(99L)).thenReturn(admin);
        when(problemaRepository.findWithPartesById(salvo.getId())).thenReturn(Optional.of(salvo));

        service.decidir(99L, salvo.getId(), new DecisaoDisputaRequest(DecisaoProblema.REALIZADO,
                Set.of(LadoAtendimento.PACIENTE), "As fotos mostram o atendimento no endereço."));

        assertThat(salvo.getStatus()).isEqualTo(StatusProblema.DECIDIDO);
        assertThat(salvo.getDecididoPor()).isEqualTo(admin);
        verify(ocorrenciaService).anularDoProblema(eq(admin), eq(salvo), anyString());
        verify(atendimentoService).retomarComoRealizado(atendimento, AGORA);
        verify(ocorrenciaService).registrar(usuarioPaciente, TipoPerfil.PACIENTE, MotivoOcorrencia.DECISAO_DISPUTA,
                atendimento, salvo);
        verify(notificador).disputaDecidida(salvo);
        ArgumentCaptor<String> justificativa = ArgumentCaptor.forClass(String.class);
        verify(acessoAdmin).registrar(eq(admin), any(), any(), any(), anyString(), justificativa.capture());
        assertThat(justificativa.getValue()).contains("fotos");
    }

    @Test
    void soDisputaEmAbertoPodeSerDecidida() {
        service.relatar(USUARIO_PACIENTE_ID, 900L, relato(MotivoProblema.OUTRO_NAO_COMPARECEU), List.of());
        when(acessoAdmin.exigir(99L)).thenReturn(Usuario.builder().id(99L).build());
        when(problemaRepository.findWithPartesById(salvo.getId())).thenReturn(Optional.of(salvo));

        assertThatThrownBy(() -> service.decidir(99L, salvo.getId(),
                new DecisaoDisputaRequest(DecisaoProblema.NAO_REALIZADO, Set.of(), "Sem contestação ainda.")))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    private static RelatoRequest relato(MotivoProblema motivo) {
        return new RelatoRequest(motivo, "O profissional não apareceu no horário combinado.");
    }

    private static MultipartFile pdf() {
        return new MockMultipartFile("anexos", "print.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

}
