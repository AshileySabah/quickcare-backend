package com.quickcare.backend.seguranca.dto;

import java.util.List;

public record SegurancaResponse(
        String email,
        List<PerfilSegurancaResponse> perfis
) {
}
