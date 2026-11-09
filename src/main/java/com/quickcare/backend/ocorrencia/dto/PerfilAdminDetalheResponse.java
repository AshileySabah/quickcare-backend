package com.quickcare.backend.ocorrencia.dto;

import com.quickcare.backend.administrador.dto.AcaoAdminResponse;
import com.quickcare.backend.banimento.dto.BanimentoResponse;

import java.util.List;

public record PerfilAdminDetalheResponse(
        PerfilAdminResumoResponse resumo,
        int limite,
        int janelaDias,
        List<OcorrenciaResponse> ocorrencias,
        List<BloqueioResponse> bloqueios,
        List<MotivoBloqueioResponse> motivosDeBloqueio,
        List<BanimentoResponse> banimentos,
        List<AcaoAdminResponse> acoes
) {
}
