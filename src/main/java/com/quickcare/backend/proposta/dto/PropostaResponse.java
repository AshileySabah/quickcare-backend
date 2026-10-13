package com.quickcare.backend.proposta.dto;

import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.StatusProposta;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PropostaResponse(
        Long id,
        SolicitacaoResumoResponse solicitacao,
        ProfissionalPropostaResponse profissional,
        BigDecimal valor,
        String abordagem,
        LocalDate prazo,
        StatusProposta status,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static PropostaResponse de(Proposta proposta) {
        return new PropostaResponse(
                proposta.getId(),
                SolicitacaoResumoResponse.de(proposta.getSolicitacao()),
                ProfissionalPropostaResponse.de(proposta.getProfissional()),
                proposta.getValor(),
                proposta.getAbordagem(),
                proposta.getPrazo(),
                proposta.getStatus(),
                proposta.getCriadoEm(),
                proposta.getAtualizadoEm()
        );
    }

}
