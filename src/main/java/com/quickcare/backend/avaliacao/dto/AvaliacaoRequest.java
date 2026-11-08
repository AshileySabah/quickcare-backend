package com.quickcare.backend.avaliacao.dto;

import com.quickcare.backend.avaliacao.DestaqueAvaliacao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record AvaliacaoRequest(
        @NotNull(message = "Escolha de 1 a 5 estrelas.")
        @Min(value = 1, message = "Escolha de 1 a 5 estrelas.")
        @Max(value = 5, message = "Escolha de 1 a 5 estrelas.")
        Integer nota,
        Set<DestaqueAvaliacao> destaques
) {
}
