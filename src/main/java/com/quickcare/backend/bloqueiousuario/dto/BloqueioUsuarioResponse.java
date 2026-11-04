package com.quickcare.backend.bloqueiousuario.dto;

import java.time.Instant;

public record BloqueioUsuarioResponse(
        Long id,
        Long perfilId,
        String nome,
        String avatarUrl,
        Instant criadoEm
) {
}
