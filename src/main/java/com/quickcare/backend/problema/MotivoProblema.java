package com.quickcare.backend.problema;

/** Por que o atendimento não foi realizado, segundo quem relatou. */
public enum MotivoProblema {
    /** O outro lado não compareceu: confirmado (ou sem resposta), ele leva uma ocorrência. */
    OUTRO_NAO_COMPARECEU,
    /** Os dois combinaram de não fazer: ninguém é penalizado. */
    COMBINADO
}
