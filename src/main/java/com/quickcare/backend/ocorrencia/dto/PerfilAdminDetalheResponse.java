package com.quickcare.backend.ocorrencia.dto;

import com.quickcare.backend.administrador.dto.AcaoAdminResponse;

import java.util.List;

/** O perfil no admin: ocorrências (inclusive anuladas), bloqueios e o que os admins já fizeram. */
public record PerfilAdminDetalheResponse(
        PerfilAdminResumoResponse resumo,
        int limite,
        int janelaDias,
        List<OcorrenciaResponse> ocorrencias,
        List<BloqueioResponse> bloqueios,
        List<AcaoAdminResponse> acoes
) {
}
