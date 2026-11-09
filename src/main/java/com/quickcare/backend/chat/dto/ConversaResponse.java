package com.quickcare.backend.chat.dto;

import java.util.List;

/**
 * Um canal de conversa de uma solicitação. {@code tipo} é GRUPO ou INDIVIDUAL; no individual,
 * {@code profissionalUsuarioId} é o profissional do canal (nulo no grupo). O paciente vem sempre
 * primeiro nos participantes. {@code somenteLeitura} vale para quem pediu a lista: o usuário saiu
 * da conversa e só vê o histórico. {@code ultimaMensagem} é nula enquanto ninguém escreveu.
 */
public record ConversaResponse(
        Long solicitacaoId,
        String tipo,
        Long profissionalUsuarioId,
        List<ParticipanteResponse> participantes,
        boolean somenteLeitura,
        UltimaMensagemResponse ultimaMensagem) {
}
