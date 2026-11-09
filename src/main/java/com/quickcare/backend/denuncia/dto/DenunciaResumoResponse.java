package com.quickcare.backend.denuncia.dto;

import com.quickcare.backend.administrador.dto.ParteResponse;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.denuncia.Denuncia;
import com.quickcare.backend.denuncia.MotivoDenuncia;
import com.quickcare.backend.denuncia.StatusDenuncia;

import java.time.Instant;

public record DenunciaResumoResponse(
        Long id,
        StatusDenuncia status,
        MotivoDenuncia motivo,
        String motivoRotulo,
        LadoAtendimento denunciante,
        ParteResponse paciente,
        ParteResponse profissional,
        Long pacienteId,
        Long profissionalId,
        Instant criadoEm
) {

    public static DenunciaResumoResponse de(Denuncia denuncia) {
        return new DenunciaResumoResponse(
                denuncia.getId(),
                denuncia.getStatus(),
                denuncia.getMotivo(),
                denuncia.getMotivo().getRotulo(),
                denuncia.getDenunciante(),
                ParteResponse.de(denuncia.getPaciente().getUsuario()),
                ParteResponse.de(denuncia.getProfissional().getUsuario()),
                denuncia.getPaciente().getId(),
                denuncia.getProfissional().getId(),
                denuncia.getCriadoEm());
    }

}
