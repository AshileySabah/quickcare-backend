package com.quickcare.backend.bloqueiousuario.dto;

import java.time.Instant;

/** Alguém que eu bloqueei; {@code perfilId} é o id do perfil dele (profissional ou paciente). */
public record BloqueioUsuarioResponse(
        Long id,
        Long perfilId,
        String nome,
        String avatarUrl,
        Instant criadoEm
) {
}
