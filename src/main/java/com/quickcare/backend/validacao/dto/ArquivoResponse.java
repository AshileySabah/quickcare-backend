package com.quickcare.backend.validacao.dto;

import java.time.Instant;

/** Documento enviado pelo usuário ou anexo do admin, com URL assinada temporária. */
public record ArquivoResponse(
        String nomeArquivo,
        String contentType,
        Long tamanhoBytes,
        Instant enviadoEm,
        String url
) {
}
