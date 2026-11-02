package com.quickcare.backend.solicitacao.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.administrador.dto.AcaoAdminResponse;
import com.quickcare.backend.administrador.dto.ParteResponse;
import com.quickcare.backend.atendimento.dto.AtendimentoResponse;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.AcaoAdminSolicitacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A solicitação no admin, com as propostas, os atendimentos e o que pode ser feito com o status. */
public record SolicitacaoAdminDetalheResponse(
        SolicitacaoAdminResumoResponse resumo,
        SolicitacaoResponse solicitacao,
        List<PropostaAdmin> propostas,
        List<AcaoAdminSolicitacao> acoesPermitidas,
        List<AcaoAdminResponse> acoes
) {

    public record PropostaAdmin(
            Long id,
            ParteResponse profissional,
            StatusProposta status,
            BigDecimal valor,
            Instant inicio,
            Instant termino,
            @JsonInclude(JsonInclude.Include.NON_NULL) AtendimentoResponse atendimento
    ) {
    }

}
