package com.quickcare.backend.ocorrencia;

public enum MotivoOcorrencia {
    CANCELAMENTO_ATENDIMENTO,
    ALTERACAO_TARDIA_SOLICITACAO,
    NAO_COMPARECIMENTO,
    DECISAO_DISPUTA,
    MANUAL,
    BLOQUEIO_TARDIO;

    public boolean recorrivel() {
        return this != NAO_COMPARECIMENTO && this != DECISAO_DISPUTA && this != BLOQUEIO_TARDIO;
    }
}
