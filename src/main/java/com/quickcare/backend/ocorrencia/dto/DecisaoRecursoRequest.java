package com.quickcare.backend.ocorrencia.dto;

import com.quickcare.backend.denuncia.ConsequenciaDenuncia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisaoRecursoRequest(
        @NotNull(message = "Informe se a ocorrência será anulada.") Boolean anular,
        @NotBlank(message = "Escreva a justificativa.")
        @Size(max = 1000, message = "A justificativa deve ter no máximo 1000 caracteres.") String justificativa,
        ConsequenciaDenuncia consequenciaSeNegado
) {
}
