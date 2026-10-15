package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.validacao.StatusEtapa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Justificativa é obrigatória tanto para aprovar quanto para reprovar. */
public record DecisaoEtapaRequest(
        @NotNull StatusEtapa decisao,
        @NotBlank @Size(min = 10, max = 1000) String justificativa
) {
}
