package com.quickcare.backend.proposta;

public enum StatusProposta {
    PENDENTE,
    ACEITA,
    RECUSADA,
    CANCELADA;

    /** Só a proposta ainda não respondida pode ser editada, cancelada ou aceita. */
    public boolean pendente() {
        return this == PENDENTE;
    }
}
