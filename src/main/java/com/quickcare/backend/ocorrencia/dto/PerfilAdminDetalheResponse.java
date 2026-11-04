package com.quickcare.backend.ocorrencia.dto;

import com.quickcare.backend.administrador.dto.AcaoAdminResponse;

import java.util.List;

public record PerfilAdminDetalheResponse(
        PerfilAdminResumoResponse resumo,
        int limite,
        int janelaDias,
        List<OcorrenciaResponse> ocorrencias,
        List<BloqueioResponse> bloqueios,
        List<MotivoBloqueioResponse> motivosDeBloqueio,
        List<AcaoAdminResponse> acoes
) {
}
