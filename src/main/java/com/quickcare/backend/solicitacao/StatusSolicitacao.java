package com.quickcare.backend.solicitacao;

import java.util.Set;

public enum StatusSolicitacao {
    ABERTA,
    RECEBENDO_PROPOSTAS,
    EM_ANDAMENTO,
    CONCLUIDA,
    CANCELADA;

    public static final Set<StatusSolicitacao> RECEBENDO_PROPOSTAS_STATUS = Set.of(ABERTA, RECEBENDO_PROPOSTAS);

    /** Ainda não encerrada (nem concluída nem cancelada). */
    public static final Set<StatusSolicitacao> EM_CURSO = Set.of(ABERTA, RECEBENDO_PROPOSTAS, EM_ANDAMENTO);

    /** Em curso, o paciente edita mesmo com aceitos (eles reaceitam ou recusam a nova versão). */
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
