package com.quickcare.backend.atuacao.dto;

import java.util.List;

public record ImpactoAtuacaoResponse(
        boolean permitido,
        String impedimento,
        List<PropostaAfetadaResponse> propostas
) {
}
