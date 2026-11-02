package com.quickcare.backend.ocorrencia;

public enum MotivoOcorrencia {
    /** Cancelou um atendimento fora da janela de tolerância. */
    CANCELAMENTO_ATENDIMENTO,
    /**
     * O paciente editou a solicitação (ou trocou o profissional aceito) a
     * menos de 48h do atendimento, e o profissional ficou sem ele. No máximo
     * uma por solicitação.
     */
    ALTERACAO_TARDIA_SOLICITACAO
}
