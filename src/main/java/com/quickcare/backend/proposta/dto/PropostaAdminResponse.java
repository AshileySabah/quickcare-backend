package com.quickcare.backend.proposta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.administrador.dto.ParteResponse;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.proposta.AcaoAdminProposta;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.StatusSolicitacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PropostaAdminResponse(
        Long id,
        StatusProposta status,
        Long solicitacaoId,
        StatusSolicitacao statusSolicitacao,
        String servico,
        ParteResponse paciente,
        ParteResponse profissional,
        BigDecimal valor,
        Instant inicio,
        Instant termino,
        StatusAtendimento statusAtendimento,
        List<AcaoAdminProposta> acoesPermitidas,
        Instant criadoEm
) {
}
