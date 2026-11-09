package com.quickcare.backend.chat.dto;

import com.quickcare.backend.chat.Mensagem;

import java.time.Instant;

public record MensagemChatResponse(
        Long id,
        Long remetenteId,
        Long solicitacaoId,
        Long profissionalUsuarioId,
        String conteudo,
        Instant dataEnvio,
        String anexoUrl,
        String anexoNomeOriginal,
        String anexoTipo) {

    /**
     * A URL do anexo é assinada e temporária, então é gerada pelo service a cada leitura e apenas
     * repassada aqui. {@code profissionalUsuarioId} nulo indica mensagem do grupo.
     */
    public static MensagemChatResponse de(Mensagem mensagem, String anexoUrl) {
        return new MensagemChatResponse(
                mensagem.getId(),
                mensagem.getRemetenteId(),
                mensagem.getSolicitacaoId(),
                mensagem.getProfissionalUsuarioId(),
                mensagem.getConteudo(),
                mensagem.getDataEnvio(),
                anexoUrl,
                mensagem.getAnexoNomeOriginal(),
                mensagem.getAnexoTipo());
    }

}
