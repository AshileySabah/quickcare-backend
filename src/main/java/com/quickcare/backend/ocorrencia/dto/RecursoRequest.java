package com.quickcare.backend.ocorrencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecursoRequest(
        @NotBlank(message = "Explique por que a ocorrência não deveria contar.")
        @Size(max = 1000, message = "O recurso deve ter no máximo 1000 caracteres.") String texto
) {
}
