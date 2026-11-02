package com.quickcare.backend.atendimento.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RemarcacaoRequest(@NotNull Instant inicio, @NotNull Instant termino) {
}
