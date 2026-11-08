package com.quickcare.backend.chat.dto;

import jakarta.validation.constraints.NotNull;

/**
 * O conteúdo é opcional quando a mensagem traz um anexo; a regra "texto ou anexo" é validada no
 * MensagemService, que é quem enxerga o arquivo.
 */
public record EnviarMensagemRequest(
        @NotNull Long solicitacaoId,
        String conteudo) {
}
