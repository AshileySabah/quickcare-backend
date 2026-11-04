package com.quickcare.backend.solicitacao.dto;

import com.quickcare.backend.solicitacao.AcaoAdminSolicitacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlterarStatusAdminRequest(
        @NotNull(message = "Escolha o novo status.") AcaoAdminSolicitacao acao,
        @NotBlank(message = "Escreva a justificativa.")
        @Size(max = 1000, message = "A justificativa deve ter no máximo 1000 caracteres.") String justificativa
) {
}
