package com.quickcare.backend.atuacao;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.atuacao.dto.AlterarEspecialidadesRequest;
import com.quickcare.backend.atuacao.dto.AtuacaoRequest;
import com.quickcare.backend.atuacao.dto.ImpactoAtuacaoResponse;
import com.quickcare.backend.atuacao.dto.PropostaAfetadaResponse;
import com.quickcare.backend.cadastro.exception.EspecialidadeInvalidaException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.DocumentoObrigatorioException;
import com.quickcare.backend.documento.DocumentoRepository;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.especialidade.EspecialidadeRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.PropostaService;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.ValidacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtuacaoServiceTest {

    private static final Long USUARIO_ID = 2L;
    private static final Instant AGORA = Instant.parse("2026-11-05T12:00:00Z");
    private static final MockMultipartFile CRM =
            new MockMultipartFile("documento", "crm.pdf", "application/pdf", new byte[]{1, 2, 3});

    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private AtuacaoRepository atuacaoRepository;
    @Mock private AtuacaoEventoRepository eventoRepository;
    @Mock private EspecialidadeRepository especialidadeRepository;
    @Mock private PropostaRepository propostaRepository;
    @Mock private PropostaService propostaService;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private ValidacaoService validacaoService;
    @Mock private DocumentoRepository documentoRepository;
    @Mock private ArmazenamentoDocumentoService armazenamentoDocumentoService;

    private AtuacaoService service;
    private Profissional profissional;
    private Atuacao enfermeiro;
    private Especialidade curativos;
    private Especialidade sondagem;
    private Especialidade clinica;
    private Solicitacao solicitacao;

    @BeforeEach
    void setUp() {
        service = new AtuacaoService(profissionalRepository, atuacaoRepository, eventoRepository,
                new MontagemAtuacoes(especialidadeRepository), propostaRepository, propostaService, atendimentoRepository,
                validacaoService, documentoRepository, armazenamentoDocumentoService, Clock.fixed(AGORA, ZoneOffset.UTC));

        curativos = especialidade(5L, "Curativos", CategoriaProfissional.ENFERMEIRO);
        sondagem = especialidade(6L, "Sondagem", CategoriaProfissional.ENFERMEIRO);
        clinica = especialidade(9L, "Clínica Médica", CategoriaProfissional.MEDICO);

        profissional = Profissional.builder().id(20L).usuario(Usuario.builder().id(USUARIO_ID).build()).build();
        enfermeiro = Atuacao.builder()
                .id(200L)
                .profissional(profissional)
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .especialidades(new LinkedHashSet<>(List.of(curativos, sondagem)))
                .registroProfissional("123456")
                .registroProfissionalUf("SP")
                .build();
        profissional.getAtuacoes().add(enfermeiro);

        solicitacao = Solicitacao.builder().id(50L).status(StatusSolicitacao.RECEBENDO_PROPOSTAS).build();
        solicitacao.getProfissionais().add(SolicitacaoProfissional.builder()
                .id(1L)
                .solicitacao(solicitacao)
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .especialidades(new LinkedHashSet<>(List.of(sondagem)))
                .quantidade(2)
                .build());

        when(profissionalRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(profissional));
        lenient().when(atuacaoRepository.findByIdAndProfissionalId(200L, 20L)).thenReturn(Optional.of(enfermeiro));
        lenient().when(validacaoService.statusDosRegistros(profissional)).thenReturn(Map.of());
    }

    @Test
    void incluirAtuacaoComRegistroExigeODocumento() {
        when(especialidadeRepository.findAllByIdIn(List.of(9L))).thenReturn(List.of(clinica));

        assertThatThrownBy(() -> service.incluir(USUARIO_ID,
                new AtuacaoRequest(CategoriaProfissional.MEDICO, List.of(9L), "98765", "SP"), null))
                .isInstanceOf(DocumentoObrigatorioException.class);
        verify(atuacaoRepository, never()).save(any());
    }

    @Test
    void incluirAtuacaoNovaAbreAValidacaoDoRegistro() {
        when(especialidadeRepository.findAllByIdIn(List.of(9L))).thenReturn(List.of(clinica));
        when(atuacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(armazenamentoDocumentoService.enviar(any(), any())).thenReturn("registro/crm.pdf");
        when(documentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.incluir(USUARIO_ID, new AtuacaoRequest(CategoriaProfissional.MEDICO, List.of(9L), "98765", "sp"), CRM);

        assertThat(profissional.atuacoesAtivas()).hasSize(2);
        Atuacao medico = profissional.atuacoesAtivas().get(1);
        assertThat(medico.getRegistroProfissionalUf()).isEqualTo("SP");
        verify(validacaoService).iniciarAtuacao(eq(medico), any());
        verify(eventoRepository).save(any());
    }

    @Test
    void naoIncluiCategoriaQueJaTem() {
        when(especialidadeRepository.findAllByIdIn(List.of(5L))).thenReturn(List.of(curativos));

        assertThatThrownBy(() -> service.incluir(USUARIO_ID,
                new AtuacaoRequest(CategoriaProfissional.ENFERMEIRO, List.of(5L), "1", "SP"), CRM))
                .isInstanceOf(EspecialidadeInvalidaException.class);
    }

    @Test
    void naoRemoveAUnicaAtuacao() {
        assertThatThrownBy(() -> service.remover(USUARIO_ID, 200L))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("pelo menos uma atuação");
        verify(propostaService, never()).encerrarPeloProfissional(any());
    }

    @Test
    void removerEncerraAsPropostasAbertasDaAtuacaoEGuardaNoHistorico() {
        incluirCuidador();
        Proposta pendente = proposta(100L, StatusProposta.PENDENTE, AGORA.plus(Duration.ofDays(3)));
        Proposta aceita = proposta(101L, StatusProposta.ACEITA, AGORA.plus(Duration.ofDays(10)));
        Atendimento agendado = atendimento(aceita, StatusAtendimento.AGENDADO, AGORA.minus(Duration.ofDays(5)));
        mockPropostas(List.of(pendente, aceita), List.of(agendado));

        service.remover(USUARIO_ID, 200L);

        verify(propostaService).encerrarPeloProfissional(pendente);
        verify(propostaService).encerrarPeloProfissional(aceita);
        assertThat(enfermeiro.getRemovidoEm()).isEqualTo(AGORA);
        assertThat(profissional.atuacoesAtivas()).doesNotContain(enfermeiro);
        verify(validacaoService).recalcular(profissional);
        verify(eventoRepository).save(any());
    }

    @Test
    void naoRemoveComAtendimentoEmAndamento() {
        incluirCuidador();
        Proposta aceita = proposta(101L, StatusProposta.ACEITA, AGORA.minus(Duration.ofHours(1)));
        mockPropostas(List.of(aceita), List.of(atendimento(aceita, StatusAtendimento.EM_ANDAMENTO, AGORA.minus(Duration.ofDays(5)))));

        assertThatThrownBy(() -> service.remover(USUARIO_ID, 200L))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("em andamento");
        assertThat(enfermeiro.ativa()).isTrue();
        verify(propostaService, never()).encerrarPeloProfissional(any());
    }

    @Test
    void previaDaRemocaoMostraQuaisPropostasGeramOcorrencia() {
        incluirCuidador();
        Proposta pendente = proposta(100L, StatusProposta.PENDENTE, AGORA.plus(Duration.ofDays(3)));
        Proposta antiga = proposta(101L, StatusProposta.ACEITA, AGORA.plus(Duration.ofDays(10)));
        Proposta recente = proposta(102L, StatusProposta.ACEITA, AGORA.plus(Duration.ofDays(11)));
        mockPropostas(List.of(pendente, antiga, recente), List.of(
                atendimento(antiga, StatusAtendimento.AGENDADO, AGORA.minus(Duration.ofDays(5))),
                atendimento(recente, StatusAtendimento.AGENDADO, AGORA.minus(Duration.ofHours(2)))));

        ImpactoAtuacaoResponse impacto = service.impacto(USUARIO_ID, 200L, null);

        assertThat(impacto.permitido()).isTrue();
        assertThat(impacto.propostas()).extracting(PropostaAfetadaResponse::propostaId).containsExactly(100L, 101L, 102L);
        assertThat(impacto.propostas()).extracting(PropostaAfetadaResponse::geraOcorrencia).containsExactly(false, true, false);
    }

    @Test
    void tirarAEspecialidadeQueAVagaPedeEncerraSoAsPropostasDela() {
        Proposta daSondagem = proposta(100L, StatusProposta.PENDENTE, AGORA.plus(Duration.ofDays(3)));
        mockPropostas(List.of(daSondagem), List.of());
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(curativos));

        service.alterarEspecialidades(USUARIO_ID, 200L, new AlterarEspecialidadesRequest(List.of(5L)));

        assertThat(enfermeiro.getEspecialidades()).containsExactly(curativos);
        verify(propostaService).encerrarPeloProfissional(daSondagem);
    }

    @Test
    void incluirEspecialidadeNaoMexeNasPropostas() {
        Especialidade ostomia = especialidade(7L, "Cuidados com Ostomia", CategoriaProfissional.ENFERMEIRO);
        mockPropostas(List.of(proposta(100L, StatusProposta.PENDENTE, AGORA.plus(Duration.ofDays(3)))), List.of());
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(curativos, sondagem, ostomia));

        service.alterarEspecialidades(USUARIO_ID, 200L, new AlterarEspecialidadesRequest(List.of(5L, 6L, 7L)));

        assertThat(enfermeiro.getEspecialidades()).hasSize(3);
        verify(propostaService, never()).encerrarPeloProfissional(any());
    }

    @Test
    void naoTrocaAEspecialidadeDeOutrosProfissionais() {
        Especialidade psicologia = especialidade(18L, "Psicologia", CategoriaProfissional.OUTRO);
        Atuacao outro = Atuacao.builder().id(201L).profissional(profissional).categoria(CategoriaProfissional.OUTRO)
                .especialidades(new LinkedHashSet<>(Set.of(psicologia))).build();
        profissional.getAtuacoes().add(outro);
        when(atuacaoRepository.findByIdAndProfissionalId(201L, 20L)).thenReturn(Optional.of(outro));

        assertThatThrownBy(() -> service.alterarEspecialidades(USUARIO_ID, 201L, new AlterarEspecialidadesRequest(List.of(21L))))
                .isInstanceOf(EspecialidadeInvalidaException.class)
                .hasMessageContaining("a especialidade define a atuação");
    }

    private void incluirCuidador() {
        profissional.getAtuacoes().add(Atuacao.builder().id(201L).profissional(profissional)
                .categoria(CategoriaProfissional.CUIDADOR).build());
    }

    private void mockPropostas(List<Proposta> propostas, List<Atendimento> atendimentos) {
        when(propostaRepository.findByAtuacaoIdAndStatusIn(200L, StatusProposta.ATIVAS)).thenReturn(propostas);
        lenient().when(atendimentoRepository.findByPropostaIdInOrderByIdAsc(anyList())).thenReturn(atendimentos);
    }

    private Proposta proposta(Long id, StatusProposta status, Instant inicio) {
        Proposta proposta = Proposta.builder()
                .id(id)
                .solicitacao(solicitacao)
                .profissional(profissional)
                .atuacao(enfermeiro)
                .status(status)
                .inicio(inicio)
                .termino(inicio.plus(Duration.ofHours(8)))
                .build();
        if (status == StatusProposta.ACEITA) {
            proposta.setVaga(solicitacao.getProfissionais().iterator().next());
        }
        return proposta;
    }

    private static Atendimento atendimento(Proposta proposta, StatusAtendimento status, Instant aceitoEm) {
        Atendimento atendimento = Atendimento.agendar(proposta, aceitoEm);
        atendimento.setStatus(status);
        return atendimento;
    }

    private static Especialidade especialidade(Long id, String nome, CategoriaProfissional categoria) {
        return Especialidade.builder().id(id).nome(nome).categoria(categoria).build();
    }

}
