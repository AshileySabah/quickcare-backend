package com.quickcare.backend.atendimento.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.atendimento.MotivoCancelamento;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.ocorrencia.RegrasOcorrencia;
import com.quickcare.backend.problema.dto.ProblemaResponse;

import java.time.Instant;

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
        boolean alteracaoTardia,
        boolean podeRelatarProblema,
        @JsonInclude(JsonInclude.Include.NON_NULL) ProblemaResponse problema
) {

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
                ativo && RegrasOcorrencia.alteracaoTardia(atendimento, agora),
                atendimento.getProblema() == null
                        && atendimento.getStatus().aceitaRelatoDeProblema()
                        && agora.isBefore(atendimento.getTermino().plus(AtendimentoService.JANELA_CONCLUSAO)),
                atendimento.getProblema() == null ? null : ProblemaResponse.de(atendimento.getProblema())
        );
    }

}
