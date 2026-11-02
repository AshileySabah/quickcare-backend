package com.quickcare.backend.problema.dto;

import com.quickcare.backend.problema.MotivoProblema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "Não foi realizado": por quê e o que aconteceu (os anexos vão em outra parte do multipart). */
public record RelatoRequest(
        @NotNull(message = "Informe o que aconteceu.") MotivoProblema motivo,
        @NotBlank(message = "Descreva o que aconteceu.")
        @Size(min = 10, max = 2000, message = "A descrição deve ter entre 10 e 2000 caracteres.") String descricao
) {
}
