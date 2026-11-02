package com.quickcare.backend.atendimento;

import java.util.Set;

/**
 * Ciclo do atendimento de uma proposta aceita. Não há botão de iniciar nem de
 * finalizar: AGENDADO vira EM_ANDAMENTO no início, FINALIZADO no término e
 * CONCLUIDO 72h depois (a janela para reportar problema, na branch 4b).
 */
public enum StatusAtendimento {
    AGENDADO,
    /** O paciente editou a solicitação; o profissional reaceita ou recusa. A vaga fica reservada. */
    AGUARDANDO_REACEITE,
    EM_ANDAMENTO,
    FINALIZADO,
    CONCLUIDO,
    CANCELADO;

    /** Ainda vai acontecer ou está acontecendo: ocupa a vaga. */
    public static final Set<StatusAtendimento> ATIVOS = Set.of(AGENDADO, AGUARDANDO_REACEITE, EM_ANDAMENTO);

    /** Aconteceu (terminou), para contar atendimentos realizados. */
    public static final Set<StatusAtendimento> REALIZADOS = Set.of(FINALIZADO, CONCLUIDO);

    public boolean ativo() {
        return ATIVOS.contains(this);
    }

    /** Remarcar e cancelar valem a qualquer momento, até o término. */
    public boolean podeSerAlterado() {
        return this == AGENDADO || this == EM_ANDAMENTO;
    }
}
