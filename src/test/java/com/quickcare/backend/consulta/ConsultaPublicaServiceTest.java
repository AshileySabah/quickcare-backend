package com.quickcare.backend.consulta;

import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.avaliacao.AvaliacaoService;
import com.quickcare.backend.avaliacao.dto.ResumoAvaliacoesResponse;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuarioRepository;
import com.quickcare.backend.consulta.dto.ProfissionalPublicoResponse;
import com.quickcare.backend.consulta.dto.SolicitacaoPublicaResponse;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import com.quickcare.backend.solicitacao.SolicitacaoRepository;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.StatusValidacao;
import com.quickcare.backend.validacao.ValidacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaPublicaServiceTest {

    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private SolicitacaoRepository solicitacaoRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private BloqueioUsuarioRepository bloqueioUsuarioRepository;
    @Mock private ValidacaoService validacaoService;
    @Mock private AvaliacaoService avaliacaoService;
    @Mock private ArmazenamentoDocumentoService armazenamentoDocumentoService;

    private ConsultaPublicaService service;

    private final Especialidade curativos = Especialidade.builder().id(5L).nome("Curativos")
            .categoria(CategoriaProfissional.ENFERMEIRO).build();
    private final Especialidade idosos = Especialidade.builder().id(7L).nome("Cuidador de Idosos")
            .categoria(CategoriaProfissional.CUIDADOR).build();

    @BeforeEach
    void setUp() {
        service = new ConsultaPublicaService(profissionalRepository, pacienteRepository, solicitacaoRepository,
                atendimentoRepository, bloqueioUsuarioRepository, validacaoService, avaliacaoService,
                armazenamentoDocumentoService);
    }

    private Profissional profissional(Long id, String nome, ModalidadeAtendimento modalidade, String cidade, boolean banido,
                                      Especialidade especialidade, Double media) {
        Profissional profissional = Profissional.builder()
                .id(id)
                .usuario(Usuario.builder().id(id * 10).nome(nome).build())
                .statusValidacao(StatusValidacao.APROVADO)
                .modalidadeAtendimento(modalidade)
                .cidade(cidade)
                .uf("SP")
                .banido(banido)
                .build();
        Atuacao atuacao = Atuacao.builder()
                .id(id * 100)
                .profissional(profissional)
                .categoria(especialidade.getCategoria())
                .especialidades(new LinkedHashSet<>(Set.of(especialidade)))
                .build();
        lenient().when(validacaoService.atuacoesLiberadas(profissional)).thenReturn(List.of(atuacao));
        lenient().when(avaliacaoService.resumoDoProfissional(id))
                .thenReturn(new ResumoAvaliacoesResponse(media, media == null ? 0 : 3, List.of()));
        return profissional;
    }

    private ConsultaPublicaService.FiltroProfissionais semFiltro() {
        return new ConsultaPublicaService.FiltroProfissionais(null, null, null, null, null, null);
    }

    @Test
    void visitanteSoVeProfissionaisNaoBanidosEOsMelhorAvaliadosPrimeiro() {
        Profissional bruno = profissional(1L, "Bruno", ModalidadeAtendimento.AMBOS, "São Paulo", false, curativos, 4.0);
        Profissional camila = profissional(2L, "Camila", ModalidadeAtendimento.PRESENCIAL, "Campinas", false, curativos, 4.8);
        Profissional kleber = profissional(3L, "Kleber", ModalidadeAtendimento.AMBOS, "São Paulo", true, curativos, 5.0);
        when(profissionalRepository.findByStatusValidacaoOrderByCriadoEmAsc(StatusValidacao.APROVADO))
                .thenReturn(List.of(bruno, camila, kleber));

        List<ProfissionalPublicoResponse> resultado = service.profissionais(null, semFiltro());

        assertThat(resultado).extracting(ProfissionalPublicoResponse::nome).containsExactly("Camila", "Bruno");
    }

    @Test
    void filtraPorCategoriaCidadeSemAcentoModalidadeENotaMinima() {
        Profissional bruno = profissional(1L, "Bruno", ModalidadeAtendimento.AMBOS, "São Paulo", false, curativos, 4.6);
        Profissional camila = profissional(2L, "Camila", ModalidadeAtendimento.PRESENCIAL, "São Paulo", false, curativos, 4.9);
        Profissional diego = profissional(4L, "Diego", ModalidadeAtendimento.AMBOS, "São Paulo", false, idosos, 5.0);
        Profissional eduardo = profissional(5L, "Eduardo", ModalidadeAtendimento.AMBOS, "São Paulo", false, curativos, null);
        when(profissionalRepository.findByStatusValidacaoOrderByCriadoEmAsc(StatusValidacao.APROVADO))
                .thenReturn(List.of(bruno, camila, diego, eduardo));

        List<ProfissionalPublicoResponse> resultado = service.profissionais(null, new ConsultaPublicaService.FiltroProfissionais(
                CategoriaProfissional.ENFERMEIRO, null, "sao paulo", "sp", ModalidadeSolicitacao.ONLINE, 4.5));

        assertThat(resultado).extracting(ProfissionalPublicoResponse::nome).containsExactly("Bruno");
    }

    @Test
    void pacienteLogadoNaoVeQuemEstaBloqueadoComEle() {
        Profissional bruno = profissional(1L, "Bruno", ModalidadeAtendimento.AMBOS, "São Paulo", false, curativos, 4.0);
        Profissional camila = profissional(2L, "Camila", ModalidadeAtendimento.AMBOS, "São Paulo", false, curativos, 4.0);
        when(profissionalRepository.findByStatusValidacaoOrderByCriadoEmAsc(StatusValidacao.APROVADO))
                .thenReturn(List.of(bruno, camila));
        when(pacienteRepository.findByUsuarioId(99L)).thenReturn(Optional.of(Paciente.builder().id(50L).build()));
        when(bloqueioUsuarioRepository.findProfissionalIdsBloqueadosCom(50L)).thenReturn(Set.of(2L));

        List<ProfissionalPublicoResponse> resultado = service.profissionais(99L, semFiltro());

        assertThat(resultado).extracting(ProfissionalPublicoResponse::nome).containsExactly("Bruno");
    }

    private Solicitacao solicitacao(Long id, Long pacienteId, Long usuarioId, ModalidadeSolicitacao modalidade,
                                    Especialidade especialidade, int quantidade, int preenchidas) {
        Solicitacao solicitacao = Solicitacao.builder()
                .id(id)
                .paciente(Paciente.builder().id(pacienteId).usuario(Usuario.builder().id(usuarioId).build()).build())
                .descricao("Texto que não pode aparecer na consulta pública.")
                .modalidade(modalidade)
                .cidade("São Paulo")
                .uf("SP")
                .bairro("Bela Vista")
                .prazoDesejado(LocalDate.of(2026, 11, 20))
                .criadoEm(Instant.parse("2026-11-08T12:00:00Z"))
                .status(StatusSolicitacao.RECEBENDO_PROPOSTAS)
                .profissionais(new LinkedHashSet<>())
                .build();
        solicitacao.getProfissionais().add(SolicitacaoProfissional.builder()
                .id(id * 10)
                .solicitacao(solicitacao)
                .categoria(especialidade.getCategoria())
                .especialidades(new LinkedHashSet<>(Set.of(especialidade)))
                .quantidade(quantidade)
                .vagasPreenchidas(preenchidas)
                .build());
        return solicitacao;
    }

    @Test
    void solicitacoesAparecemSemDadosDoPacienteENemCidadeQuandoSaoOnline() {
        when(solicitacaoRepository.findDistinctByStatusInOrderByCriadoEmDesc(any())).thenReturn(List.of(
                solicitacao(1L, 10L, 100L, ModalidadeSolicitacao.PRESENCIAL, curativos, 2, 1),
                solicitacao(2L, 11L, 101L, ModalidadeSolicitacao.ONLINE, curativos, 1, 0)));

        List<SolicitacaoPublicaResponse> resultado = service.solicitacoes(null,
                new ConsultaPublicaService.FiltroSolicitacoes(null, null, null, null, null));

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).cidade()).isEqualTo("São Paulo");
        assertThat(resultado.get(0).profissionais()).singleElement().satisfies(vaga -> {
            assertThat(vaga.especialidades()).containsExactly("Curativos");
            assertThat(vaga.vagasAbertas()).isEqualTo(1);
        });
        assertThat(resultado.get(1).cidade()).isNull();
        assertThat(resultado.get(1).uf()).isNull();
    }

    @Test
    void solicitacoesSemVagaDaEspecialidadeOuDeBloqueadosOuPropriasNaoAparecem() {
        when(solicitacaoRepository.findDistinctByStatusInOrderByCriadoEmDesc(any())).thenReturn(List.of(
                solicitacao(1L, 10L, 100L, ModalidadeSolicitacao.PRESENCIAL, curativos, 1, 1),
                solicitacao(2L, 11L, 101L, ModalidadeSolicitacao.PRESENCIAL, curativos, 1, 0),
                solicitacao(3L, 12L, 77L, ModalidadeSolicitacao.PRESENCIAL, curativos, 1, 0),
                solicitacao(4L, 13L, 103L, ModalidadeSolicitacao.PRESENCIAL, idosos, 1, 0),
                solicitacao(5L, 14L, 104L, ModalidadeSolicitacao.PRESENCIAL, curativos, 1, 0)));
        when(profissionalRepository.findByUsuarioId(77L)).thenReturn(Optional.of(Profissional.builder().id(20L).build()));
        when(bloqueioUsuarioRepository.findPacienteIdsBloqueadosCom(20L)).thenReturn(Set.of(11L));

        List<SolicitacaoPublicaResponse> resultado = service.solicitacoes(77L,
                new ConsultaPublicaService.FiltroSolicitacoes(null, 5L, "são paulo", null, null));

        assertThat(resultado).extracting(SolicitacaoPublicaResponse::id).containsExactly(5L);
    }

}
