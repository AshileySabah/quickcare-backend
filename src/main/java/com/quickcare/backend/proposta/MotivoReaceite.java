package com.quickcare.backend.proposta;

/** Por que uma proposta já aceita voltou a aguardar o aceite do paciente. */
public enum MotivoReaceite {
    /** O profissional alterou a proposta depois do aceite. */
    PROPOSTA_ALTERADA,
    /** O paciente alterou a solicitação depois de aceitar a proposta. */
    SOLICITACAO_ALTERADA
}
