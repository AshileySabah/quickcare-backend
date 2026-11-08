package com.quickcare.backend.validacao;

public record ChaveEtapa(TipoEtapaValidacao tipo, Long atuacaoId) {

    public static ChaveEtapa de(TipoEtapaValidacao tipo, Long atuacaoId) {
        return new ChaveEtapa(tipo, tipo == TipoEtapaValidacao.REGISTRO_PROFISSIONAL ? atuacaoId : null);
    }

}
