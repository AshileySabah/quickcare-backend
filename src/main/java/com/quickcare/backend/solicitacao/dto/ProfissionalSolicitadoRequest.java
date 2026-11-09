package com.quickcare.backend.solicitacao.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ProfissionalSolicitadoRequest(
        @NotNull(message = "Escolha a categoria do profissional.") CategoriaProfissional categoria,
        @NotEmpty(message = "Escolha pelo menos uma especialidade.")
        Set<@NotNull(message = "Escolha pelo menos uma especialidade.") Long> especialidadeIds,
        @NotNull(message = "Informe quantos profissionais você precisa, de 1 a 10.")
        @Min(value = 1, message = "Informe quantos profissionais você precisa, de 1 a 10.")
        @Max(value = 10, message = "Informe quantos profissionais você precisa, de 1 a 10.") Integer quantidade
) {
}
