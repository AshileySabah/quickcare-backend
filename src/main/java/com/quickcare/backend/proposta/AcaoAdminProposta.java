package com.quickcare.backend.proposta;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AcaoAdminProposta {
    RECUSAR("recusada", "recusou"),
    CANCELAR("cancelada", "cancelou"),
    REATIVAR("reativada", "reativou");

    private final String participio;
    private final String verbo;
}
