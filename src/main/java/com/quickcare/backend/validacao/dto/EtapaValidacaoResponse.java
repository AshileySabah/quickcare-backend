package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.atuacao.dto.AtuacaoResumoResponse;
import com.quickcare.backend.validacao.StatusEtapa;
import com.quickcare.backend.validacao.TipoEtapaValidacao;

import java.time.Instant;
import java.util.List;

public record EtapaValidacaoResponse(
        Long id,
        TipoEtapaValidacao tipo,
        String descricao,
        AtuacaoResumoResponse atuacao,
        StatusEtapa status,
        String justificativa,
        ArquivoResponse documento,
        ArquivoResponse anexo,
        String avaliadoPor,
        Instant avaliadoEm,
        Instant criadoEm,
        List<EtapaValidacaoResponse> historico
) {
}
