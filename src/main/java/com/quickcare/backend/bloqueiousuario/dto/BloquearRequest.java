package com.quickcare.backend.bloqueiousuario.dto;

import jakarta.validation.constraints.Size;

public record BloquearRequest(
        @Size(max = 1000, message = "O motivo deve ter no máximo 1000 caracteres.") String motivo
) {

    public static String motivoDe(BloquearRequest request) {
        return request == null ? null : request.motivo();
    }
}
