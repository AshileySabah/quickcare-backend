package com.quickcare.backend.cadastro;

import com.quickcare.backend.cadastro.exception.DocumentoIdentidadeInvalidoException;
import com.quickcare.backend.common.validation.DocumentosBrasileiros;
import com.quickcare.backend.localizacao.Uf;
import com.quickcare.backend.usuario.TipoDocumentoIdentidade;

import java.util.Locale;

public record DocumentoIdentidade(TipoDocumentoIdentidade tipo, String numero, String uf) {

    public static DocumentoIdentidade validar(TipoDocumentoIdentidade tipo, String numero, String uf) {
        if (tipo == null || numero == null || numero.isBlank()) {
            throw new DocumentoIdentidadeInvalidoException("Informe o tipo e o número do documento de identidade (RG ou CNH).");
        }

        String normalizado = numero.replaceAll("[^0-9xX]", "").toUpperCase(Locale.ROOT);

        if (tipo == TipoDocumentoIdentidade.CNH) {
            if (!DocumentosBrasileiros.cnhValida(normalizado)) {
                throw new DocumentoIdentidadeInvalidoException("Número de CNH inválido.");
            }
            return new DocumentoIdentidade(tipo, normalizado, null);
        }

        String ufNormalizada = validarUf(uf);
        if (!DocumentosBrasileiros.rgValido(normalizado, ufNormalizada)) {
            throw new DocumentoIdentidadeInvalidoException("SP".equals(ufNormalizada)
                    ? "Número de RG inválido (o dígito verificador não confere)."
                    : "Número de RG inválido.");
        }
        return new DocumentoIdentidade(tipo, normalizado, ufNormalizada);
    }

    private static String validarUf(String uf) {
        if (uf == null || uf.isBlank()) {
            throw new DocumentoIdentidadeInvalidoException("Informe a UF emissora do RG.");
        }
        try {
            return Uf.valueOf(uf.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new DocumentoIdentidadeInvalidoException("UF emissora do RG inválida.");
        }
    }

}
