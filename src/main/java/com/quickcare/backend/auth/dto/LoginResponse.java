package com.quickcare.backend.auth.dto;

import java.util.List;

public record LoginResponse(
        Long usuarioId,
        String nome,
        String email,
        List<PerfilStatusResponse> perfis
) {
}
