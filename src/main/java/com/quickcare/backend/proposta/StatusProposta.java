package com.quickcare.backend.proposta;

import java.util.Set;

public enum StatusProposta {
    PENDENTE,
    ACEITA,
    RECUSADA,
    CANCELADA;

    public static final Set<StatusProposta> ATIVAS = Set.of(PENDENTE, ACEITA);

    public boolean pendente() {
        return this == PENDENTE;
    }

    public boolean podeSerAlterada() {
        return this == PENDENTE || this == ACEITA;
    }
}
