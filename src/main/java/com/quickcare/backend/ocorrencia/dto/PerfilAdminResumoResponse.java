package com.quickcare.backend.ocorrencia.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.validacao.StatusValidacao;

/** Uma linha da lista de perfis do admin, com a situação de ocorrências e recursos. */
public record PerfilAdminResumoResponse(
        Long usuarioId,
        TipoPerfil perfil,
        String nome,
        String email,
        StatusValidacao statusValidacao,
        boolean banido,
        int ocorrenciasContando,
        int recursosEmAnalise,
        @JsonInclude(JsonInclude.Include.NON_NULL) BloqueioResponse bloqueio
) {
}
