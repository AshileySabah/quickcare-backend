package com.quickcare.backend.administrador.dto;

import com.quickcare.backend.administrador.AcaoAdmin;
import com.quickcare.backend.administrador.TipoAcaoAdmin;

import java.time.Instant;

public record AcaoAdminResponse(TipoAcaoAdmin tipo, String admin, String detalhe, String justificativa, Instant criadoEm) {

    public static AcaoAdminResponse de(AcaoAdmin acao) {
        return new AcaoAdminResponse(acao.getTipo(), acao.getAdmin() == null ? null : acao.getAdmin().getNome(),
                acao.getDetalhe(), acao.getJustificativa(), acao.getCriadoEm());
    }

}
