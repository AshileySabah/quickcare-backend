package com.quickcare.backend.cadastro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EspecialidadeRegistroRequest(
        @NotNull Long especialidadeId,
        @NotBlank String registroProfissional
) {
}
