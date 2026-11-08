package com.quickcare.backend.chat.dto;

/** {@code papel} é PACIENTE ou PROFISSIONAL. {@code avatarUrl} é nulo quando não há foto. */
public record ParticipanteResponse(Long usuarioId, String nome, String papel, String avatarUrl) {
}
