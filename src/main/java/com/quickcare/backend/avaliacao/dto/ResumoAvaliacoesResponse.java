package com.quickcare.backend.avaliacao.dto;

import com.quickcare.backend.avaliacao.DestaqueAvaliacao;

import java.util.List;

public record ResumoAvaliacoesResponse(Double media, long total, List<DestaqueRecebido> destaques) {

    public record DestaqueRecebido(DestaqueAvaliacao codigo, String rotulo, boolean positivo, long total) {
    }

}
