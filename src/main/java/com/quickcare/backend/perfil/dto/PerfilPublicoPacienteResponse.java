package com.quickcare.backend.perfil.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.avaliacao.dto.ResumoAvaliacoesResponse;

import java.time.Instant;
import java.util.List;

public record PerfilPublicoPacienteResponse(
        Long id,
        String nome,
        String avatarUrl,
        int idade,
        String cidade,
        String uf,
        Instant membroDesde,
        ResumoAvaliacoesResponse avaliacoes,
        boolean podeDenunciar,
        boolean denunciaEmAndamento,
        @JsonInclude(JsonInclude.Include.NON_NULL) Saude saude,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ContatoEmergenciaResponse> contatosEmergencia
) {

    public record Saude(
            String alergias,
            String condicoesSaude,
            String medicamentosEmUso
    ) {
    }

}
