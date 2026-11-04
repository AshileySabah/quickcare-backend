package com.quickcare.backend.solicitacao;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AcaoAdminSolicitacao {
    REABRIR("reaberta", "reabriu"),
    CONCLUIR("concluída", "concluiu"),
    CANCELAR("cancelada", "cancelou");

    private final String participio;
    private final String verbo;
}
