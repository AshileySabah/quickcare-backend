package com.quickcare.backend.validacao;

import com.quickcare.backend.validacao.exception.EtapaNaoEncontradaException;

import java.util.Arrays;
import java.util.Locale;

public enum ArquivoDaEtapa {
    DOCUMENTO,
    ANEXO;

    public String caminho() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ArquivoDaEtapa ler(String valor) {
        return Arrays.stream(values())
                .filter(arquivo -> arquivo.caminho().equals(valor))
                .findFirst()
                .orElseThrow(EtapaNaoEncontradaException::new);
    }
}
