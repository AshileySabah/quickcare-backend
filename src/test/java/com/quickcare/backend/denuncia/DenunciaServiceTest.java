package com.quickcare.backend.denuncia;

import com.quickcare.backend.administrador.AcaoAdmin;
import com.quickcare.backend.administrador.AcaoAdminRepository;
import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.administrador.TipoAcaoAdmin;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.denuncia.dto.DecisaoDenunciaRequest;
import com.quickcare.backend.denuncia.dto.DenunciaDetalheResponse;
import com.quickcare.backend.denuncia.dto.DenunciaRequest;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.ocorrencia.Ocorrencia;
import com.quickcare.backend.ocorrencia.OcorrenciaService;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class DenunciaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");
    private static final Long USUARIO_PACIENTE_ID = 1L;
    private static final Long ADMIN_ID = 99L;

    @Mock private DenunciaRepository denunciaRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private ArmazenamentoDocumentoService armazenamento;
    @Mock private OcorrenciaService ocorrenciaService;
    @Mock private AcaoAdminRepository acaoAdminRepository;
    @Mock private AcessoAdmin acessoAdmin;
    @Mock private Notificador notificador;

    private DenunciaService service;
    private Paciente paciente;
    private Profissional profissional;
    private final Usuario admin = Usuario.builder().id(ADMIN_ID).nome("Admin").build();

    @BeforeEach
    void setUp() {
        service = new DenunciaService(denunciaRepository, pacienteRepository, profissionalRepository, atendimentoRepository,
                armazenamento, ocorrenciaService, acaoAdminRepository, acessoAdmin, notificador,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        paciente = Paciente.builder().id(10L)
                .usuario(Usuario.builder().id(USUARIO_PACIENTE_ID).nome("Helena").email("helena@quickcare.dev").build()).build();
        profissional = Profissional.builder().id(20L)
                .usuario(Usuario.builder().id(2L).nome("Bruno").email("bruno@quickcare.dev").build()).build();
    }

    private DenunciaRequest pedido() {
        return new DenunciaRequest(TipoPerfil.PACIENTE, 20L, MotivoDenuncia.DESRESPEITO_OU_ASSEDIO,
                "  Foi grosseiro durante todo o plantão.  ");
    }

    private void partesDoPaciente() {
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(paciente));
        when(profissionalRepository.findById(20L)).thenReturn(Optional.of(profissional));
    }

    private Denuncia aberta() {
        return Denuncia.builder()
                .id(500L)
                .paciente(paciente)
                .profissional(profissional)
                .denunciante(LadoAtendimento.PACIENTE)
                .motivo(MotivoDenuncia.NEGLIGENCIA)
                .descricao("Não fez o curativo combinado.")
                .status(StatusDenuncia.ABERTA)
                .criadoEm(AGORA.minusSeconds(3600))
                .build();
    }

    private Denuncia denunciaParaJulgar() {
        Denuncia denuncia = aberta();
        when(acessoAdmin.exigir(ADMIN_ID)).thenReturn(admin);
        when(denunciaRepository.findWithPartesById(500L)).thenReturn(Optional.of(denuncia));
        return denuncia;
    }

    @Test
    void pacienteDenunciaQuemJaAtendeuComAnexo() {
        partesDoPaciente();
        when(atendimentoRepository.existsByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(eq(20L), eq(10L), any()))
                .thenReturn(true);
        when(armazenamento.enviarAnexoDenuncia(any())).thenReturn("anexos-denuncia/print.png");
        when(denunciaRepository.save(any(Denuncia.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        MockMultipartFile anexo = new MockMultipartFile("anexo", "print.png", "image/png", new byte[]{1, 2, 3});

        service.denunciar(USUARIO_PACIENTE_ID, pedido(), anexo);

        ArgumentCaptor<Denuncia> denuncia = ArgumentCaptor.forClass(Denuncia.class);
        verify(denunciaRepository).save(denuncia.capture());
        assertThat(denuncia.getValue().getStatus()).isEqualTo(StatusDenuncia.ABERTA);
        assertThat(denuncia.getValue().getDenunciante()).isEqualTo(LadoAtendimento.PACIENTE);
        assertThat(denuncia.getValue().usuarioDenunciado()).isSameAs(profissional.getUsuario());
        assertThat(denuncia.getValue().getDescricao()).isEqualTo("Foi grosseiro durante todo o plantão.");
        assertThat(denuncia.getValue().getAnexoStorageKey()).isEqualTo("anexos-denuncia/print.png");
        assertThat(denuncia.getValue().getAnexoNome()).isEqualTo("print.png");
        verify(notificador).denunciaRecebida(denuncia.getValue());
    }

    @Test
    void semAtendimentoEntreOsDoisNaoDaParaDenunciar() {
        partesDoPaciente();
        when(atendimentoRepository.existsByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(eq(20L), eq(10L), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.denunciar(USUARIO_PACIENTE_ID, pedido(), null))
                .isInstanceOf(PerfilNaoAutorizadoException.class)
                .hasMessage("Só dá para denunciar quem já teve um atendimento com você.");
        verify(denunciaRepository, never()).save(any());
    }

    @Test
    void naoAbreOutraDenunciaEnquantoAAnteriorEstaEmAnalise() {
        partesDoPaciente();
        when(atendimentoRepository.existsByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(eq(20L), eq(10L), any()))
                .thenReturn(true);
        when(denunciaRepository.existsByPacienteIdAndProfissionalIdAndDenuncianteAndStatusIn(
                10L, 20L, LadoAtendimento.PACIENTE, StatusDenuncia.PENDENTES)).thenReturn(true);

        assertThatThrownBy(() -> service.denunciar(USUARIO_PACIENTE_ID, pedido(), null))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("já tem uma denúncia em análise");
        verify(armazenamento, never()).enviarAnexoDenuncia(any());
    }

    @Test
    void iniciarAnaliseRegistraQuemAnalisa() {
        Denuncia denuncia = denunciaParaJulgar();

        service.iniciarAnalise(ADMIN_ID, 500L);

        assertThat(denuncia.getStatus()).isEqualTo(StatusDenuncia.EM_ANALISE);
        assertThat(denuncia.getAnalisadaPor()).isSameAs(admin);
        assertThat(denuncia.getAnaliseIniciadaEm()).isEqualTo(AGORA);
        verify(acessoAdmin).registrar(eq(admin), eq(TipoAcaoAdmin.DENUNCIA_EM_ANALISE), eq(AcaoAdmin.Alvo.DENUNCIA),
                eq(500L), any(), anyString());
    }

    @Test
    void procedenteComOcorrenciaLancaAOcorrenciaParaODenunciadoEAvisaOsDois() {
        Denuncia denuncia = denunciaParaJulgar();
        Ocorrencia ocorrencia = Ocorrencia.builder().id(700L).build();
        when(ocorrenciaService.lancarPorDenuncia(admin, profissional.getUsuario(), TipoPerfil.PROFISSIONAL,
                "Relato confirmado pela família.")).thenReturn(ocorrencia);

        DenunciaDetalheResponse detalhe = service.julgar(ADMIN_ID, 500L, new DecisaoDenunciaRequest(
                StatusDenuncia.PROCEDENTE, ConsequenciaDenuncia.OCORRENCIA, "Relato confirmado pela família."));

        assertThat(denuncia.getStatus()).isEqualTo(StatusDenuncia.PROCEDENTE);
        assertThat(denuncia.getConsequencia()).isEqualTo(ConsequenciaDenuncia.OCORRENCIA);
        assertThat(denuncia.getOcorrencia()).isSameAs(ocorrencia);
        assertThat(denuncia.getDecididaPor()).isSameAs(admin);
        assertThat(detalhe.ocorrencia().id()).isEqualTo(700L);
        verify(notificador).denunciaJulgada(denuncia);
        verify(notificador).denunciaProcedente(denuncia);
        verify(acessoAdmin).registrar(eq(admin), eq(TipoAcaoAdmin.DENUNCIA_PROCEDENTE), eq(AcaoAdmin.Alvo.DENUNCIA),
                eq(500L), eq("Consequência: OCORRENCIA"), eq("Relato confirmado pela família."));
    }

    @Test
    void improcedenteSoAvisaQuemDenunciou() {
        Denuncia denuncia = denunciaParaJulgar();

        service.julgar(ADMIN_ID, 500L, new DecisaoDenunciaRequest(
                StatusDenuncia.IMPROCEDENTE, ConsequenciaDenuncia.OCORRENCIA, "Sem indícios do que foi relatado."));

        assertThat(denuncia.getStatus()).isEqualTo(StatusDenuncia.IMPROCEDENTE);
        assertThat(denuncia.getConsequencia()).isNull();
        verify(ocorrenciaService, never()).lancarPorDenuncia(any(), any(), any(), any());
        verify(notificador).denunciaJulgada(denuncia);
        verify(notificador, never()).denunciaProcedente(any());
    }

    @Test
    void procedenteExigeAConsequencia() {
        denunciaParaJulgar();

        assertThatThrownBy(() -> service.julgar(ADMIN_ID, 500L,
                new DecisaoDenunciaRequest(StatusDenuncia.PROCEDENTE, null, "Confirmado.")))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Escolha a consequência para o denunciado.");
    }

    @Test
    void banimentoAindaNaoEstaDisponivel() {
        Denuncia denuncia = denunciaParaJulgar();

        assertThatThrownBy(() -> service.julgar(ADMIN_ID, 500L,
                new DecisaoDenunciaRequest(StatusDenuncia.PROCEDENTE, ConsequenciaDenuncia.BANIMENTO, "Grave.")))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("banimento ainda não está disponível");
        assertThat(denuncia.getStatus()).isEqualTo(StatusDenuncia.ABERTA);
    }

    @Test
    void naoJulgaDuasVezes() {
        Denuncia denuncia = denunciaParaJulgar();
        denuncia.julgar(StatusDenuncia.IMPROCEDENTE, null, "Sem indícios.", admin, AGORA);

        assertThatThrownBy(() -> service.julgar(ADMIN_ID, 500L,
                new DecisaoDenunciaRequest(StatusDenuncia.PROCEDENTE, ConsequenciaDenuncia.OCORRENCIA, "Mudei de ideia.")))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Esta denúncia já foi julgada.");
    }

}
