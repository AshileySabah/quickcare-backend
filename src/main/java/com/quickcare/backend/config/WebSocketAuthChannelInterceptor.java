package com.quickcare.backend.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

/**
 * Garante que apenas sessões autenticadas no handshake (cookie JWT) conectem e
 * assinem, e só em destinos do próprio usuário ({@code /user/...}). Assinar
 * {@code /queue/**} ou {@code /topic/**} diretamente é bloqueado para que ninguém
 * leia a fila de outro usuário.
 */
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String PREFIXO_DESTINO_USUARIO = "/user/";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if ((command == StompCommand.CONNECT || command == StompCommand.SUBSCRIBE || command == StompCommand.SEND)
                && accessor.getUser() == null) {
            throw new AccessDeniedException("Sessão não autenticada.");
        }

        if (command == StompCommand.SUBSCRIBE) {
            String destino = accessor.getDestination();
            if (destino == null || !destino.startsWith(PREFIXO_DESTINO_USUARIO)) {
                throw new AccessDeniedException("Destino de assinatura não permitido.");
            }
        }

        return message;
    }

}
