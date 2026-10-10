package com.quickcare.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EnviarMensagemRequest(
        @NotNull Long destinatarioId,
        @NotBlank String conteudo
) {
}
