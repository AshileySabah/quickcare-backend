package com.quickcare.backend.auth.dto;

public record RedefinirSenhaResponse(
        String mensagem,
        LoginResponse usuario
) {
}
