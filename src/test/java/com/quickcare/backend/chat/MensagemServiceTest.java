package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.EnviarMensagemRequest;
import com.quickcare.backend.chat.dto.MensagemChatResponse;
import com.quickcare.backend.chat.dto.MensagemExcluidaEvento;
import com.quickcare.backend.chat.exception.DestinatarioInvalidoException;
import com.quickcare.backend.chat.exception.ExclusaoMensagemNaoAutorizadaException;
import com.quickcare.backend.chat.exception.MensagemNaoEncontradaException;
import com.quickcare.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensagemServiceTest {

    @Mock
    private MensagemRepository mensagemRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MensagemService service;

    @Test
    void deveSalvarMensagemEEntregarSoAoDestinatario() {
        when(usuarioRepository.existsById(2L)).thenReturn(true);
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(inv -> inv.getArgument(0));

        MensagemChatResponse response = service.enviar(1L, new EnviarMensagemRequest(2L, "Olá"));

        assertThat(response.remetenteId()).isEqualTo(1L);
        assertThat(response.destinatarioId()).isEqualTo(2L);
        assertThat(response.conteudo()).isEqualTo("Olá");
        verify(messagingTemplate).convertAndSendToUser(
                eq("2"), eq(MensagemService.FILA_MENSAGENS), any(MensagemChatResponse.class));
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void deveRejeitarEnvioParaSiMesmo() {
        assertThatThrownBy(() -> service.enviar(1L, new EnviarMensagemRequest(1L, "Olá")))
                .isInstanceOf(DestinatarioInvalidoException.class);

        verify(mensagemRepository, never()).save(any());
    }

    @Test
    void deveRejeitarDestinatarioInexistente() {
        when(usuarioRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.enviar(1L, new EnviarMensagemRequest(99L, "Olá")))
                .isInstanceOf(DestinatarioInvalidoException.class);

        verify(mensagemRepository, never()).save(any());
    }

    @Test
    void deveListarMensagensDoUsuario() {
        when(mensagemRepository.findMensagensAtivasPorUsuario(1L))
                .thenReturn(List.of(new Mensagem(1L, 2L, "Oi"), new Mensagem(2L, 1L, "Olá")));

        List<MensagemChatResponse> resultado = service.listar(1L);

        assertThat(resultado).extracting(MensagemChatResponse::conteudo).containsExactly("Oi", "Olá");
    }

    @Test
    void deveExcluirLogicamenteENotificarDestinatario() {
        Mensagem mensagem = new Mensagem(1L, 2L, "Oi");
        when(mensagemRepository.findById(10L)).thenReturn(Optional.of(mensagem));

        service.excluir(1L, 10L);

        assertThat(mensagem.isAtivo()).isFalse();
        verify(mensagemRepository, never()).delete(any());
        verify(mensagemRepository, never()).deleteById(any());
        verify(messagingTemplate).convertAndSendToUser(
                eq("2"), eq(MensagemService.FILA_MENSAGENS), any(MensagemExcluidaEvento.class));
    }

    @Test
    void deveRejeitarExclusaoPorQuemNaoEhORemetente() {
        Mensagem mensagem = new Mensagem(1L, 2L, "Oi");
        when(mensagemRepository.findById(10L)).thenReturn(Optional.of(mensagem));

        assertThatThrownBy(() -> service.excluir(2L, 10L))
                .isInstanceOf(ExclusaoMensagemNaoAutorizadaException.class);

        assertThat(mensagem.isAtivo()).isTrue();
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void deveRejeitarExclusaoDeMensagemInexistente() {
        when(mensagemRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(1L, 10L))
                .isInstanceOf(MensagemNaoEncontradaException.class);
    }

    @Test
    void deveTratarMensagemJaExcluidaComoNaoEncontrada() {
        Mensagem mensagem = new Mensagem(1L, 2L, "Oi");
        mensagem.excluir();
        when(mensagemRepository.findById(10L)).thenReturn(Optional.of(mensagem));

        assertThatThrownBy(() -> service.excluir(1L, 10L))
                .isInstanceOf(MensagemNaoEncontradaException.class);
    }
}