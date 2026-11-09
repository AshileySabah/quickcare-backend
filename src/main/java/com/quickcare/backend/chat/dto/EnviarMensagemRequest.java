package com.quickcare.backend.chat.dto;

import jakarta.validation.constraints.NotNull;

/**
 * {@code profissionalUsuarioId} escolhe o canal: preenchido é a conversa individual com esse
 * profissional; nulo é o grupo. O conteúdo é opcional quando a mensagem traz um anexo; a regra
 * "texto ou anexo" é validada no MensagemService, que é quem enxerga o arquivo.
 */
public record EnviarMensagemRequest(
        @NotNull Long solicitacaoId,
        Long profissionalUsuarioId,
        String conteudo) {
}
