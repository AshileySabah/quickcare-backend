package com.quickcare.backend.chat.dto;

import java.util.List;

/**
 * Uma conversa por solicitação; o paciente vem sempre primeiro na lista de participantes.
 * {@code ultimaMensagem} é nula enquanto ninguém escreveu.
 */
public record ConversaResponse(
        Long solicitacaoId,
        List<ParticipanteResponse> participantes,
        UltimaMensagemResponse ultimaMensagem) {
}
