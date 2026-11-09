package com.quickcare.backend.banimento.dto;

import com.quickcare.backend.banimento.TipoBanimento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BanimentoRequest(
        @NotNull(message = "Escolha se o banimento é permanente ou uma suspensão.") TipoBanimento tipo,
        Integer dias,
        boolean ambosPerfis,
        @NotBlank(message = "Escreva a justificativa.")
        @Size(max = 1000, message = "A justificativa deve ter no máximo 1000 caracteres.") String justificativa
) {
}
