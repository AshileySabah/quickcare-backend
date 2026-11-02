package com.quickcare.backend.problema.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContestacaoRequest(
        @NotBlank(message = "Conte a sua versão do que aconteceu.")
        @Size(min = 10, max = 2000, message = "A descrição deve ter entre 10 e 2000 caracteres.") String descricao
) {
}
