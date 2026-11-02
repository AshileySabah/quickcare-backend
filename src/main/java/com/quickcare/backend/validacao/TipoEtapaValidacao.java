package com.quickcare.backend.validacao;

import com.quickcare.backend.documento.TipoDocumento;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TipoEtapaValidacao {
    IDENTIDADE("Identidade", TipoDocumento.VALIDACAO_CPF),
    CNPJ("CNPJ", TipoDocumento.VALIDACAO_CNPJ),
    REGISTRO_PROFISSIONAL("Registro profissional", TipoDocumento.REGISTRO_PROFISSIONAL);

    private final String descricao;
    private final TipoDocumento tipoDocumento;
}
