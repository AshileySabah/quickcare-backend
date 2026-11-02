package com.quickcare.backend.bloqueiousuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarBloqueioUsuarioRequest(
        @NotNull(message = "Escolha o paciente.") Long pacienteUsuarioId,
        @NotNull(message = "Escolha o profissional.") Long profissionalUsuarioId,
        @NotBlank(message = "Escreva a justificativa.")
        @Size(max = 1000, message = "A justificativa deve ter no máximo 1000 caracteres.") String justificativa
) {
}
