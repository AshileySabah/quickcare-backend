package com.quickcare.backend.solicitacao;

import java.util.Set;

public enum StatusSolicitacao {
    ABERTA,
    RECEBENDO_PROPOSTAS,
    EM_ANDAMENTO,
    CONCLUIDA,
    CANCELADA;

    public static final Set<StatusSolicitacao> RECEBENDO_PROPOSTAS_STATUS = Set.of(ABERTA, RECEBENDO_PROPOSTAS);

    public static final Set<StatusSolicitacao> EM_CURSO = Set.of(ABERTA, RECEBENDO_PROPOSTAS, EM_ANDAMENTO);

    public boolean podeSerEditada() {
        return EM_CURSO.contains(this);
    }

    public boolean podeSerCancelada() {
        return this == ABERTA || this == RECEBENDO_PROPOSTAS || this == EM_ANDAMENTO;
    }

    public boolean recebePropostas() {
        return RECEBENDO_PROPOSTAS_STATUS.contains(this);
    }
}
