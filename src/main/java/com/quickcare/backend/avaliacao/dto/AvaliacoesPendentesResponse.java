package com.quickcare.backend.avaliacao.dto;

import java.time.Instant;
import java.util.List;

public record AvaliacoesPendentesResponse(List<Pendente> pendentes, List<DestaqueResponse> destaques) {

    public record Pendente(
            Long atendimentoId,
            Long solicitacaoId,
            String servico,
            String nomeAvaliado,
            Instant inicio,
            Instant termino,
            Instant prazo
    ) {
    }

}
