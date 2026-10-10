package com.quickcare.backend.chat.dto;

import com.quickcare.backend.chat.Mensagem;

import java.time.Instant;

public record MensagemChatResponse(
        Long id,
        Long remetenteId,
        Long destinatarioId,
        String conteudo,
        Instant dataEnvio
) {

    public static MensagemChatResponse de(Mensagem mensagem) {
        return new MensagemChatResponse(
                mensagem.getId(),
                mensagem.getRemetenteId(),
                mensagem.getDestinatarioId(),
                mensagem.getConteudo(),
                mensagem.getDataEnvio()
        );
    }

}
