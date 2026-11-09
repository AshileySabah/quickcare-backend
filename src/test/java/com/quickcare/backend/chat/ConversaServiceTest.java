package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.ConversaResponse;
import com.quickcare.backend.chat.exception.ConversaAcessoNegadoException;
import com.quickcare.backend.chat.exception.ConversaSomenteLeituraException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversaServiceTest {

    private static final Long SOLICITACAO = 10L;
    private static final Long PACIENTE = 1L;
    private static final Long PROF_A = 2L;
    private static final Long PROF_B = 3L;

    private static final Instant ENTROU = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant SAIU = Instant.parse("2026-11-02T10:00:00Z");

    @Mock
    private ChatPropostaRepository chatPropostaRepository;
    @Mock
    private ConversaProfissionalRepository conversaProfissionalRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private MensagemRepository mensagemRepository;
    @Mock
    private ArmazenamentoDocumentoService armazenamento;
    @InjectMocks
    private ConversaService service;

    // ---- acesso ao canal individual -------------------------------------------------------

    @Test
    void pacienteEProfissionalAcessamAConversaIndividualEEscrevem() {
        cenario(List.of(PROF_A), participacao(PROF_A, ENTROU, null));

        AcessoAoCanal doPaciente = service.acessoDe(SOLICITACAO, PROF_A, PACIENTE);
        AcessoAoCanal doProfissional = service.acessoDe(SOLICITACAO, PROF_A, PROF_A);

        assertThat(doPaciente).isEqualTo(new AcessoAoCanal(true, null, null));
        assertThat(doProfissional).isEqualTo(new AcessoAoCanal(true, null, null));
    }

    @Test
    void outroProfissionalNaoAcessaAConversaIndividualDeOutro() {
        cenario(List.of(PROF_A, PROF_B), participacao(PROF_A, ENTROU, null), participacao(PROF_B, ENTROU, null));

        assertThatThrownBy(() -> service.acessoDe(SOLICITACAO, PROF_A, PROF_B))
                .isInstanceOf(ConversaAcessoNegadoException.class);
    }

    @Test
    void quemNaoParticipaRecebeAcessoNegado() {
        cenario(List.of(PROF_A), participacao(PROF_A, ENTROU, null));

        assertThatThrownBy(() -> service.acessoDe(SOLICITACAO, PROF_A, 99L))
                .isInstanceOf(ConversaAcessoNegadoException.class);
        assertThatThrownBy(() -> service.acessoDe(SOLICITACAO, null, 99L))
                .isInstanceOf(ConversaAcessoNegadoException.class);
    }

    @Test
    void semIdsNegaAcesso() {
        assertThatThrownBy(() -> service.acessoDe(null, null, 1L)).isInstanceOf(ConversaAcessoNegadoException.class);
        assertThatThrownBy(() -> service.acessoDe(SOLICITACAO, null, null))
                .isInstanceOf(ConversaAcessoNegadoException.class);
    }

    @Test
    void conversaIndividualComProfissionalInexistenteEhNegada() {
        cenario(List.of(PROF_A), participacao(PROF_A, ENTROU, null));

        assertThatThrownBy(() -> service.acessoDe(SOLICITACAO, PROF_B, PACIENTE))
                .isInstanceOf(ConversaAcessoNegadoException.class);
    }

    // ---- grupo ----------------------------------------------------------------------------

    @Test
    void grupoNaoExisteComUmUnicoProfissional() {
        cenario(List.of(PROF_A), participacao(PROF_A, ENTROU, null));

        assertThatThrownBy(() -> service.acessoDe(SOLICITACAO, null, PACIENTE))
                .isInstanceOf(ConversaAcessoNegadoException.class);
        assertThatThrownBy(() -> service.acessoDe(SOLICITACAO, null, PROF_A))
                .isInstanceOf(ConversaAcessoNegadoException.class);
    }

    @Test
    void grupoComDoisProfissionaisEAcessivelATodos() {
        cenario(List.of(PROF_A, PROF_B), participacao(PROF_A, ENTROU, null), participacao(PROF_B, ENTROU, null));

        assertThat(service.acessoDe(SOLICITACAO, null, PACIENTE)).isEqualTo(new AcessoAoCanal(true, null, null));
        assertThat(service.acessoDe(SOLICITACAO, null, PROF_B)).isEqualTo(new AcessoAoCanal(true, ENTROU, null));
    }

    @Test
    void grupoPermanecePorqueJaHouveDoisProfissionaisMesmoQueUmTenhaSaido() {
        cenario(List.of(PROF_A), participacao(PROF_A, ENTROU, null), participacao(PROF_B, ENTROU, SAIU));

        assertThat(service.acessoDe(SOLICITACAO, null, PACIENTE).podeEscrever()).isTrue();
        assertThat(service.acessoDe(SOLICITACAO, null, PROF_A).podeEscrever()).isTrue();
    }

    // ---- profissional removido: só histórico ----------------------------------------------

    @Test
    void profissionalQueSaiuLeOHistoricoAteASaidaEMasNaoEscreve() {
        cenario(List.of(PROF_A), participacao(PROF_A, ENTROU, null), participacao(PROF_B, ENTROU, SAIU));

        AcessoAoCanal individual = service.acessoDe(SOLICITACAO, PROF_B, PROF_B);
        AcessoAoCanal grupo = service.acessoDe(SOLICITACAO, null, PROF_B);

        assertThat(individual.podeEscrever()).isFalse();
        assertThat(individual.enxerga(SAIU.minusSeconds(1))).isTrue();
        assertThat(individual.enxerga(SAIU.plusSeconds(1))).isFalse();
        assertThat(grupo.podeEscrever()).isFalse();
        assertThat(grupo.enxerga(SAIU.plusSeconds(1))).isFalse();
        assertThat(grupo.enxerga(ENTROU.minusSeconds(1))).isFalse();
    }

    @Test
    void profissionalQueSaiuNaoPodeEnviar() {
        cenario(List.of(PROF_A), participacao(PROF_A, ENTROU, null), participacao(PROF_B, ENTROU, SAIU));

        assertThatThrownBy(() -> service.exigirPodeEscrever(SOLICITACAO, PROF_B, PROF_B))
                .isInstanceOf(ConversaSomenteLeituraException.class);
        assertThatThrownBy(() -> service.exigirPodeEscrever(SOLICITACAO, null, PROF_B))
                .isInstanceOf(ConversaSomenteLeituraException.class);
    }

    @Test
    void pacienteVeTudoMasNaoEscreveParaProfissionalQueSaiu() {
        cenario(List.of(PROF_A), participacao(PROF_A, ENTROU, null), participacao(PROF_B, ENTROU, SAIU));

        AcessoAoCanal comQuemSaiu = service.acessoDe(SOLICITACAO, PROF_B, PACIENTE);

        assertThat(comQuemSaiu.podeEscrever()).isFalse();
        assertThat(comQuemSaiu.enxerga(SAIU.plusSeconds(1))).isTrue();
        assertThatThrownBy(() -> service.exigirPodeEscrever(SOLICITACAO, PROF_B, PACIENTE))
                .isInstanceOf(ConversaSomenteLeituraException.class);
    }

    @Test
    void pacienteNaoEscreveNoGrupoQuandoTodosSairam() {
        cenario(List.of(), participacao(PROF_A, ENTROU, SAIU), participacao(PROF_B, ENTROU, SAIU));

        assertThat(service.acessoDe(SOLICITACAO, null, PACIENTE).podeEscrever()).isFalse();
    }

    // ---- sincronização com as propostas ---------------------------------------------------

    @Test
    void propostaAceitaCriaAParticipacaoDoProfissional() {
        cenario(List.of(PROF_A));

        AcessoAoCanal acesso = service.acessoDe(SOLICITACAO, PROF_A, PACIENTE);

        assertThat(acesso.podeEscrever()).isTrue();
        verify(conversaProfissionalRepository).save(any(ConversaProfissional.class));
    }

    @Test
    void propostaQueDeixouDeSerAceitaEncerraAParticipacaoNoMomentoDaMudanca() {
        Instant mudouEm = Instant.now().minusSeconds(3600);
        ConversaProfissional participacao = participacao(PROF_A, mudouEm.minusSeconds(7200), null);
        cenario(List.of(), participacao);
        when(chatPropostaRepository.ultimaAtualizacaoDaProposta(SOLICITACAO, PROF_A)).thenReturn(mudouEm);

        AcessoAoCanal acesso = service.acessoDe(SOLICITACAO, PROF_A, PROF_A);

        assertThat(participacao.getSaiuEm()).isEqualTo(mudouEm);
        assertThat(acesso.podeEscrever()).isFalse();
        assertThat(acesso.lerAte()).isEqualTo(mudouEm);
    }

    @Test
    void profissionalQueVoltaATerPropostaAceitaReabreAConversa() {
        ConversaProfissional participacao = participacao(PROF_A, ENTROU, SAIU);
        cenario(List.of(PROF_A), participacao);

        AcessoAoCanal acesso = service.acessoDe(SOLICITACAO, PROF_A, PROF_A);

        assertThat(participacao.ativo()).isTrue();
        assertThat(acesso.podeEscrever()).isTrue();
        verify(conversaProfissionalRepository, never()).save(any());
    }

    // ---- destinatários em tempo real ------------------------------------------------------

    @Test
    void destinatariosDoGrupoSaoOPacienteEQuemAindaEstaNaConversa() {
        lenient().when(chatPropostaRepository.usuarioIdDoPaciente(SOLICITACAO)).thenReturn(Optional.of(PACIENTE));
        when(conversaProfissionalRepository.findBySolicitacaoId(SOLICITACAO))
                .thenReturn(List.of(participacao(PROF_A, ENTROU, null), participacao(PROF_B, ENTROU, SAIU)));

        assertThat(service.destinatarios(SOLICITACAO, null)).containsExactly(PACIENTE, PROF_A);
    }

    @Test
    void destinatariosDaConversaIndividualSaoOPacienteEOProfissional() {
        lenient().when(chatPropostaRepository.usuarioIdDoPaciente(SOLICITACAO)).thenReturn(Optional.of(PACIENTE));
        when(conversaProfissionalRepository.findBySolicitacaoId(SOLICITACAO))
                .thenReturn(List.of(participacao(PROF_A, ENTROU, null), participacao(PROF_B, ENTROU, null)));

        assertThat(service.destinatarios(SOLICITACAO, PROF_B)).containsExactly(PACIENTE, PROF_B);
    }

    // ---- listagem -------------------------------------------------------------------------

    @Test
    void pacienteVeUmaConversaPorProfissionalMaisOGrupo() {
        Usuario paciente = usuario(PACIENTE, "Paciente");
        Usuario a = usuario(PROF_A, "Profissional A");
        Usuario b = usuario(PROF_B, "Profissional B");
        cenario(List.of(PROF_A, PROF_B), participacao(PROF_A, ENTROU, null), participacao(PROF_B, ENTROU, null));
        when(chatPropostaRepository.solicitacaoIdsComChat(PACIENTE, StatusProposta.ACEITA)).thenReturn(List.of(SOLICITACAO));
        when(conversaProfissionalRepository.solicitacaoIdsComoProfissional(PACIENTE)).thenReturn(List.of());
        when(conversaProfissionalRepository.solicitacaoIdsComoPaciente(PACIENTE)).thenReturn(List.of(SOLICITACAO));
        when(usuarioRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any()))
                .thenReturn(List.of(paciente, a, b));

        List<ConversaResponse> conversas = service.listar(PACIENTE);

        assertThat(conversas).hasSize(3);
        assertThat(conversas).extracting(ConversaResponse::tipo)
                .containsExactlyInAnyOrder("INDIVIDUAL", "INDIVIDUAL", "GRUPO");
        assertThat(conversas).extracting(ConversaResponse::profissionalUsuarioId)
                .containsExactlyInAnyOrder(PROF_A, PROF_B, null);
        assertThat(conversas).allMatch(c -> !c.somenteLeitura());
        ConversaResponse grupo = conversas.stream().filter(c -> "GRUPO".equals(c.tipo())).findFirst().orElseThrow();
        assertThat(grupo.participantes()).extracting("usuarioId", "papel")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PACIENTE, "PACIENTE"),
                        org.assertj.core.groups.Tuple.tuple(PROF_A, "PROFISSIONAL"),
                        org.assertj.core.groups.Tuple.tuple(PROF_B, "PROFISSIONAL"));
    }

    @Test
    void profissionalVeSoASuaConversaIndividualEOGrupo() {
        cenario(List.of(PROF_A, PROF_B), participacao(PROF_A, ENTROU, null), participacao(PROF_B, ENTROU, null));
        when(chatPropostaRepository.solicitacaoIdsComChat(PROF_A, StatusProposta.ACEITA)).thenReturn(List.of(SOLICITACAO));
        when(conversaProfissionalRepository.solicitacaoIdsComoProfissional(PROF_A)).thenReturn(List.of(SOLICITACAO));
        when(conversaProfissionalRepository.solicitacaoIdsComoPaciente(PROF_A)).thenReturn(List.of());
        when(usuarioRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any())).thenReturn(List.of());

        List<ConversaResponse> conversas = service.listar(PROF_A);

        assertThat(conversas).extracting(ConversaResponse::tipo).containsExactlyInAnyOrder("INDIVIDUAL", "GRUPO");
        assertThat(conversas).filteredOn(c -> "INDIVIDUAL".equals(c.tipo()))
                .extracting(ConversaResponse::profissionalUsuarioId).containsExactly(PROF_A);
    }

    @Test
    void profissionalQueSaiuAindaVeAConversaMasSomenteLeituraEComAUltimaMensagemAteASaida() {
        Mensagem antes = new Mensagem(PACIENTE, SOLICITACAO, PROF_B, "antes");
        ReflectionTestUtils.setField(antes, "dataEnvio", SAIU.minusSeconds(60));
        Mensagem depois = new Mensagem(PACIENTE, SOLICITACAO, PROF_B, "depois");
        ReflectionTestUtils.setField(depois, "dataEnvio", SAIU.plusSeconds(60));
        cenario(List.of(PROF_A), participacao(PROF_A, ENTROU, null), participacao(PROF_B, ENTROU, SAIU));
        when(chatPropostaRepository.solicitacaoIdsComChat(PROF_B, StatusProposta.ACEITA)).thenReturn(List.of());
        when(conversaProfissionalRepository.solicitacaoIdsComoProfissional(PROF_B)).thenReturn(List.of(SOLICITACAO));
        when(conversaProfissionalRepository.solicitacaoIdsComoPaciente(PROF_B)).thenReturn(List.of());
        when(usuarioRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any())).thenReturn(List.of());
        when(mensagemRepository.listarDoCanal(SOLICITACAO, PROF_B)).thenReturn(List.of(antes, depois));
        when(mensagemRepository.listarDoCanal(SOLICITACAO, null)).thenReturn(List.of());

        List<ConversaResponse> conversas = service.listar(PROF_B);

        assertThat(conversas).hasSize(2);
        assertThat(conversas).allMatch(ConversaResponse::somenteLeitura);
        ConversaResponse individual = conversas.stream().filter(c -> "INDIVIDUAL".equals(c.tipo())).findFirst().orElseThrow();
        assertThat(individual.ultimaMensagem().conteudo()).isEqualTo("antes");
    }

    @Test
    void listarOrdenaPelaMensagemMaisRecente() {
        Mensagem antiga = new Mensagem(PROF_A, 10L, PROF_A, "antiga");
        ReflectionTestUtils.setField(antiga, "dataEnvio", Instant.parse("2026-11-01T10:00:00Z"));
        Mensagem recente = new Mensagem(PACIENTE, 11L, PROF_A, "recente");
        ReflectionTestUtils.setField(recente, "dataEnvio", Instant.parse("2026-11-05T10:00:00Z"));
        when(chatPropostaRepository.solicitacaoIdsComChat(PACIENTE, StatusProposta.ACEITA)).thenReturn(List.of(10L, 11L, 12L));
        when(conversaProfissionalRepository.solicitacaoIdsComoProfissional(PACIENTE)).thenReturn(List.of());
        when(conversaProfissionalRepository.solicitacaoIdsComoPaciente(PACIENTE)).thenReturn(List.of());
        for (Long id : List.of(10L, 11L, 12L)) {
            when(chatPropostaRepository.usuarioIdDoPaciente(id)).thenReturn(Optional.of(PACIENTE));
            when(chatPropostaRepository.usuarioIdsDosProfissionais(id, StatusProposta.ACEITA)).thenReturn(List.of(PROF_A));
            when(conversaProfissionalRepository.findBySolicitacaoId(id))
                    .thenReturn(List.of(participacaoEm(id, PROF_A, ENTROU, null)));
        }
        when(usuarioRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any())).thenReturn(List.of());
        when(mensagemRepository.findUltimasPorCanal(List.of(10L, 11L, 12L))).thenReturn(List.of(antiga, recente));

        List<ConversaResponse> conversas = service.listar(PACIENTE);

        assertThat(conversas).extracting(ConversaResponse::solicitacaoId).containsExactly(11L, 10L, 12L);
        assertThat(conversas.get(0).ultimaMensagem().conteudo()).isEqualTo("recente");
        assertThat(conversas.get(2).ultimaMensagem()).isNull();
    }

    @Test
    void listarSemConversasRetornaVazio() {
        when(chatPropostaRepository.solicitacaoIdsComChat(5L, StatusProposta.ACEITA)).thenReturn(List.of());
        when(conversaProfissionalRepository.solicitacaoIdsComoProfissional(5L)).thenReturn(List.of());
        when(conversaProfissionalRepository.solicitacaoIdsComoPaciente(5L)).thenReturn(List.of());

        assertThat(service.listar(5L)).isEmpty();
    }

    // ---- apoio ----------------------------------------------------------------------------

    /** Solicitação 10 do paciente 1, com os profissionais dados com proposta ACEITA e as participações já gravadas. */
    private void cenario(List<Long> profissionaisComPropostaAceita, ConversaProfissional... participacoes) {
        lenient().when(chatPropostaRepository.usuarioIdDoPaciente(SOLICITACAO)).thenReturn(Optional.of(PACIENTE));
        lenient().when(chatPropostaRepository.usuarioIdsDosProfissionais(SOLICITACAO, StatusProposta.ACEITA))
                .thenReturn(profissionaisComPropostaAceita);
        lenient().when(conversaProfissionalRepository.findBySolicitacaoId(SOLICITACAO))
                .thenReturn(List.of(participacoes));
    }

    private ConversaProfissional participacao(Long usuarioId, Instant entrou, Instant saiu) {
        return participacaoEm(SOLICITACAO, usuarioId, entrou, saiu);
    }

    private ConversaProfissional participacaoEm(Long solicitacaoId, Long usuarioId, Instant entrou, Instant saiu) {
        ConversaProfissional participacao = new ConversaProfissional(solicitacaoId, usuarioId, entrou);
        if (saiu != null) {
            participacao.sair(saiu);
        }
        return participacao;
    }

    private Usuario usuario(Long id, String nome) {
        Usuario usuario = mock(Usuario.class);
        lenient().when(usuario.getId()).thenReturn(id);
        lenient().when(usuario.getNome()).thenReturn(nome);
        return usuario;
    }

}
