package com.quickcare.backend.denuncia;

import java.util.Set;

public enum StatusDenuncia {
    ABERTA,
    EM_ANALISE,
    PROCEDENTE,
    IMPROCEDENTE;

    public static final Set<StatusDenuncia> PENDENTES = Set.of(ABERTA, EM_ANALISE);

    public boolean pendente() {
        return PENDENTES.contains(this);
    }

    public boolean julgamento() {
        return this == PROCEDENTE || this == IMPROCEDENTE;
    }
}
