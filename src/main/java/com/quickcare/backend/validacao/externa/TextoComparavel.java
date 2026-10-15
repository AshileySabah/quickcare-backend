package com.quickcare.backend.validacao.externa;

import java.text.Normalizer;
import java.util.Locale;

final class TextoComparavel {

    private TextoComparavel() {
    }

    /** Ignora acentos, caixa e espaços extras ao comparar nomes. */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

}
