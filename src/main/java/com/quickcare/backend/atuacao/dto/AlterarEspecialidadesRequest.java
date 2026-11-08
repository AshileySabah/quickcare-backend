package com.quickcare.backend.atuacao.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AlterarEspecialidadesRequest(
        @NotEmpty List<Long> especialidadeIds
) {
}
