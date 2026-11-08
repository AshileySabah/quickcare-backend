package com.quickcare.backend.atuacao.dto;

import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.validacao.StatusEtapa;

public record AtuacaoResponse(
        AtuacaoResumoResponse atuacao,
        StatusEtapa statusRegistro,
        boolean liberada
) {

    public static AtuacaoResponse de(Atuacao atuacao, StatusEtapa statusRegistro, boolean liberada) {
        return new AtuacaoResponse(AtuacaoResumoResponse.de(atuacao), statusRegistro, liberada);
    }

}
