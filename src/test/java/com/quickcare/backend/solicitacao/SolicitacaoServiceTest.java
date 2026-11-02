package com.quickcare.backend.solicitacao;

import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.ocorrencia.OcorrenciaService;
import com.quickcare.backend.cadastro.exception.EspecialidadeInvalidaException;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuarioRepository;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.especialidade.EspecialidadeRepository;
import com.quickcare.backend.especialidade.dto.EspecialidadeResponse;
import com.quickcare.backend.geolocalizacao.Coordenadas;
import com.quickcare.backend.geolocalizacao.EnderecoGeocodificavel;
import com.quickcare.backend.geolocalizacao.GeocodificacaoService;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.dto.EnderecoSolicitacaoRequest;
import com.quickcare.backend.solicitacao.dto.ProfissionalSolicitadoRequest;
import com.quickcare.backend.solicitacao.dto.ProfissionalSolicitadoResponse;
import com.quickcare.backend.solicitacao.dto.SolicitacaoRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoResponse;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoInvalidaException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoNaoEncontradaException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.StatusValidacao;
import com.quickcare.backend.validacao.exception.CadastroNaoValidadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoServiceTest {

    private static final Long USUARIO_PACIENTE_ID = 1L;
    private static final Long USUARIO_PROFISSIONAL_ID = 2L;
    private static final EnderecoSolicitacaoRequest ENDERECO =
            new EnderecoSolicitacaoRequest("01310100", "Avenida Paulista", "1000", " ", "Bela Vista", "São Paulo", "sp");

    @Mock
    private SolicitacaoRepository solicitacaoRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private ProfissionalRepository profissionalRepository;
    @Mock
    private EspecialidadeRepository especialidadeRepository;
    @Mock
    private PropostaRepository propostaRepository;
    @Mock
    private GeocodificacaoService geocodificacaoService;

    @Mock
    private Notificador notificador;

    @Mock
    private AtendimentoService atendimentoService;

    @Mock
    private AtendimentoRepository atendimentoRepository;

    @Mock
    private OcorrenciaService ocorrenciaService;

    @Mock
    private BloqueioUsuarioRepository bloqueioUsuarioRepository;

    @InjectMocks
    private SolicitacaoService service;

    private Paciente paciente;
    private Especialidade nutricao;
    private Especialidade curativos;
    private Especialidade sondagem;
    private Especialidade cuidadorIdosos;

    @BeforeEach
    void setUp() {
        Usuario usuario = Usuario.builder().id(USUARIO_PACIENTE_ID).build();
        paciente = Paciente.builder().id(10L).usuario(usuario).statusValidacao(StatusValidacao.APROVADO).build();
        nutricao = especialidade(4L, "Nutrição", CategoriaProfissional.OUTRO);
        curativos = especialidade(5L, "Curativos", CategoriaProfissional.ENFERMEIRO);
        sondagem = especialidade(6L, "Sondagem", CategoriaProfissional.ENFERMEIRO);
        cuidadorIdosos = especialidade(7L, "Cuidador de Idosos", CategoriaProfissional.CUIDADOR);
    }

    @Test
    void criaSolicitacaoComProfissionalDeVariasEspecialidades() {
        mockPaciente();
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(curativos, sondagem, cuidadorIdosos));
        when(solicitacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitacaoResponse response = service.criar(USUARIO_PACIENTE_ID, request(ModalidadeSolicitacao.PRESENCIAL, ENDERECO,
                pedido(CategoriaProfissional.ENFERMEIRO, 2, curativos, sondagem),
                pedido(CategoriaProfissional.CUIDADOR, 1, cuidadorIdosos)));

        assertThat(response.status()).isEqualTo(StatusSolicitacao.ABERTA);
        assertThat(response.profissionais()).hasSize(2);

        ProfissionalSolicitadoResponse enfermeiros = response.profissionais().get(0);
        assertThat(enfermeiros.categoria()).isEqualTo(CategoriaProfissional.ENFERMEIRO);
        assertThat(enfermeiros.quantidade()).isEqualTo(2);
        assertThat(enfermeiros.especialidades()).extracting(EspecialidadeResponse::nome)
                .containsExactly("Curativos", "Sondagem");

        assertThat(response.endereco().uf()).isEqualTo("SP");
        assertThat(response.endereco().complemento()).isNull();
    }

    @Test
    void solicitacaoOnlineDescartaEndereco() {
        mockPaciente();
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(nutricao));
        when(solicitacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitacaoResponse response = service.criar(USUARIO_PACIENTE_ID,
                request(ModalidadeSolicitacao.ONLINE, ENDERECO, pedido(CategoriaProfissional.OUTRO, 1, nutricao)));

        assertThat(response.endereco()).isNull();
    }

    @Test
    void rejeitaEspecialidadeDeOutraCategoria() {
        mockPaciente();
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(curativos, cuidadorIdosos));

        assertThatThrownBy(() -> service.criar(USUARIO_PACIENTE_ID, request(ModalidadeSolicitacao.ONLINE, null,
                pedido(CategoriaProfissional.ENFERMEIRO, 1, curativos, cuidadorIdosos))))
                .isInstanceOf(SolicitacaoInvalidaException.class)
                .hasMessage("As especialidades de cada profissional devem ser da categoria escolhida.");
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void rejeitaLinhasComAsMesmasEspecialidades() {
        mockPaciente();
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(curativos, sondagem));

        assertThatThrownBy(() -> service.criar(USUARIO_PACIENTE_ID, request(ModalidadeSolicitacao.ONLINE, null,
                pedido(CategoriaProfissional.ENFERMEIRO, 1, curativos, sondagem),
                pedido(CategoriaProfissional.ENFERMEIRO, 2, sondagem, curativos))))
                .isInstanceOf(SolicitacaoInvalidaException.class);
    }

    @Test
    void aceitaLinhasDaMesmaCategoriaComEspecialidadesDiferentes() {
        mockPaciente();
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(curativos, sondagem));
        when(solicitacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitacaoResponse response = service.criar(USUARIO_PACIENTE_ID, request(ModalidadeSolicitacao.ONLINE, null,
                pedido(CategoriaProfissional.ENFERMEIRO, 1, curativos),
                pedido(CategoriaProfissional.ENFERMEIRO, 1, curativos, sondagem)));

        assertThat(response.profissionais()).hasSize(2);
    }

    @Test
    void rejeitaEspecialidadeInexistente() {
        mockPaciente();
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(nutricao));

        assertThatThrownBy(() -> service.criar(USUARIO_PACIENTE_ID, request(ModalidadeSolicitacao.ONLINE, null,
                new ProfissionalSolicitadoRequest(CategoriaProfissional.OUTRO, Set.of(nutricao.getId(), 999L), 1))))
                .isInstanceOf(EspecialidadeInvalidaException.class);
    }

    @Test
    void exigeEnderecoNoAtendimentoPresencial() {
        mockPaciente();
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(nutricao));

        assertThatThrownBy(() -> service.criar(USUARIO_PACIENTE_ID,
                request(ModalidadeSolicitacao.PRESENCIAL, null, pedido(CategoriaProfissional.OUTRO, 1, nutricao))))
                .isInstanceOf(SolicitacaoInvalidaException.class);
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void rejeitaUfInexistente() {
        mockPaciente();
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(nutricao));
        EnderecoSolicitacaoRequest endereco =
                new EnderecoSolicitacaoRequest("01310100", "Rua A", "1", null, "Centro", "Cidade", "XX");

        assertThatThrownBy(() -> service.criar(USUARIO_PACIENTE_ID,
                request(ModalidadeSolicitacao.PRESENCIAL, endereco, pedido(CategoriaProfissional.OUTRO, 1, nutricao))))
                .isInstanceOf(SolicitacaoInvalidaException.class)
                .hasMessage("UF inválida.");
    }

    @Test
    void pacienteComCadastroEmAnaliseNaoCriaSolicitacao() {
        paciente.setStatusValidacao(StatusValidacao.PENDENTE);
        mockPaciente();

        assertThatThrownBy(() -> service.criar(USUARIO_PACIENTE_ID,
                request(ModalidadeSolicitacao.ONLINE, null, pedido(CategoriaProfissional.OUTRO, 1, nutricao))))
                .isInstanceOf(CadastroNaoValidadoException.class);
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void profissionalComCadastroEmAnaliseNaoVeSolicitacoesDisponiveis() {
        Profissional pendente = mockProfissional(ModalidadeAtendimento.AMBOS, CategoriaProfissional.ENFERMEIRO, curativos);
        pendente.setStatusValidacao(StatusValidacao.PENDENTE);

        assertThatThrownBy(() -> service.listarDisponiveis(USUARIO_PROFISSIONAL_ID))
                .isInstanceOf(CadastroNaoValidadoException.class);
    }

    @Test
    void profissionalNaoPodeCriarSolicitacao() {
        when(pacienteRepository.findByUsuarioId(USUARIO_PROFISSIONAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(USUARIO_PROFISSIONAL_ID,
                request(ModalidadeSolicitacao.ONLINE, null, pedido(CategoriaProfissional.OUTRO, 1, nutricao))))
                .isInstanceOf(PerfilNaoAutorizadoException.class);
    }

    @Test
    void edicaoSubstituiOsProfissionaisPedidos() {
        Solicitacao solicitacao = mockSolicitacaoDoPaciente(StatusSolicitacao.ABERTA);
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(curativos, sondagem));
        when(solicitacaoRepository.saveAndFlush(solicitacao)).thenReturn(solicitacao);

        service.atualizar(USUARIO_PACIENTE_ID, 50L,
                request(ModalidadeSolicitacao.ONLINE, null, pedido(CategoriaProfissional.ENFERMEIRO, 3, curativos, sondagem)));

        assertThat(solicitacao.getProfissionais()).singleElement().satisfies(linha -> {
            assertThat(linha.getCategoria()).isEqualTo(CategoriaProfissional.ENFERMEIRO);
            assertThat(linha.getQuantidade()).isEqualTo(3);
            assertThat(linha.getEspecialidades()).containsExactly(curativos, sondagem);
        });
    }

    @Test
    void solicitacaoPresencialGuardaAsCoordenadasDoEndereco() {
        mockPaciente();
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(nutricao));
        when(solicitacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Coordenadas paulista = new Coordenadas(new BigDecimal("-23.564100"), new BigDecimal("-46.652400"));
        when(geocodificacaoService.geocodificar(new EnderecoGeocodificavel(
                "01310100", "Avenida Paulista", "1000", "São Paulo", "SP"))).thenReturn(Optional.of(paulista));
        ArgumentCaptor<Solicitacao> salva = ArgumentCaptor.forClass(Solicitacao.class);

        service.criar(USUARIO_PACIENTE_ID,
                request(ModalidadeSolicitacao.PRESENCIAL, ENDERECO, pedido(CategoriaProfissional.OUTRO, 1, nutricao)));

        verify(solicitacaoRepository).save(salva.capture());
        assertThat(salva.getValue().getLatitude()).isEqualByComparingTo("-23.564100");
        assertThat(salva.getValue().getLongitude()).isEqualByComparingTo("-46.652400");
    }

    /** Os aceitos não voltam para o paciente: reservam vaga e o profissional reaceita ou recusa. */
    @Test
    void editarComAceitosPedeReaceiteAosProfissionais() {
        Solicitacao solicitacao = mockSolicitacaoDoPaciente(StatusSolicitacao.EM_ANDAMENTO);
        Proposta aceita = Proposta.builder().id(100L).solicitacao(solicitacao).status(StatusProposta.ACEITA).build();
        Proposta pendente = Proposta.builder().id(101L).solicitacao(solicitacao).status(StatusProposta.PENDENTE).build();
        when(propostaRepository.findBySolicitacaoIdAndStatus(50L, StatusProposta.PENDENTE)).thenReturn(List.of(pendente));
        when(atendimentoService.solicitacaoEditada(solicitacao)).thenReturn(List.of(aceita));
        when(especialidadeRepository.findAllByIdIn(anyList())).thenReturn(List.of(nutricao));
        when(solicitacaoRepository.saveAndFlush(solicitacao)).thenReturn(solicitacao);

        service.atualizar(USUARIO_PACIENTE_ID, 50L,
                request(ModalidadeSolicitacao.ONLINE, null, pedido(CategoriaProfissional.OUTRO, 1, nutricao)));

        InOrder ordem = inOrder(atendimentoService, solicitacaoRepository);
        ordem.verify(atendimentoService).prepararEdicao(solicitacao);
        ordem.verify(solicitacaoRepository).saveAndFlush(solicitacao);
        ordem.verify(atendimentoService).solicitacaoEditada(solicitacao);
        verify(notificador).solicitacaoAlterada(List.of(aceita), List.of(pendente));
    }

    @Test
    void disponiveisDescartaPresencialForaDoRaio() {
        Profissional profissional = mockProfissional(ModalidadeAtendimento.AMBOS, CategoriaProfissional.ENFERMEIRO, curativos, sondagem);
        profissional.setRaioAtendimentoKm(new BigDecimal("10"));
        profissional.setLatitude(new BigDecimal("-23.552300"));
        profissional.setLongitude(new BigDecimal("-46.647000"));

        Solicitacao perto = solicitacaoPedindoEnfermeiroCurativosESondagem();
        perto.setModalidade(ModalidadeSolicitacao.PRESENCIAL);
        perto.setLatitude(new BigDecimal("-23.564100"));
        perto.setLongitude(new BigDecimal("-46.652400"));
        Solicitacao longe = solicitacaoPedindoEnfermeiroCurativosESondagem();
        longe.setId(51L);
        longe.setModalidade(ModalidadeSolicitacao.PRESENCIAL);
        longe.setLatitude(new BigDecimal("-22.905600"));
        longe.setLongitude(new BigDecimal("-47.060800"));
        when(solicitacaoRepository.findDisponiveisPara(any(), any(), any(), any())).thenReturn(List.of(perto, longe));

        assertThat(service.listarDisponiveis(USUARIO_PROFISSIONAL_ID))
                .extracting(SolicitacaoResponse::id).containsExactly(50L);
    }

    @Test
    void bloqueiaEdicaoDeSolicitacaoConcluida() {
        mockSolicitacaoDoPaciente(StatusSolicitacao.CONCLUIDA);

        assertThatThrownBy(() -> service.atualizar(USUARIO_PACIENTE_ID, 50L,
                request(ModalidadeSolicitacao.ONLINE, null, pedido(CategoriaProfissional.OUTRO, 1, nutricao))))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Esta solicitação não pode mais ser editada.");
    }

    @Test
    void cancelaSolicitacaoEmAndamento() {
        Solicitacao solicitacao = mockSolicitacaoDoPaciente(StatusSolicitacao.EM_ANDAMENTO);
        when(solicitacaoRepository.saveAndFlush(solicitacao)).thenReturn(solicitacao);

        SolicitacaoResponse response = service.cancelar(USUARIO_PACIENTE_ID, 50L);

        assertThat(response.status()).isEqualTo(StatusSolicitacao.CANCELADA);
    }

    @Test
    void cancelarRecusaAsPropostasPendentes() {
        Solicitacao solicitacao = mockSolicitacaoDoPaciente(StatusSolicitacao.RECEBENDO_PROPOSTAS);
        Proposta pendente = Proposta.builder().id(1L).status(StatusProposta.PENDENTE).build();
        when(propostaRepository.findBySolicitacaoIdAndStatus(50L, StatusProposta.PENDENTE)).thenReturn(List.of(pendente));
        when(solicitacaoRepository.saveAndFlush(solicitacao)).thenReturn(solicitacao);

        service.cancelar(USUARIO_PACIENTE_ID, 50L);

        assertThat(pendente.getStatus()).isEqualTo(StatusProposta.RECUSADA);
        verify(atendimentoService).solicitacaoCancelada(solicitacao);
        verify(notificador).solicitacaoCancelada(List.of(pendente));
    }

    @Test
    void bloqueiaCancelamentoDeSolicitacaoConcluida() {
        mockSolicitacaoDoPaciente(StatusSolicitacao.CONCLUIDA);

        assertThatThrownBy(() -> service.cancelar(USUARIO_PACIENTE_ID, 50L))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    @Test
    void profissionalQueCobreTodasAsEspecialidadesVeASolicitacao() {
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacaoPedindoEnfermeiroCurativosESondagem()));
        mockProfissional(CategoriaProfissional.ENFERMEIRO, curativos, sondagem, nutricao);

        assertThat(service.buscar(USUARIO_PROFISSIONAL_ID, 50L).id()).isEqualTo(50L);
    }

    @Test
    void profissionalQueCobreSoParteDasEspecialidadesNaoVe() {
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacaoPedindoEnfermeiroCurativosESondagem()));
        mockProfissional(CategoriaProfissional.ENFERMEIRO, curativos);

        assertThatThrownBy(() -> service.buscar(USUARIO_PROFISSIONAL_ID, 50L))
                .isInstanceOf(SolicitacaoNaoEncontradaException.class);
    }

    @Test
    void profissionalDeOutraCategoriaNaoVe() {
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacaoPedindoEnfermeiroCurativosESondagem()));
        mockProfissional(CategoriaProfissional.CUIDADOR, curativos, sondagem);

        assertThatThrownBy(() -> service.buscar(USUARIO_PROFISSIONAL_ID, 50L))
                .isInstanceOf(SolicitacaoNaoEncontradaException.class);
    }

    @Test
    void profissionalQueSoAtendePresencialNaoVeSolicitacaoOnline() {
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacaoPedindoEnfermeiroCurativosESondagem()));
        mockProfissional(ModalidadeAtendimento.PRESENCIAL, CategoriaProfissional.ENFERMEIRO, curativos, sondagem);

        assertThatThrownBy(() -> service.buscar(USUARIO_PROFISSIONAL_ID, 50L))
                .isInstanceOf(SolicitacaoNaoEncontradaException.class);
    }

    @Test
    void profissionalQueJaEnviouPropostaContinuaVendoASolicitacao() {
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacaoPedindoEnfermeiroCurativosESondagem()));
        mockProfissional(ModalidadeAtendimento.PRESENCIAL, CategoriaProfissional.ENFERMEIRO, curativos, sondagem);
        when(propostaRepository.existsBySolicitacaoIdAndProfissionalIdAndStatusNot(any(), any(), any())).thenReturn(true);

        assertThat(service.buscar(USUARIO_PROFISSIONAL_ID, 50L).id()).isEqualTo(50L);
    }

    @Test
    void solicitacaoComPropostaDoProfissionalSaiDasDisponiveis() {
        mockProfissional(CategoriaProfissional.ENFERMEIRO, curativos, sondagem);
        Solicitacao comProposta = solicitacaoPedindoEnfermeiroCurativosESondagem();
        Solicitacao semProposta = solicitacaoPedindoEnfermeiroCurativosESondagem();
        semProposta.setId(51L);
        when(solicitacaoRepository.findDisponiveisPara(any(), any(), any(), any()))
                .thenReturn(List.of(comProposta, semProposta));
        when(propostaRepository.findSolicitacaoIdsComPropostaDoProfissional(20L, StatusProposta.ATIVAS))
                .thenReturn(Set.of(50L));

        assertThat(service.listarDisponiveis(USUARIO_PROFISSIONAL_ID))
                .extracting(SolicitacaoResponse::id).containsExactly(51L);
    }

    @Test
    void profissionalQueTambemEhPacienteNaoVeAsPropriasSolicitacoes() {
        Profissional mesmoUsuario = Profissional.builder()
                .statusValidacao(StatusValidacao.APROVADO)
                .id(21L)
                .modalidadeAtendimento(ModalidadeAtendimento.AMBOS)
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .especialidades(Set.of(curativos, sondagem))
                .build();
        when(profissionalRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(mesmoUsuario));
        when(solicitacaoRepository.findDisponiveisPara(any(), any(), any(), any()))
                .thenReturn(List.of(solicitacaoPedindoEnfermeiroCurativosESondagem()));

        assertThat(service.listarDisponiveis(USUARIO_PACIENTE_ID)).isEmpty();
    }

    @Test
    void disponiveisFiltraPelasModalidadesQueOProfissionalAtende() {
        mockProfissional(ModalidadeAtendimento.REMOTO, CategoriaProfissional.ENFERMEIRO, curativos);

        service.listarDisponiveis(USUARIO_PROFISSIONAL_ID);

        verify(solicitacaoRepository).findDisponiveisPara(
                CategoriaProfissional.ENFERMEIRO, Set.of(curativos.getId()),
                Set.of(ModalidadeSolicitacao.ONLINE), StatusSolicitacao.RECEBENDO_PROPOSTAS_STATUS);
    }

    private static Especialidade especialidade(Long id, String nome, CategoriaProfissional categoria) {
        return Especialidade.builder().id(id).nome(nome).categoria(categoria).build();
    }

    private void mockPaciente() {
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.of(paciente));
    }

    private void mockProfissional(CategoriaProfissional categoria, Especialidade... especialidades) {
        mockProfissional(ModalidadeAtendimento.AMBOS, categoria, especialidades);
    }

    private Profissional mockProfissional(ModalidadeAtendimento modalidade, CategoriaProfissional categoria,
                                          Especialidade... especialidades) {
        Profissional profissional = Profissional.builder()
                .statusValidacao(StatusValidacao.APROVADO)
                .id(20L)
                .modalidadeAtendimento(modalidade)
                .categoria(categoria)
                .especialidades(Set.of(especialidades))
                .build();
        when(profissionalRepository.findByUsuarioId(USUARIO_PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));
        return profissional;
    }

    private Solicitacao mockSolicitacaoDoPaciente(StatusSolicitacao status) {
        Solicitacao solicitacao = solicitacao(status, CategoriaProfissional.OUTRO, nutricao);
        mockPaciente();
        when(solicitacaoRepository.findByIdAndPacienteId(50L, paciente.getId())).thenReturn(Optional.of(solicitacao));
        return solicitacao;
    }

    private Solicitacao solicitacaoPedindoEnfermeiroCurativosESondagem() {
        return solicitacao(StatusSolicitacao.ABERTA, CategoriaProfissional.ENFERMEIRO, curativos, sondagem);
    }

    /** Solicitação online de outro usuário que pede 1 profissional com as especialidades informadas. */
    private Solicitacao solicitacao(StatusSolicitacao status, CategoriaProfissional categoria, Especialidade... especialidades) {
        Solicitacao solicitacao = Solicitacao.builder()
                .id(50L)
                .paciente(paciente)
                .profissionais(new LinkedHashSet<>())
                .descricao("Acompanhamento para teste automatizado.")
                .modalidade(ModalidadeSolicitacao.ONLINE)
                .prazoDesejado(LocalDate.now().plusDays(10))
                .status(status)
                .build();
        solicitacao.getProfissionais().add(SolicitacaoProfissional.builder()
                .id(1L)
                .solicitacao(solicitacao)
                .categoria(categoria)
                .especialidades(new LinkedHashSet<>(Arrays.asList(especialidades)))
                .quantidade(1)
                .build());
        return solicitacao;
    }

    private ProfissionalSolicitadoRequest pedido(CategoriaProfissional categoria, int quantidade, Especialidade... especialidades) {
        Set<Long> ids = Arrays.stream(especialidades).map(Especialidade::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ProfissionalSolicitadoRequest(categoria, ids, quantidade);
    }

    private SolicitacaoRequest request(ModalidadeSolicitacao modalidade, EnderecoSolicitacaoRequest endereco,
                                       ProfissionalSolicitadoRequest... profissionais) {
        return new SolicitacaoRequest(List.of(profissionais), "Acompanhamento para teste automatizado.",
                modalidade, endereco, LocalDate.now().plusDays(10));
    }

}
