package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.EnviarMensagemRequest;
import com.quickcare.backend.chat.dto.MensagemChatResponse;
import com.quickcare.backend.chat.exception.ConversaAcessoNegadoException;
import com.quickcare.backend.chat.exception.ConversaSomenteLeituraException;
import com.quickcare.backend.chat.exception.ExclusaoMensagemNaoAutorizadaException;
import com.quickcare.backend.chat.exception.MensagemNaoEncontradaException;
import com.quickcare.backend.chat.exception.MensagemVaziaException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensagemServiceTest {

    @Mock
    private MensagemRepository mensagemRepository;
    @Mock
    private ConversaService conversaService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ArmazenamentoDocumentoService armazenamento;
    @InjectMocks
    private MensagemService service;

    @BeforeEach
    void salvarDevolveOMesmoObjeto() {
        org.mockito.Mockito.lenient().when(mensagemRepository.save(any(Mensagem.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void enviaNoGrupoEEntregaATodosMenosORemetente() {
        when(conversaService.destinatarios(10L, null)).thenReturn(List.of(1L, 2L, 3L));

        MensagemChatResponse response = service.enviar(2L, new EnviarMensagemRequest(10L, null, "  oi  "), null);

        assertThat(response.conteudo()).isEqualTo("oi");
        assertThat(response.solicitacaoId()).isEqualTo(10L);
        assertThat(response.profissionalUsuarioId()).isNull();
        verify(messagingTemplate).convertAndSendToUser(eq("1"), eq("/queue/mensagens"), any(Object.class));
        verify(messagingTemplate).convertAndSendToUser(eq("3"), eq("/queue/mensagens"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("2"), any(), any(Object.class));
    }

    @Test
    void enviaNaConversaIndividualSoParaOPacienteEOProfissionalDoCanal() {
        when(conversaService.destinatarios(10L, 2L)).thenReturn(List.of(1L, 2L));

        MensagemChatResponse response = service.enviar(1L, new EnviarMensagemRequest(10L, 2L, "oi"), null);

        ArgumentCaptor<Mensagem> salva = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository).save(salva.capture());
        assertThat(salva.getValue().getProfissionalUsuarioId()).isEqualTo(2L);
        assertThat(response.profissionalUsuarioId()).isEqualTo(2L);
        verify(conversaService).exigirPodeEscrever(10L, 2L, 1L);
        verify(messagingTemplate).convertAndSendToUser(eq("2"), eq("/queue/mensagens"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("1"), any(), any(Object.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("3"), any(), any(Object.class));
    }

    @Test
    void naoParticipanteNaoEnvia() {
        doThrow(new ConversaAcessoNegadoException()).when(conversaService).exigirPodeEscrever(10L, null, 9L);

        assertThatThrownBy(() -> service.enviar(9L, new EnviarMensagemRequest(10L, null, "oi"), null))
                .isInstanceOf(ConversaAcessoNegadoException.class);
        verify(mensagemRepository, never()).save(any());
    }

    @Test
    void quemSaiuDaConversaNaoEnvia() {
        doThrow(new ConversaSomenteLeituraException()).when(conversaService).exigirPodeEscrever(10L, 2L, 2L);

        assertThatThrownBy(() -> service.enviar(2L, new EnviarMensagemRequest(10L, 2L, "oi"), null))
                .isInstanceOf(ConversaSomenteLeituraException.class);
        verify(mensagemRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any(Object.class));
    }

    @Test
    void semTextoENemAnexoEhRejeitada() {
        assertThatThrownBy(() -> service.enviar(1L, new EnviarMensagemRequest(10L, null, "   "), null))
                .isInstanceOf(MensagemVaziaException.class);
    }

    @Test
    void anexoSemTextoEhAceito() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.png", "image/png", new byte[]{1});
        when(armazenamento.enviarAnexoChat(arquivo)).thenReturn("chat-anexos/x.png");
        when(armazenamento.urlDoAnexoChat("chat-anexos/x.png")).thenReturn("http://url");
        when(conversaService.destinatarios(10L, 2L)).thenReturn(List.of(1L, 2L));

        MensagemChatResponse response = service.enviar(1L, new EnviarMensagemRequest(10L, 2L, null), arquivo);

        assertThat(response.anexoUrl()).isEqualTo("http://url");
        assertThat(response.anexoNomeOriginal()).isEqualTo("foto.png");
        assertThat(response.anexoTipo()).isEqualTo("image/png");
    }

    @Test
    void falhaNoWebSocketNaoDesfazOEnvio() {
        when(conversaService.destinatarios(10L, null)).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("ws")).when(messagingTemplate)
                .convertAndSendToUser(eq("1"), any(), any(Object.class));

        service.enviar(2L, new EnviarMensagemRequest(10L, null, "oi"), null);

        verify(messagingTemplate).convertAndSendToUser(eq("3"), any(), any(Object.class));
    }

    @Test
    void listarExigeParticipacao() {
        when(conversaService.acessoDe(10L, null, 9L)).thenThrow(new ConversaAcessoNegadoException());

        assertThatThrownBy(() -> service.listarPorConversa(9L, 10L, null))
                .isInstanceOf(ConversaAcessoNegadoException.class);
    }

    @Test
    void listarMostraTudoParaQuemAindaEstaNaConversa() {
        when(conversaService.acessoDe(10L, 2L, 1L)).thenReturn(new AcessoAoCanal(true, null, null));
        when(mensagemRepository.listarDoCanal(10L, 2L)).thenReturn(List.of(
                mensagemEm(2L, "a", "2026-11-01T10:00:00Z"), mensagemEm(1L, "b", "2026-11-03T10:00:00Z")));

        assertThat(service.listarPorConversa(1L, 10L, 2L)).extracting(MensagemChatResponse::conteudo)
                .containsExactly("a", "b");
    }

    @Test
    void profissionalQueSaiuSoVeOQueFoiEscritoAteASaida() {
        Instant saiu = Instant.parse("2026-11-02T10:00:00Z");
        when(conversaService.acessoDe(10L, 2L, 2L)).thenReturn(new AcessoAoCanal(false, null, saiu));
        when(mensagemRepository.listarDoCanal(10L, 2L)).thenReturn(List.of(
                mensagemEm(1L, "antes", "2026-11-01T10:00:00Z"),
                mensagemEm(2L, "no limite", "2026-11-02T10:00:00Z"),
                mensagemEm(1L, "depois", "2026-11-03T10:00:00Z")));

        assertThat(service.listarPorConversa(2L, 10L, 2L)).extracting(MensagemChatResponse::conteudo)
                .containsExactly("antes", "no limite");
    }

    @Test
    void profissionalNoGrupoSoVeDesdeQueEntrou() {
        Instant entrou = Instant.parse("2026-11-02T00:00:00Z");
        when(conversaService.acessoDe(10L, null, 3L)).thenReturn(new AcessoAoCanal(true, entrou, null));
        when(mensagemRepository.listarDoCanal(10L, null)).thenReturn(List.of(
                mensagemEm(1L, "antes de entrar", "2026-11-01T10:00:00Z"),
                mensagemEm(1L, "depois", "2026-11-03T10:00:00Z")));

        assertThat(service.listarPorConversa(3L, 10L, null)).extracting(MensagemChatResponse::conteudo)
                .containsExactly("depois");
    }

    @Test
    void soORemetenteExclui() {
        Mensagem mensagem = new Mensagem(1L, 10L, "oi");
        when(mensagemRepository.findById(5L)).thenReturn(Optional.of(mensagem));

        assertThatThrownBy(() -> service.excluir(2L, 5L))
                .isInstanceOf(ExclusaoMensagemNaoAutorizadaException.class);
        assertThat(mensagem.isAtivo()).isTrue();
    }

    @Test
    void remetenteExcluiENotificaOsOutrosDoCanal() {
        Mensagem mensagem = new Mensagem(1L, 10L, 2L, "oi");
        when(mensagemRepository.findById(5L)).thenReturn(Optional.of(mensagem));
        when(conversaService.destinatarios(10L, 2L)).thenReturn(List.of(1L, 2L));

        service.excluir(1L, 5L);

        assertThat(mensagem.isAtivo()).isFalse();
        verify(messagingTemplate).convertAndSendToUser(eq("2"), eq("/queue/mensagens"), any(Object.class));
    }

    @Test
    void quemSaiuDaConversaNaoExcluiMensagem() {
        Mensagem mensagem = new Mensagem(2L, 10L, 2L, "oi");
        when(mensagemRepository.findById(5L)).thenReturn(Optional.of(mensagem));
        doThrow(new ConversaSomenteLeituraException()).when(conversaService).exigirPodeEscrever(10L, 2L, 2L);

        assertThatThrownBy(() -> service.excluir(2L, 5L)).isInstanceOf(ConversaSomenteLeituraException.class);
        assertThat(mensagem.isAtivo()).isTrue();
    }

    @Test
    void excluirMensagemInexistenteOuJaExcluida() {
        Mensagem excluida = new Mensagem(1L, 10L, "oi");
        excluida.excluir();
        when(mensagemRepository.findById(5L)).thenReturn(Optional.of(excluida));
        when(mensagemRepository.findById(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(1L, 5L)).isInstanceOf(MensagemNaoEncontradaException.class);
        assertThatThrownBy(() -> service.excluir(1L, 6L)).isInstanceOf(MensagemNaoEncontradaException.class);
    }

    private Mensagem mensagemEm(Long remetenteId, String conteudo, String dataEnvio) {
        Mensagem mensagem = new Mensagem(remetenteId, 10L, conteudo);
        ReflectionTestUtils.setField(mensagem, "dataEnvio", Instant.parse(dataEnvio));
        return mensagem;
    }

}
