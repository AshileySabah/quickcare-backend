package com.quickcare.backend.auth.dto;

import com.quickcare.backend.validacao.StatusValidacao;

public record PerfilStatusResponse(
        String tipo,
        String status,
        StatusValidacao statusValidacao
) {
}
