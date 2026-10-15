package com.quickcare.backend.validacao;

import com.quickcare.backend.documento.TipoDocumento;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Cada etapa confere um dado do cadastro contra o documento enviado para ele.
 * O paciente só passa pela identidade; o profissional também por CNPJ (se
 * informou) e registro profissional (se a categoria exige).
 */
@Getter
@RequiredArgsConstructor
public enum TipoEtapaValidacao {
    IDENTIDADE("Identidade", TipoDocumento.VALIDACAO_CPF),
    CNPJ("CNPJ", TipoDocumento.VALIDACAO_CNPJ),
    REGISTRO_PROFISSIONAL("Registro profissional", TipoDocumento.REGISTRO_PROFISSIONAL);

    private final String descricao;
    private final TipoDocumento tipoDocumento;
}
