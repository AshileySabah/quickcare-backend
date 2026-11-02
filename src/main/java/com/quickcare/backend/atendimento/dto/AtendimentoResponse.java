package com.quickcare.backend.atendimento.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.atendimento.MotivoCancelamento;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.ocorrencia.RegrasOcorrencia;

import java.time.Instant;

/**
 * O atendimento como os dois lados veem. {@code cancelamentoContaOcorrencia}
 * e {@code alteracaoTardia} deixam o front avisar antes de cancelar ou editar.
 */
public record AtendimentoResponse(
        Long id,
        StatusAtendimento status,
        Instant inicio,
        Instant termino,
        Instant aceitoEm,
        @JsonInclude(JsonInclude.Include.NON_NULL) Remarcacao remarcacao,
        @JsonInclude(JsonInclude.Include.NON_NULL) LadoAtendimento canceladoPor,
        @JsonInclude(JsonInclude.Include.NON_NULL) MotivoCancelamento motivoCancelamento,
        boolean cancelamentoContaOcorrencia,
        boolean alteracaoTardia
) {

    /** Período proposto por um lado, à espera da resposta do outro. */
    public record Remarcacao(Instant inicio, Instant termino, LadoAtendimento solicitadaPor, Instant solicitadaEm) {
    }

    public static AtendimentoResponse de(Atendimento atendimento, Instant agora) {
        boolean ativo = atendimento.getStatus().ativo();
        return new AtendimentoResponse(
                atendimento.getId(),
                atendimento.getStatus(),
                atendimento.getInicio(),
                atendimento.getTermino(),
                atendimento.getAceitoEm(),
                atendimento.temRemarcacaoPendente()
                        ? new Remarcacao(atendimento.getRemarcacaoInicio(), atendimento.getRemarcacaoTermino(),
                                atendimento.getRemarcacaoPor(), atendimento.getRemarcacaoSolicitadaEm())
                        : null,
                atendimento.getCanceladoPor(),
                atendimento.getMotivoCancelamento(),
                ativo && RegrasOcorrencia.cancelamentoConta(atendimento, agora),
                ativo && RegrasOcorrencia.alteracaoTardia(atendimento, agora)
        );
    }

}
