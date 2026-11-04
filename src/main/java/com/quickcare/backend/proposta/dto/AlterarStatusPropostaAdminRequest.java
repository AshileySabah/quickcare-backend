package com.quickcare.backend.proposta.dto;

import com.quickcare.backend.proposta.AcaoAdminProposta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlterarStatusPropostaAdminRequest(
        @NotNull(message = "Escolha o novo status.") AcaoAdminProposta acao,
        @NotBlank(message = "Escreva a justificativa.")
        @Size(max = 1000, message = "A justificativa deve ter no máximo 1000 caracteres.") String justificativa
) {
}
