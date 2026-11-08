package com.quickcare.backend.denuncia.dto;

import com.quickcare.backend.denuncia.Denuncia;
import com.quickcare.backend.denuncia.StatusDenuncia;

import java.time.Instant;

public record DenunciaEnviadaResponse(Long id, StatusDenuncia status, Instant criadoEm) {

    public static DenunciaEnviadaResponse de(Denuncia denuncia) {
        return new DenunciaEnviadaResponse(denuncia.getId(), denuncia.getStatus(), denuncia.getCriadoEm());
    }

}
