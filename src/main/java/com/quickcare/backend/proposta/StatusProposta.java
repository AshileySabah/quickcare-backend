package com.quickcare.backend.proposta;

import java.util.Set;

public enum StatusProposta {
    PENDENTE,
    ACEITA,
    RECUSADA,
    CANCELADA;

    /** Ainda valendo: o profissional não pode ter duas destas na mesma solicitação. */
    public static final Set<StatusProposta> ATIVAS = Set.of(PENDENTE, ACEITA);

    /** Só a proposta ainda não respondida pode ser aceita ou recusada. */
    public boolean pendente() {
        return this == PENDENTE;
    }

    /**
     * O profissional edita ou cancela a proposta pendente e também a aceita
     * (nesse caso a vaga reabre e o paciente precisa aceitar de novo).
     */
    public boolean podeSerAlterada() {
        return this == PENDENTE || this == ACEITA;
    }
}
