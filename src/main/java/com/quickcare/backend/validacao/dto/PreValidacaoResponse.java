package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.validacao.TipoEtapaValidacao;
import com.quickcare.backend.validacao.externa.Parecer;
import com.quickcare.backend.validacao.externa.VerificacaoExterna;

import java.time.Instant;
import java.util.List;

public record PreValidacaoResponse(
        TipoEtapaValidacao tipo,
        Parecer parecer,
        List<VerificacaoExterna> verificacoes,
        String sugestaoJustificativa,
        Instant consultadoEm
) {
}
