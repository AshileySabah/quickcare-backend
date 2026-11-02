package com.quickcare.backend.atendimento;

import java.util.Set;

/**
 * Ciclo do atendimento de uma proposta aceita. Não há botão de iniciar nem de
 * finalizar: AGENDADO vira EM_ANDAMENTO no início, FINALIZADO no término e
 * CONCLUIDO 72h depois (a janela para relatar problema).
 */
public enum StatusAtendimento {
    AGENDADO,
    /** O paciente editou a solicitação; o profissional reaceita ou recusa. A vaga fica reservada. */
    AGUARDANDO_REACEITE,
    EM_ANDAMENTO,
    FINALIZADO,
    CONCLUIDO,
    CANCELADO,
    /** Um lado disse que o atendimento não foi realizado; o outro tem 7 dias para confirmar ou contestar. */
    PROBLEMA_RELATADO,
    /** O relato foi contestado: o admin decide. */
    EM_DISPUTA,
    NAO_REALIZADO;

    /** Ainda vai acontecer ou está acontecendo: ocupa a vaga. */
    public static final Set<StatusAtendimento> ATIVOS = Set.of(AGENDADO, AGUARDANDO_REACEITE, EM_ANDAMENTO);

    /** Aconteceu (terminou), para contar atendimentos realizados. */
    public static final Set<StatusAtendimento> REALIZADOS = Set.of(FINALIZADO, CONCLUIDO);

    /** Com um problema ainda sem desfecho: nada muda sozinho e a solicitação não conclui. */
    public static final Set<StatusAtendimento> EM_PROBLEMA = Set.of(PROBLEMA_RELATADO, EM_DISPUTA);

    /** Não vai mais acontecer nem mudar, para concluir a solicitação. */
    public static final Set<StatusAtendimento> ENCERRADOS = Set.of(FINALIZADO, CONCLUIDO, NAO_REALIZADO);

    public boolean ativo() {
        return ATIVOS.contains(this);
    }

    /** Remarcar e cancelar valem a qualquer momento, até o término. */
    public boolean podeSerAlterado() {
        return this == AGENDADO || this == EM_ANDAMENTO;
    }

    /** Relatar que não foi realizado: do início até a conclusão (72h depois do término). */
    public boolean aceitaRelatoDeProblema() {
        return this == EM_ANDAMENTO || this == FINALIZADO;
    }
}
