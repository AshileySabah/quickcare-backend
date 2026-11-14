package com.quickcare.backend.chat.dto;

/**
 * {@code papel} é PACIENTE ou PROFISSIONAL. {@code avatarUrl} é nulo quando não há foto.
 * {@code ativo} é falso para o profissional que saiu da conversa.
 */
public record ParticipanteResponse(Long usuarioId, Long perfilId, String nome, String papel, String avatarUrl, boolean ativo) {
}
