package com.quickcare.backend.ocorrencia;

public enum MotivoOcorrencia {
    /** Cancelou um atendimento fora da janela de tolerância. */
    CANCELAMENTO_ATENDIMENTO,
    /**
     * O paciente editou a solicitação (ou trocou o profissional aceito) a
     * menos de 48h do atendimento, e o profissional ficou sem ele. No máximo
     * uma por solicitação.
     */
    ALTERACAO_TARDIA_SOLICITACAO,
    /** Não compareceu: o outro lado relatou e ele confirmou ou não respondeu em 7 dias. */
    NAO_COMPARECIMENTO,
    /** Aplicada pelo admin ao decidir uma disputa. */
    DECISAO_DISPUTA,
    /** Lançada pelo admin, com justificativa. */
    MANUAL;

    /**
     * Cabe recurso ao admin. A de não comparecimento se contesta pelo próprio
     * atendimento (vira disputa), e a de disputa já foi decisão do admin.
     */
    public boolean recorrivel() {
        return this != NAO_COMPARECIMENTO && this != DECISAO_DISPUTA;
    }
}
