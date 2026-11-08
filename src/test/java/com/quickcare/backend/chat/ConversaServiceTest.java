package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.ConversaResponse;
import com.quickcare.backend.chat.exception.ConversaAcessoNegadoException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversaServiceTest {

    @Mock
    private ChatPropostaRepository chatPropostaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private MensagemRepository mensagemRepository;
    @Mock
    private ArmazenamentoDocumentoService armazenamento;
    @InjectMocks
    private ConversaService service;

    @Test
    void participanteDaConversaPassa() {
        when(chatPropostaRepository.usuarioParticipa(10L, 1L, StatusProposta.ACEITA)).thenReturn(true);

        assertThatCode(() -> service.exigirParticipante(10L, 1L)).doesNotThrowAnyException();
    }

    @Test
    void quemNaoParticipaRecebeAcessoNegado() {
        when(chatPropostaRepository.usuarioParticipa(10L, 99L, StatusProposta.ACEITA)).thenReturn(false);

        assertThatThrownBy(() -> service.exigirParticipante(10L, 99L))
                .isInstanceOf(ConversaAcessoNegadoException.class);
    }

    @Test
    void semIdsNegaAcesso() {
        assertThatThrownBy(() -> service.exigirParticipante(null, 1L))
                .isInstanceOf(ConversaAcessoNegadoException.class);
        assertThatThrownBy(() -> service.exigirParticipante(10L, null))
                .isInstanceOf(ConversaAcessoNegadoException.class);
    }

    @Test
    void participantesTrazPacientePrimeiroSemRepetir() {
        when(chatPropostaRepository.usuarioIdsDoPaciente(10L, StatusProposta.ACEITA)).thenReturn(List.of(1L));
        when(chatPropostaRepository.usuarioIdsDosProfissionais(10L, StatusProposta.ACEITA)).thenReturn(List.of(2L, 3L));

        assertThat(service.participantes(10L)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void listarMontaParticipantesComNomePapelEFoto() {
        Usuario paciente = usuario(1L, "Paciente");
        Usuario medico = usuario(2L, "Medico");
        when(chatPropostaRepository.solicitacaoIdsComChat(1L, StatusProposta.ACEITA)).thenReturn(List.of(10L));
        when(chatPropostaRepository.usuarioIdsDoPaciente(10L, StatusProposta.ACEITA)).thenReturn(List.of(1L));
        when(chatPropostaRepository.usuarioIdsDosProfissionais(10L, StatusProposta.ACEITA)).thenReturn(List.of(2L));
        when(usuarioRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any()))
                .thenReturn(List.of(paciente, medico));
        when(armazenamento.urlDoAvatar(paciente)).thenReturn(null);
        when(armazenamento.urlDoAvatar(medico)).thenReturn("http://foto-medico");

        List<ConversaResponse> conversas = service.listar(1L);

        assertThat(conversas).hasSize(1);
        assertThat(conversas.get(0).solicitacaoId()).isEqualTo(10L);
        assertThat(conversas.get(0).ultimaMensagem()).isNull();
        assertThat(conversas.get(0).participantes())
                .extracting("usuarioId", "nome", "papel", "avatarUrl")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, "Paciente", "PACIENTE", null),
                        org.assertj.core.groups.Tuple.tuple(2L, "Medico", "PROFISSIONAL", "http://foto-medico"));
    }

    @Test
    void listarTrazAUltimaMensagemEOrdenaPelaMaisRecente() {
        Usuario paciente = usuario(1L, "Paciente");
        Usuario medico = usuario(2L, "Medico");
        Mensagem antiga = new Mensagem(2L, 10L, "antiga");
        ReflectionTestUtils.setField(antiga, "dataEnvio", Instant.parse("2026-11-01T10:00:00Z"));
        Mensagem recente = new Mensagem(1L, 11L, "recente");
        ReflectionTestUtils.setField(recente, "dataEnvio", Instant.parse("2026-11-05T10:00:00Z"));

        when(chatPropostaRepository.solicitacaoIdsComChat(1L, StatusProposta.ACEITA)).thenReturn(List.of(10L, 11L, 12L));
        when(chatPropostaRepository.usuarioIdsDoPaciente(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(StatusProposta.ACEITA)))
                .thenReturn(List.of(1L));
        when(chatPropostaRepository.usuarioIdsDosProfissionais(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(StatusProposta.ACEITA)))
                .thenReturn(List.of(2L));
        when(usuarioRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any()))
                .thenReturn(List.of(paciente, medico));
        when(mensagemRepository.findUltimasPorSolicitacao(List.of(10L, 11L, 12L))).thenReturn(List.of(antiga, recente));

        List<ConversaResponse> conversas = service.listar(1L);

        assertThat(conversas).extracting(ConversaResponse::solicitacaoId).containsExactly(11L, 10L, 12L);
        assertThat(conversas.get(0).ultimaMensagem().conteudo()).isEqualTo("recente");
        assertThat(conversas.get(2).ultimaMensagem()).isNull();
    }

    @Test
    void listarSemConversasRetornaVazio() {
        when(chatPropostaRepository.solicitacaoIdsComChat(5L, StatusProposta.ACEITA)).thenReturn(List.of());

        assertThat(service.listar(5L)).isEmpty();
    }

    private Usuario usuario(Long id, String nome) {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(id);
        when(usuario.getNome()).thenReturn(nome);
        return usuario;
    }

}
