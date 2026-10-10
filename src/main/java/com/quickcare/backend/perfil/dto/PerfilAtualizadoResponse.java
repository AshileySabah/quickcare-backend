package com.quickcare.backend.perfil.dto;

public record PerfilAtualizadoResponse<T>(
        String mensagem,
        T perfil
) {
}
