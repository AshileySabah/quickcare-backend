package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.EnviarMensagemRequest;
import com.quickcare.backend.chat.dto.MensagemChatResponse;
import com.quickcare.backend.chat.exception.ConversaAcessoNegadoException;
import com.quickcare.backend.chat.exception.ExclusaoMensagemNaoAutorizadaException;
import com.quickcare.backend.chat.exception.MensagemNaoEncontradaException;
import com.quickcare.backend.chat.exception.MensagemVaziaException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;

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
    void enviaEEntregaATodosMenosORemetente() {
        when(conversaService.participantes(10L)).thenReturn(List.of(1L, 2L, 3L));

        MensagemChatResponse response = service.enviar(2L, new EnviarMensagemRequest(10L, "  oi  "), null);

        assertThat(response.conteudo()).isEqualTo("oi");
        assertThat(response.solicitacaoId()).isEqualTo(10L);
        verify(messagingTemplate).convertAndSendToUser(eq("1"), eq("/queue/mensagens"), any(Object.class));
        verify(messagingTemplate).convertAndSendToUser(eq("3"), eq("/queue/mensagens"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("2"), any(), any(Object.class));
    }

    @Test
    void naoParticipanteNaoEnvia() {
        doThrow(new ConversaAcessoNegadoException()).when(conversaService).exigirParticipante(10L, 9L);

        assertThatThrownBy(() -> service.enviar(9L, new EnviarMensagemRequest(10L, "oi"), null))
                .isInstanceOf(ConversaAcessoNegadoException.class);
        verify(mensagemRepository, never()).save(any());
    }

    @Test
    void semTextoENemAnexoEhRejeitada() {
        assertThatThrownBy(() -> service.enviar(1L, new EnviarMensagemRequest(10L, "   "), null))
                .isInstanceOf(MensagemVaziaException.class);
    }

    @Test
    void anexoSemTextoEhAceito() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.png", "image/png", new byte[]{1});
        when(armazenamento.enviarAnexoChat(arquivo)).thenReturn("chat-anexos/x.png");
        when(armazenamento.urlDoAnexoChat("chat-anexos/x.png")).thenReturn("http://url");
        when(conversaService.participantes(10L)).thenReturn(List.of(1L));

        MensagemChatResponse response = service.enviar(1L, new EnviarMensagemRequest(10L, null), arquivo);

        assertThat(response.anexoUrl()).isEqualTo("http://url");
        assertThat(response.anexoNomeOriginal()).isEqualTo("foto.png");
        assertThat(response.anexoTipo()).isEqualTo("image/png");
    }

    @Test
    void falhaNoWebSocketNaoDesfazOEnvio() {
        when(conversaService.participantes(10L)).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("ws")).when(messagingTemplate)
                .convertAndSendToUser(eq("1"), any(), any(Object.class));

        service.enviar(2L, new EnviarMensagemRequest(10L, "oi"), null);

        verify(messagingTemplate).convertAndSendToUser(eq("3"), any(), any(Object.class));
    }

    @Test
    void listarExigeParticipacao() {
        doThrow(new ConversaAcessoNegadoException()).when(conversaService).exigirParticipante(10L, 9L);

        assertThatThrownBy(() -> service.listarPorConversa(9L, 10L))
                .isInstanceOf(ConversaAcessoNegadoException.class);
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
    void remetenteExcluiENotificaOsOutros() {
        Mensagem mensagem = new Mensagem(1L, 10L, "oi");
        when(mensagemRepository.findById(5L)).thenReturn(Optional.of(mensagem));
        when(conversaService.participantes(10L)).thenReturn(List.of(1L, 2L));

        service.excluir(1L, 5L);

        assertThat(mensagem.isAtivo()).isFalse();
        verify(messagingTemplate).convertAndSendToUser(eq("2"), eq("/queue/mensagens"), any(Object.class));
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

}
