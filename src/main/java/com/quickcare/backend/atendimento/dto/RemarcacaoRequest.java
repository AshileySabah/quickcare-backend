package com.quickcare.backend.atendimento.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RemarcacaoRequest(
        @NotNull(message = "Informe o novo início.") Instant inicio,
        @NotNull(message = "Informe o novo término.") Instant termino
) {
}
