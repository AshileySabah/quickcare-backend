package com.quickcare.backend.chat.dto;

import java.util.List;

public record NaoLidasChatResponse(long total, List<Canal> canais) {

    public record Canal(Long solicitacaoId, Long profissionalUsuarioId, long quantidade) {
    }

}
