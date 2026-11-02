package com.quickcare.backend.administrador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Toda ação do admin fora da validação pede justificativa. */
public record JustificativaRequest(
        @NotBlank(message = "Escreva a justificativa.")
        @Size(max = 1000, message = "A justificativa deve ter no máximo 1000 caracteres.") String justificativa
) {
}
