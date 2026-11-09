package com.quickcare.backend.atuacao.dto;

import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.StatusProposta;

import java.time.Instant;
import java.util.stream.Collectors;

public record PropostaAfetadaResponse(
        Long propostaId,
        Long solicitacaoId,
        String servico,
        StatusProposta status,
        Instant inicio,
        Instant termino,
        boolean geraOcorrencia
) {

    public static PropostaAfetadaResponse de(Proposta proposta, boolean geraOcorrencia) {
        return new PropostaAfetadaResponse(
                proposta.getId(),
                proposta.getSolicitacao().getId(),
                servico(proposta),
                proposta.getStatus(),
                proposta.getInicio(),
                proposta.getTermino(),
                geraOcorrencia
        );
    }

    private static String servico(Proposta proposta) {
        if (proposta.getVaga() == null || proposta.getVaga().getEspecialidades().isEmpty()) {
            return proposta.getAtuacao().rotulo();
        }
        return proposta.getVaga().getCategoria().getRotulo() + " — " + proposta.getVaga().getEspecialidades().stream()
                .map(Especialidade::getNome)
                .sorted()
                .collect(Collectors.joining(", "));
    }

}
