package com.quickcare.backend.chat.dto;

import com.quickcare.backend.chat.Mensagem;

import java.time.Instant;

public record MensagemChatResponse(
        Long id,
        Long remetenteId,
        Long destinatarioId,
        String conteudo,
        Instant dataEnvio,
        String anexoUrl,
        String anexoNomeOriginal,
        String anexoTipo
) {

    /**
     * A URL do anexo é assinada e temporária, então é gerada pelo service a cada
     * leitura e apenas repassada aqui.
     */
    public static MensagemChatResponse de(Mensagem mensagem, String anexoUrl) {
        return new MensagemChatResponse(
                mensagem.getId(),
                mensagem.getRemetenteId(),
                mensagem.getDestinatarioId(),
                mensagem.getConteudo(),
                mensagem.getDataEnvio(),
                anexoUrl,
                mensagem.getAnexoNomeOriginal(),
                mensagem.getAnexoTipo()
        );
    }

}
