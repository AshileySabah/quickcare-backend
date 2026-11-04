package com.quickcare.backend.validacao.dto;

import java.time.Instant;

public record ArquivoResponse(
        String nomeArquivo,
        String contentType,
        Long tamanhoBytes,
        Instant enviadoEm,
        String url
) {
}
