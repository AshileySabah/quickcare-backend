package com.quickcare.backend.proposta;

/** Por que uma proposta já aceita voltou a aguardar o aceite do paciente. */
public enum MotivoReaceite {
    /** O profissional alterou a proposta depois do aceite. */
    PROPOSTA_ALTERADA,
    /**
     * Legado: antes da branch 4a, editar a solicitação devolvia as aceitas ao
     * paciente. Hoje é o profissional quem reaceita (atendimento AGUARDANDO_REACEITE).
     */
    SOLICITACAO_ALTERADA
}
