package com.quickcare.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ValidarTokenRedefinicaoRequest(
        @NotBlank(message = "O link de redefinição é inválido. Peça um novo.") String token
) {
}
