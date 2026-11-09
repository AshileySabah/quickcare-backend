package com.quickcare.backend.atuacao.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AtuacaoRequest(
        @NotNull(message = "Escolha a categoria da atuação.") CategoriaProfissional categoria,
        @NotEmpty(message = "Escolha pelo menos uma especialidade.") List<Long> especialidadeIds,
        @Size(max = 50, message = "O número do registro deve ter no máximo 50 caracteres.") String registroProfissional,
        @Pattern(regexp = "(?i)^[a-z]{2}$", message = "Escolha a UF do registro profissional.") String registroProfissionalUf
) {
}
