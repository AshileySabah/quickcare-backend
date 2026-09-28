package com.quickcare.backend.solicitacao.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** O profissional precisa atender todas as {@code especialidadeIds}, que devem ser da {@code categoria}. */
public record ProfissionalSolicitadoRequest(
        @NotNull CategoriaProfissional categoria,
        @NotEmpty Set<@NotNull Long> especialidadeIds,
        @NotNull @Min(1) @Max(10) Integer quantidade
) {
}
