package com.quickcare.backend.bloqueiousuario.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.administrador.dto.ParteResponse;
import com.quickcare.backend.bloqueiousuario.AutorBloqueio;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuario;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BloqueioUsuarioAdminResponse(
        Long id,
        ParteResponse paciente,
        ParteResponse profissional,
        AutorBloqueio bloqueadoPor,
        String criadoPor,
        String justificativa,
        Instant criadoEm
) {

    public static BloqueioUsuarioAdminResponse de(BloqueioUsuario bloqueio) {
        return new BloqueioUsuarioAdminResponse(
                bloqueio.getId(),
                ParteResponse.de(bloqueio.getPaciente().getUsuario()),
                ParteResponse.de(bloqueio.getProfissional().getUsuario()),
                bloqueio.getBloqueadoPor(),
                bloqueio.getCriadoPor() == null ? null : bloqueio.getCriadoPor().getNome(),
                bloqueio.getJustificativa(),
                bloqueio.getCriadoEm());
    }

}
