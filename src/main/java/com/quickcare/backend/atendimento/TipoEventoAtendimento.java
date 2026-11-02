package com.quickcare.backend.atendimento;

/** Auditoria do atendimento: cada mudança gera um evento com o período vigente. */
public enum TipoEventoAtendimento {
    AGENDADO,
    REACEITE_SOLICITADO,
    REACEITO,
    REMARCACAO_SOLICITADA,
    REMARCACAO_ACEITA,
    REMARCACAO_RECUSADA,
    REMARCACAO_EXPIRADA,
    INICIADO,
    FINALIZADO,
    CONCLUIDO,
    CANCELADO
}
