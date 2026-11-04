package com.quickcare.backend.problema;

import java.util.Set;

public enum StatusProblema {
    AGUARDANDO_RESPOSTA,
    CONFIRMADO,
    SEM_RESPOSTA,
    EM_DISPUTA,
    DECIDIDO;

    public static final Set<StatusProblema> CONTESTAVEIS = Set.of(AGUARDANDO_RESPOSTA, SEM_RESPOSTA);
}
