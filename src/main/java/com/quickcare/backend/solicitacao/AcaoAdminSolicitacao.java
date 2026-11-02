package com.quickcare.backend.solicitacao;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** O que o admin pode fazer com o status de uma solicitação. */
@Getter
@RequiredArgsConstructor
public enum AcaoAdminSolicitacao {
    /** Volta a ficar em curso; o status (aberta, recebendo, em andamento) sai das vagas. */
    REABRIR("reaberta", "reabriu"),
    CONCLUIR("concluída", "concluiu"),
    CANCELAR("cancelada", "cancelou");

    private final String participio;
    private final String verbo;
}
