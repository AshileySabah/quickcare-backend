package com.quickcare.backend.denuncia;

import lombok.Getter;

@Getter
public enum MotivoDenuncia {
    COMPORTAMENTO_INADEQUADO("Comportamento inadequado"),
    DESRESPEITO_OU_ASSEDIO("Desrespeito ou assédio"),
    NEGLIGENCIA("Negligência no atendimento"),
    COBRANCA_INDEVIDA("Cobrança indevida"),
    FRAUDE("Fraude ou golpe"),
    PERFIL_FALSO("Perfil ou documentos falsos"),
    OUTRO("Outro motivo");

    private final String rotulo;

    MotivoDenuncia(String rotulo) {
        this.rotulo = rotulo;
    }
}
