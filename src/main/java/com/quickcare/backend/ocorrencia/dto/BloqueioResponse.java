package com.quickcare.backend.ocorrencia.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.ocorrencia.Bloqueio;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BloqueioResponse(
        Long id,
        int nivel,
        Instant inicio,
        Instant fim,
        boolean ativo,
        Instant removidoEm,
        String removidoPor,
        String justificativaRemocao,
        boolean desfeito,
        String aplicadoPor,
        String justificativa
) {

    public static BloqueioResponse de(Bloqueio bloqueio, Instant agora) {
        return new BloqueioResponse(bloqueio.getId(), bloqueio.getNivel(), bloqueio.getInicio(), bloqueio.getFim(),
                bloqueio.ativoEm(agora), bloqueio.getRemovidoEm(),
                bloqueio.getRemovidoPor() == null ? null : bloqueio.getRemovidoPor().getNome(),
                bloqueio.getJustificativaRemocao(), bloqueio.isDesfeito(),
                bloqueio.getCriadoPor() == null ? null : bloqueio.getCriadoPor().getNome(), bloqueio.getJustificativa());
    }

}
