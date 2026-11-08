package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.validacao.StatusEtapa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisaoEtapaRequest(
        @NotNull(message = "Escolha aprovar ou reprovar.") StatusEtapa decisao,
        @NotBlank(message = "Escreva a justificativa.")
        @Size(min = 10, max = 1000, message = "A justificativa deve ter entre 10 e 1000 caracteres.") String justificativa
) {
}
