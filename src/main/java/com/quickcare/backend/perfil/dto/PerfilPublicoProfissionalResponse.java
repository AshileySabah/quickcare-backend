package com.quickcare.backend.perfil.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.atuacao.dto.AtuacaoResumoResponse;
import com.quickcare.backend.profissional.ModalidadeAtendimento;

import java.time.Instant;
import java.util.List;

public record PerfilPublicoProfissionalResponse(
        Long id,
        String nome,
        String avatarUrl,
        List<AtuacaoResumoResponse> atuacoes,
        ModalidadeAtendimento modalidadeAtendimento,
        String cidade,
        String uf,
        Instant membroDesde,
        long atendimentosConcluidos,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ContatoEmergenciaResponse> contatosEmergencia
) {
}
