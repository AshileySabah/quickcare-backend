package com.quickcare.backend.atendimento;

public enum MotivoCancelamento {
    /** Paciente ou profissional cancelou o atendimento. */
    DESISTENCIA,
    /** O paciente cancelou a solicitação inteira. */
    SOLICITACAO_CANCELADA,
    /** O profissional alterou a proposta aceita: o paciente precisa aceitá-la de novo. */
    PROPOSTA_ALTERADA,
    /** O profissional recusou a nova versão da solicitação. */
    REACEITE_RECUSADO,
    /** Chegou o início sem o profissional reaceitar. */
    SEM_REACEITE,
    /** O paciente aceitou outro profissional na vaga reservada. */
    SUBSTITUIDO,
    /** A nova versão da solicitação não tem mais vaga para este profissional. */
    EDICAO_INCOMPATIVEL,
    /** O admin cancelou ou concluiu a solicitação. */
    ADMIN
}
