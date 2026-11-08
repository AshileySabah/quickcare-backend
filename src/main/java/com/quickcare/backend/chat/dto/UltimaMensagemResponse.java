package com.quickcare.backend.chat.dto;

import com.quickcare.backend.chat.Mensagem;

import java.time.Instant;

/** Resumo da última mensagem da conversa, para a prévia na lista. */
public record UltimaMensagemResponse(
        Long remetenteId,
        String conteudo,
        String anexoNomeOriginal,
        String anexoTipo,
        Instant dataEnvio) {

    public static UltimaMensagemResponse de(Mensagem mensagem) {
        return new UltimaMensagemResponse(
                mensagem.getRemetenteId(),
                mensagem.getConteudo(),
                mensagem.getAnexoNomeOriginal(),
                mensagem.getAnexoTipo(),
                mensagem.getDataEnvio());
    }

}
