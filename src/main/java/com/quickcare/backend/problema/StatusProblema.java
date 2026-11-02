package com.quickcare.backend.problema;

import java.util.Set;

public enum StatusProblema {
    /** O outro lado tem 7 dias para confirmar ou contestar. */
    AGUARDANDO_RESPOSTA,
    /** O outro lado confirmou que não foi realizado. */
    CONFIRMADO,
    /** Os 7 dias passaram sem resposta: vale o relato (ainda dá para contestar com o admin). */
    SEM_RESPOSTA,
    EM_DISPUTA,
    /** O admin decidiu a disputa. */
    DECIDIDO;

    /** Quem foi acusado ainda pode contestar. */
    public static final Set<StatusProblema> CONTESTAVEIS = Set.of(AGUARDANDO_RESPOSTA, SEM_RESPOSTA);
}
