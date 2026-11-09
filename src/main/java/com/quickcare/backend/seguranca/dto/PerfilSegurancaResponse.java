package com.quickcare.backend.seguranca.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.usuario.TipoPerfil;

import java.time.Instant;

public record PerfilSegurancaResponse(
        TipoPerfil perfil,
        boolean encerrado,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant encerradoEm,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant excluiEm
) {
}
