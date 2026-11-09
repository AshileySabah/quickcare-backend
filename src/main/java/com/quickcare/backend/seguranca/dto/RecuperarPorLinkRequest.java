package com.quickcare.backend.seguranca.dto;

import jakarta.validation.constraints.NotBlank;

public record RecuperarPorLinkRequest(
        @NotBlank(message = "O link de recuperação é inválido.") String token
) {
}
