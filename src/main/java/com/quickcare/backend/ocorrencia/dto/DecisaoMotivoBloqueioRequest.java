package com.quickcare.backend.ocorrencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisaoMotivoBloqueioRequest(
        @NotNull(message = "Informe se o motivo foi aceito.") Boolean aceitar,
        @NotBlank(message = "Escreva a justificativa.")
        @Size(max = 1000, message = "A justificativa deve ter no máximo 1000 caracteres.") String justificativa
) {
}
