package com.quickcare.backend.proposta;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.geolocalizacao.Coordenadas;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.dto.PropostaRequest;
import com.quickcare.backend.proposta.dto.PropostaResponse;
import com.quickcare.backend.proposta.exception.PropostaJaEnviadaException;
import com.quickcare.backend.proposta.exception.PropostaNaoEncontradaException;
import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import com.quickcare.backend.solicitacao.SolicitacaoRepository;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoNaoEncontradaException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.StatusValidacao;
import com.quickcare.backend.validacao.exception.CadastroNaoValidadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropostaServiceTest {

    private static final Long USUARIO_PACIENTE_ID = 1L;
    private static final Long USUARIO_PROFISSIONAL_ID = 2L;
    private static final Long USUARIO_OUTRO_PROFISSIONAL_ID = 3L;
    /** ~1,4 km uma da outra. */
    private static final Coordenadas AV_PAULISTA = new Coordenadas(new BigDecimal("-23.564100"), new BigDecimal("-46.652400"));
    private static final Coordenadas RUA_AUGUSTA = new Coordenadas(new BigDecimal("-23.552300"), new BigDecimal("-46.647000"));
    /** ~70 km da Paulista. */
    private static final Coordenadas CAMPINAS = new Coordenadas(new BigDecimal("-22.905600"), new BigDecimal("-47.060800"));

    private static final PropostaRequest REQUEST = new PropostaRequest(
            new BigDecimal("180.00"), "  Curativos diários com relatório semanal de evolução.  ", LocalDate.now().plusDays(5));

    @Mock
    private PropostaRepository propostaRepository;
    @Mock
    private SolicitacaoRepository solicitacaoRepository;
    @Mock
    private ProfissionalRepository profissionalRepository;

    @InjectMocks
    private PropostaService service;

    private Especialidade curativos;
    private Solicitacao solicitacao;
    private Profissional profissional;

    @BeforeEach
    void setUp() {
        curativos = Especialidade.builder().id(5L).nome("Curativos").categoria(CategoriaProfissional.ENFERMEIRO).build();

        Paciente paciente = Paciente.builder()
                .id(10L)
                .usuario(Usuario.builder().id(USUARIO_PACIENTE_ID).nome("Paciente Teste").build())
                .build();
        solicitacao = Solicitacao.builder()
                .id(50L)
                .paciente(paciente)
                .profissionais(new LinkedHashSet<>())
                .descricao("Curativos diários para teste automatizado.")
                .modalidade(ModalidadeSolicitacao.PRESENCIAL)
                .cidade("São Paulo")
                .uf("SP")
                .latitude(AV_PAULISTA.latitude())
                .longitude(AV_PAULISTA.longitude())
                .prazoDesejado(LocalDate.now().plusDays(10))
                .status(StatusSolicitacao.ABERTA)
                .build();
        solicitacao.getProfissionais().add(SolicitacaoProfissional.builder()
                .id(1L)
                .solicitacao(solicitacao)
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .especialidades(new LinkedHashSet<>(List.of(curativos)))
                .quantidade(1)
                .build());

        profissional = profissional(20L, USUARIO_PROFISSIONAL_ID, ModalidadeAtendimento.AMBOS);
    }

    @Test
    void enviaPropostaEMoveSolicitacaoParaRecebendoPropostas() {
        mockProfissional(profissional);
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao));
        when(propostaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PropostaResponse response = service.enviar(USUARIO_PROFISSIONAL_ID, 50L, REQUEST);

        assertThat(response.status()).isEqualTo(StatusProposta.PENDENTE);
        assertThat(response.abordagem()).isEqualTo("Curativos diários com relatório semanal de evolução.");
        assertThat(response.profissional().nome()).isEqualTo("Profissional 20");
        assertThat(response.solicitacao().pacienteNome()).isEqualTo("Paciente Teste");
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.RECEBENDO_PROPOSTAS);
    }

    @Test
    void profissionalComCadastroEmAnaliseNaoEnviaProposta() {
        profissional.setStatusValidacao(StatusValidacao.REPROVADO);
        mockProfissional(profissional);

        assertThatThrownBy(() -> service.enviar(USUARIO_PROFISSIONAL_ID, 50L, REQUEST))
                .isInstanceOf(CadastroNaoValidadoException.class);
        verify(propostaRepository, never()).save(any());
    }

    @Test
    void naoEnviaParaSolicitacaoIncompativel() {
        Profissional remoto = profissional(20L, USUARIO_PROFISSIONAL_ID, ModalidadeAtendimento.REMOTO);
        mockProfissional(remoto);
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.enviar(USUARIO_PROFISSIONAL_ID, 50L, REQUEST))
                .isInstanceOf(SolicitacaoNaoEncontradaException.class);
        verify(propostaRepository, never()).save(any());
    }

    @Test
    void naoEnviaPropostaParaAPropriaSolicitacaoAbertaNoPerfilDePaciente() {
        Profissional mesmoUsuario = profissional(22L, USUARIO_PACIENTE_ID, ModalidadeAtendimento.AMBOS);
        mockProfissional(mesmoUsuario);
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.enviar(USUARIO_PACIENTE_ID, 50L, REQUEST))
                .isInstanceOf(SolicitacaoNaoEncontradaException.class);
        verify(propostaRepository, never()).save(any());
    }

    @Test
    void naoEnviaParaEnderecoForaDoRaio() {
        profissional.definirCoordenadas(CAMPINAS);
        mockProfissional(profissional);
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.enviar(USUARIO_PROFISSIONAL_ID, 50L, REQUEST))
                .isInstanceOf(SolicitacaoNaoEncontradaException.class);
    }

    @Test
    void semCoordenadasAceitaMesmaCidadeIgnorandoAcento() {
        profissional.definirCoordenadas(null);
        mockProfissional(profissional);
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao));
        when(propostaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.enviar(USUARIO_PROFISSIONAL_ID, 50L, REQUEST).status()).isEqualTo(StatusProposta.PENDENTE);
    }

    @Test
    void semCoordenadasRecusaOutraCidade() {
        profissional.definirCoordenadas(null);
        profissional.setCidade("Campinas");
        mockProfissional(profissional);
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.enviar(USUARIO_PROFISSIONAL_ID, 50L, REQUEST))
                .isInstanceOf(SolicitacaoNaoEncontradaException.class);
    }

    @Test
    void naoEnviaParaSolicitacaoEmAndamento() {
        solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        mockProfissional(profissional);
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.enviar(USUARIO_PROFISSIONAL_ID, 50L, REQUEST))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Esta solicitação não está mais recebendo propostas.");
    }

    @Test
    void naoEnviaSegundaPropostaAtiva() {
        mockProfissional(profissional);
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao));
        when(propostaRepository.existsBySolicitacaoIdAndProfissionalIdAndStatusNot(50L, 20L, StatusProposta.CANCELADA))
                .thenReturn(true);

        assertThatThrownBy(() -> service.enviar(USUARIO_PROFISSIONAL_ID, 50L, REQUEST))
                .isInstanceOf(PropostaJaEnviadaException.class);
    }

    @Test
    void pacienteNaoEnviaProposta() {
        when(profissionalRepository.findByUsuarioId(USUARIO_PACIENTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enviar(USUARIO_PACIENTE_ID, 50L, REQUEST))
                .isInstanceOf(PerfilNaoAutorizadoException.class);
    }

    @Test
    void pacienteVeAsPropostasNaoCanceladasDaSolicitacao() {
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao));
        when(propostaRepository.findBySolicitacaoIdOrderByCriadoEmAsc(50L)).thenReturn(List.of(
                proposta(100L, profissional, StatusProposta.PENDENTE),
                proposta(101L, profissional, StatusProposta.CANCELADA)));

        List<PropostaResponse> propostas = service.listarDaSolicitacao(USUARIO_PACIENTE_ID, 50L);

        assertThat(propostas).extracting(PropostaResponse::id).containsExactly(100L);
    }

    @Test
    void profissionalVeSoAsSuasPropostasDaSolicitacao() {
        when(solicitacaoRepository.findById(50L)).thenReturn(Optional.of(solicitacao));
        mockProfissional(profissional);
        when(propostaRepository.findBySolicitacaoIdAndProfissionalIdOrderByCriadoEmAsc(50L, 20L))
                .thenReturn(List.of(proposta(100L, profissional, StatusProposta.PENDENTE)));

        assertThat(service.listarDaSolicitacao(USUARIO_PROFISSIONAL_ID, 50L))
                .extracting(PropostaResponse::id).containsExactly(100L);
    }

    @Test
    void editaPropostaPendente() {
        Proposta proposta = proposta(100L, profissional, StatusProposta.PENDENTE);
        mockProfissional(profissional);
        when(propostaRepository.findById(100L)).thenReturn(Optional.of(proposta));
        when(propostaRepository.saveAndFlush(proposta)).thenReturn(proposta);

        PropostaResponse response = service.atualizar(USUARIO_PROFISSIONAL_ID, 100L,
                new PropostaRequest(new BigDecimal("250.00"), "Abordagem revisada com visitas diárias.", LocalDate.now()));

        assertThat(response.valor()).isEqualByComparingTo("250.00");
    }

    @Test
    void naoEditaPropostaDeOutroProfissional() {
        Profissional outro = profissional(21L, USUARIO_OUTRO_PROFISSIONAL_ID, ModalidadeAtendimento.AMBOS);
        mockProfissional(outro);
        when(propostaRepository.findById(100L)).thenReturn(Optional.of(proposta(100L, profissional, StatusProposta.PENDENTE)));

        assertThatThrownBy(() -> service.atualizar(USUARIO_OUTRO_PROFISSIONAL_ID, 100L, REQUEST))
                .isInstanceOf(PropostaNaoEncontradaException.class);
    }

    @Test
    void naoEditaPropostaJaRecusada() {
        mockProfissional(profissional);
        when(propostaRepository.findById(100L)).thenReturn(Optional.of(proposta(100L, profissional, StatusProposta.RECUSADA)));

        assertThatThrownBy(() -> service.atualizar(USUARIO_PROFISSIONAL_ID, 100L, REQUEST))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessage("Esta proposta não pode mais ser editada.");
    }

    @Test
    void cancelarUltimaPropostaPendenteReabreASolicitacao() {
        solicitacao.setStatus(StatusSolicitacao.RECEBENDO_PROPOSTAS);
        Proposta proposta = proposta(100L, profissional, StatusProposta.PENDENTE);
        mockProfissional(profissional);
        when(propostaRepository.findById(100L)).thenReturn(Optional.of(proposta));
        when(propostaRepository.existsBySolicitacaoIdAndStatus(50L, StatusProposta.PENDENTE)).thenReturn(false);

        PropostaResponse response = service.cancelar(USUARIO_PROFISSIONAL_ID, 100L);

        assertThat(response.status()).isEqualTo(StatusProposta.CANCELADA);
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.ABERTA);
    }

    @Test
    void aceitarRecusaAsDemaisEColocaSolicitacaoEmAndamento() {
        solicitacao.setStatus(StatusSolicitacao.RECEBENDO_PROPOSTAS);
        Profissional outro = profissional(21L, USUARIO_OUTRO_PROFISSIONAL_ID, ModalidadeAtendimento.AMBOS);
        Proposta escolhida = proposta(100L, profissional, StatusProposta.PENDENTE);
        Proposta concorrente = proposta(101L, outro, StatusProposta.PENDENTE);
        when(propostaRepository.findById(100L)).thenReturn(Optional.of(escolhida));
        when(propostaRepository.findBySolicitacaoIdAndStatus(50L, StatusProposta.PENDENTE))
                .thenReturn(List.of(escolhida, concorrente));
        when(propostaRepository.saveAndFlush(escolhida)).thenReturn(escolhida);

        PropostaResponse response = service.aceitar(USUARIO_PACIENTE_ID, 100L);

        assertThat(response.status()).isEqualTo(StatusProposta.ACEITA);
        assertThat(concorrente.getStatus()).isEqualTo(StatusProposta.RECUSADA);
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.EM_ANDAMENTO);
    }

    @Test
    void aceitaAteAQuantidadePedidaAntesDeEntrarEmAndamento() {
        solicitacao.setStatus(StatusSolicitacao.RECEBENDO_PROPOSTAS);
        SolicitacaoProfissional linha = solicitacao.getProfissionais().iterator().next();
        linha.setQuantidade(2);
        Profissional outro = profissional(21L, USUARIO_OUTRO_PROFISSIONAL_ID, ModalidadeAtendimento.AMBOS);
        Proposta primeira = proposta(100L, profissional, StatusProposta.PENDENTE);
        Proposta segunda = proposta(101L, outro, StatusProposta.PENDENTE);
        when(propostaRepository.findById(100L)).thenReturn(Optional.of(primeira));
        when(propostaRepository.findById(101L)).thenReturn(Optional.of(segunda));
        when(propostaRepository.findBySolicitacaoIdAndStatus(50L, StatusProposta.PENDENTE))
                .thenReturn(List.of(primeira, segunda), List.of(segunda));
        when(propostaRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.aceitar(USUARIO_PACIENTE_ID, 100L);

        assertThat(linha.getVagasPreenchidas()).isEqualTo(1);
        assertThat(primeira.getVaga()).isSameAs(linha);
        assertThat(segunda.getStatus()).isEqualTo(StatusProposta.PENDENTE);
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.RECEBENDO_PROPOSTAS);

        service.aceitar(USUARIO_PACIENTE_ID, 101L);

        assertThat(linha.getVagasPreenchidas()).isEqualTo(2);
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.EM_ANDAMENTO);
    }

    @Test
    void aoLotarUmaLinhaRecusaQuemSoCabiaNela() {
        Especialidade sondagem = Especialidade.builder().id(6L).nome("Sondagem").categoria(CategoriaProfissional.ENFERMEIRO).build();
        solicitacao.setStatus(StatusSolicitacao.RECEBENDO_PROPOSTAS);
        solicitacao.getProfissionais().add(SolicitacaoProfissional.builder()
                .id(2L)
                .solicitacao(solicitacao)
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .especialidades(new LinkedHashSet<>(List.of(sondagem)))
                .quantidade(1)
                .build());
        Profissional concorrenteCurativos = profissional(21L, USUARIO_OUTRO_PROFISSIONAL_ID, ModalidadeAtendimento.AMBOS);
        Profissional deSondagem = profissional(22L, 4L, ModalidadeAtendimento.AMBOS);
        deSondagem.setEspecialidades(Set.of(sondagem));
        Proposta escolhida = proposta(100L, profissional, StatusProposta.PENDENTE);
        Proposta semVaga = proposta(101L, concorrenteCurativos, StatusProposta.PENDENTE);
        Proposta aindaCabe = proposta(102L, deSondagem, StatusProposta.PENDENTE);
        when(propostaRepository.findById(100L)).thenReturn(Optional.of(escolhida));
        when(propostaRepository.findBySolicitacaoIdAndStatus(50L, StatusProposta.PENDENTE))
                .thenReturn(List.of(escolhida, semVaga, aindaCabe));
        when(propostaRepository.saveAndFlush(escolhida)).thenReturn(escolhida);

        service.aceitar(USUARIO_PACIENTE_ID, 100L);

        assertThat(semVaga.getStatus()).isEqualTo(StatusProposta.RECUSADA);
        assertThat(aindaCabe.getStatus()).isEqualTo(StatusProposta.PENDENTE);
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.RECEBENDO_PROPOSTAS);
    }

    @Test
    void soODonoDaSolicitacaoAceita() {
        when(propostaRepository.findById(100L)).thenReturn(Optional.of(proposta(100L, profissional, StatusProposta.PENDENTE)));

        assertThatThrownBy(() -> service.aceitar(USUARIO_PROFISSIONAL_ID, 100L))
                .isInstanceOf(PropostaNaoEncontradaException.class);
    }

    private void mockProfissional(Profissional profissional) {
        when(profissionalRepository.findByUsuarioId(profissional.getUsuario().getId())).thenReturn(Optional.of(profissional));
    }

    private Profissional profissional(Long id, Long usuarioId, ModalidadeAtendimento modalidade) {
        return Profissional.builder()
                .statusValidacao(StatusValidacao.APROVADO)
                .id(id)
                .usuario(Usuario.builder().id(usuarioId).nome("Profissional " + id).build())
                .categoria(CategoriaProfissional.ENFERMEIRO)
                .modalidadeAtendimento(modalidade)
                .raioAtendimentoKm(new BigDecimal("10"))
                .latitude(RUA_AUGUSTA.latitude())
                .longitude(RUA_AUGUSTA.longitude())
                .cidade("Sao Paulo")
                .uf("SP")
                .especialidades(Set.of(curativos))
                .build();
    }

    private Proposta proposta(Long id, Profissional autor, StatusProposta status) {
        return Proposta.builder()
                .id(id)
                .solicitacao(solicitacao)
                .profissional(autor)
                .valor(new BigDecimal("180.00"))
                .abordagem("Curativos diários com relatório semanal.")
                .prazo(LocalDate.now().plusDays(5))
                .status(status)
                .build();
    }

}
