package com.quickcare.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ValidarTokenRedefinicaoRequest(
        @NotBlank String token
) {
}
