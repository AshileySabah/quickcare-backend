package com.quickcare.backend.atendimento;

import java.util.Set;

public enum StatusAtendimento {
    AGENDADO,
    AGUARDANDO_REACEITE,
    EM_ANDAMENTO,
    FINALIZADO,
    CONCLUIDO,
    CANCELADO,
    PROBLEMA_RELATADO,
    EM_DISPUTA,
    NAO_REALIZADO;

    public static final Set<StatusAtendimento> ATIVOS = Set.of(AGENDADO, AGUARDANDO_REACEITE, EM_ANDAMENTO);

    public static final Set<StatusAtendimento> REALIZADOS = Set.of(FINALIZADO, CONCLUIDO);

    public static final Set<StatusAtendimento> EM_PROBLEMA = Set.of(PROBLEMA_RELATADO, EM_DISPUTA);

    public static final Set<StatusAtendimento> ENCERRADOS = Set.of(FINALIZADO, CONCLUIDO, NAO_REALIZADO);

    public boolean ativo() {
        return ATIVOS.contains(this);
    }

    public boolean podeSerAlterado() {
        return this == AGENDADO || this == EM_ANDAMENTO;
    }

    public boolean aceitaRelatoDeProblema() {
        return this == EM_ANDAMENTO || this == FINALIZADO;
    }
}
