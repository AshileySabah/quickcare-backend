package com.quickcare.backend.chat.dto;

/** {@code papel} é PACIENTE ou PROFISSIONAL. */
public record ParticipanteResponse(Long usuarioId, String nome, String papel) {
}
