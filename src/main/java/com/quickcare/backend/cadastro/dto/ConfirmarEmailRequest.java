package com.quickcare.backend.cadastro.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmarEmailRequest(
        @NotBlank(message = "O link de confirmação é inválido.") String token
) {
}
