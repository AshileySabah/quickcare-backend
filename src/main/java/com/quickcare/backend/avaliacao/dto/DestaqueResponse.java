package com.quickcare.backend.avaliacao.dto;

import com.quickcare.backend.avaliacao.DestaqueAvaliacao;

public record DestaqueResponse(DestaqueAvaliacao codigo, String rotulo, boolean positivo) {

    public static DestaqueResponse de(DestaqueAvaliacao destaque) {
        return new DestaqueResponse(destaque, destaque.getRotulo(), destaque.isPositivo());
    }

}
