package com.quickcare.backend.chat.dto;

/**
 * Aviso enviado via WebSocket aos demais participantes quando o remetente apaga uma mensagem.
 * Serializa como {@code {"id": 1, "excluida": true}}.
 */
public record MensagemExcluidaEvento(Long id, boolean excluida) {

    public static MensagemExcluidaEvento de(Long id) {
        return new MensagemExcluidaEvento(id, true);
    }

}
