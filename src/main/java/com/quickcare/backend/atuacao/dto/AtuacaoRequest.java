package com.quickcare.backend.atuacao.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AtuacaoRequest(
        @NotNull CategoriaProfissional categoria,
        @NotEmpty List<Long> especialidadeIds,
        @Size(max = 50) String registroProfissional,
        @Pattern(regexp = "(?i)^[a-z]{2}$") String registroProfissionalUf
) {
}
